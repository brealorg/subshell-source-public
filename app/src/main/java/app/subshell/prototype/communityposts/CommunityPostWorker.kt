package app.subshell.prototype.communityposts

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.subshell.prototype.auth.AuthApplicationRuntimeFactory
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityNewPostsLoader
import app.subshell.prototype.data.auth.CommunityComment
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.inbox.INBOX_BACKGROUND_RETRY_BACKOFF_SECONDS
import app.subshell.prototype.inbox.INBOX_BACKGROUND_SYNC_INTERVAL_MINUTES
import app.subshell.prototype.inbox.backgroundSyncFailureDetails
import app.subshell.prototype.inbox.backgroundSyncRetries
import app.subshell.prototype.inbox.inboxBackgroundSyncConstraints
import app.subshell.prototype.inbox.inboxWorkerAccountSignedIn
import app.subshell.prototype.inbox.isTransientNetworkFailure
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.activeMutedCommunityNamesAt
import app.subshell.prototype.ui.communityMutesPreferenceKey
import app.subshell.prototype.ui.decodeCommunityMutes
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

// ISSUE239_BACKGROUND_CHECK_FOR_NEW_POSTS_V1: every 15 minutes (Android's
// minimum) while the active signed-in account watches a community; the same
// network rules and retries as the Inbox background sync (BGSYNCDIAG01).
internal object CommunityPostCheckScheduler {
    fun schedule(context: Context, accountStableId: String) {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Community post check account stable ID cannot be blank" }
        val request = PeriodicWorkRequest.Builder(
            CommunityPostCheckWorker::class.java,
            INBOX_BACKGROUND_SYNC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(inboxBackgroundSyncConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INBOX_BACKGROUND_RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(Data.Builder().putString(COMMUNITY_POST_CHECK_ACCOUNT_INPUT, account).build())
            .addTag(COMMUNITY_POST_CHECK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(COMMUNITY_POST_CHECK_UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(COMMUNITY_POST_CHECK_UNIQUE_WORK)
    }
}

/** The outcome of one check, apart from the worker so it can be tested without WorkManager. */
internal data class CommunityPostCheckRun(val posted: Int, val transientNetworkFailure: Boolean)

/**
 * Checks each watched, unmuted community of [accountStableId] once: a silent
 * baseline the first time, afterwards a notification for each new post, and
 * with [includeComments] for each new comment. Nothing by [ownUsername] notifies.
 */
internal suspend fun runCommunityPostCheck(
    accountStableId: String,
    watched: List<String>,
    mutedNames: Set<String>,
    loader: AuthenticatedRedditCommunityNewPostsLoader,
    seen: CommunityPostSeenStore,
    nowEpochMillis: Long,
    recordFailure: (FailureDetails) -> Unit,
    notify: (communityName: String, posts: List<Post>) -> Int,
    ownUsername: String? = null,
    includeComments: Boolean = false,
    notifyComments: (communityName: String, comments: List<CommunityComment>) -> Int = { _, _ -> 0 },
): CommunityPostCheckRun {
    seen.forgetAllBut(accountStableId, watched)
    var posted = 0
    var transient = false
    suspend fun <T> readOrRecord(read: suspend () -> T?): T? = try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        val details = backgroundSyncFailureDetails(failure)
        if (details.isTransientNetworkFailure()) transient = true
        recordFailure(details)
        null
    }
    watched
        .filterNot { name -> mutedNames.any { it.equals(name, ignoreCase = true) } }
        .forEach { community ->
            val posts = readOrRecord { loader.load(accountStableId, community) }
            if (posts != null) {
                val check = checkForNewPosts(posts, seen.watermark(accountStableId, community, nowEpochMillis), ownUsername)
                posted += notify(community, check.newPosts)
                seen.save(accountStableId, community, check.watermark, nowEpochMillis)
            }
            // ISSUE239_NEW_COMMENTS_IN_A_WATCHED_COMMUNITY_V1: only while the
            // setting is on; turned on again later, the old watermark is too old
            // and the first check is a silent baseline.
            if (!includeComments) return@forEach
            val comments = readOrRecord { loader.loadComments(accountStableId, community) } ?: return@forEach
            val stream = CommunityStream.COMMENTS
            val commentCheck = checkForNewComments(
                comments,
                seen.watermark(accountStableId, community, nowEpochMillis, stream),
                ownUsername,
            )
            posted += notifyComments(community, commentCheck.newComments)
            seen.save(accountStableId, community, commentCheck.watermark, nowEpochMillis, stream)
        }
    return CommunityPostCheckRun(posted = posted, transientNetworkFailure = transient)
}

internal class CommunityPostCheckWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val account = inputData.getString(COMMUNITY_POST_CHECK_ACCOUNT_INPUT)
            ?.trim()?.takeIf(String::isNotEmpty)
            ?: return Result.success()
        val watched = readWatchedCommunityNames(applicationContext, account)
        if (watched.isEmpty()) {
            CommunityPostCheckScheduler.cancel(applicationContext)
            return Result.success()
        }
        // Without permission nothing could be shown; reading Reddit would be wasted.
        if (!CommunityPostNotificationCenter.canPost(applicationContext)) return Result.success()
        val diagnostics = DiagnosticEventStore(applicationContext)
        val runtime = runCatching { AuthApplicationRuntimeFactory.create(applicationContext) }.getOrElse {
            diagnostics.recordSafely(FailureDetails(retryable = false, localKind = LocalFailureKind.CONFIGURATION))
            return Result.success()
        }
        // Also reads the account directory, which the loader needs (it checks the active account).
        if (inboxWorkerAccountSignedIn(runtime.accountSessionDirectory, account) == false) {
            CommunityPostCheckScheduler.cancel(applicationContext)
            return Result.success()
        }
        val loader = runtime.redditCommunityNewPostsLoader ?: run {
            diagnostics.recordSafely(FailureDetails(retryable = false, localKind = LocalFailureKind.CONFIGURATION))
            return Result.success()
        }
        val now = System.currentTimeMillis()
        val ownUsername = accountUsername(runtime.accountSessionDirectory, account)
        val run = runCommunityPostCheck(
            accountStableId = account,
            watched = watched,
            mutedNames = mutedCommunityNames(account, now),
            loader = loader,
            seen = CommunityPostSeenStore(applicationContext),
            nowEpochMillis = now,
            recordFailure = { details -> diagnostics.recordSafely(details) },
            notify = { community, posts ->
                CommunityPostNotificationCenter.postNewPosts(applicationContext, account, community, posts)
            },
            ownUsername = ownUsername,
            includeComments = newCommentNotificationsEnabled(applicationContext),
            notifyComments = { community, comments ->
                CommunityPostNotificationCenter.postNewComments(applicationContext, account, community, comments)
            },
        )
        Log.d(COMMUNITY_POST_CHECK_LOG_TAG, "checked communities=${watched.size} posted=${run.posted}")
        return if (backgroundSyncRetries(run.transientNetworkFailure, runAttemptCount)) Result.retry() else Result.success()
    }

    private fun mutedCommunityNames(account: String, now: Long): Set<String> = runCatching {
        val stored = applicationContext
            .getSharedPreferences(COMMUNITY_POST_WATCH_PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(communityMutesPreferenceKey(account), emptySet())
            .orEmpty()
        activeMutedCommunityNamesAt(decodeCommunityMutes(stored), now)
    }.getOrDefault(emptySet())

    private fun DiagnosticEventStore.recordSafely(details: FailureDetails) {
        runCatching { recordBackgroundFailure(scopeKey = COMMUNITY_POST_CHECK_SCOPE, details = details) }
    }
}

/** The account's Reddit name, so its own posts and comments never notify; null if unknown. */
private suspend fun accountUsername(directory: AccountSessionDirectory?, accountStableId: String): String? {
    val snapshot = try {
        directory?.hydratedSnapshot()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
    return snapshot?.accounts?.firstOrNull { it.stableId == accountStableId }?.username
}

// ISSUE239_NEW_COMMENTS_IN_A_WATCHED_COMMUNITY_V1: off by default (owner).
internal fun newCommentNotificationsEnabled(context: Context): Boolean =
    runCatching {
        context.getSharedPreferences(COMMUNITY_POST_WATCH_PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(SubshellSettings.NOTIFY_NEW_COMMENTS, false)
    }.getOrDefault(false)

// The scope names the community subsystem, never a community (DIAG04 action token).
internal const val COMMUNITY_POST_CHECK_SCOPE = "community_posts_background_check"
private const val COMMUNITY_POST_CHECK_ACCOUNT_INPUT = "account_stable_id"
private const val COMMUNITY_POST_CHECK_TAG = "subshell-community-post-check"
internal const val COMMUNITY_POST_CHECK_UNIQUE_WORK = "subshell-community-post-check-active-account"
private const val COMMUNITY_POST_CHECK_LOG_TAG = "SubshellCommunityPosts"
