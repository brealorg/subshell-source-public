package app.subshell.prototype.offline

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.subshell.prototype.R
import app.subshell.prototype.auth.AuthApplicationRuntimeFactory
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditFeedUpdater
import app.subshell.prototype.data.createPersistentSubshellRepository
import app.subshell.prototype.inbox.inboxWorkerAccountSignedIn
import app.subshell.prototype.media.RemoteImageLoader
import app.subshell.prototype.model.Post
import kotlin.coroutines.cancellation.CancellationException

/** ISSUE265_SAVE_FOR_OFFLINE_V1 (owner 2026-10-27): the top 50 posts of the feed. */
internal const val OFFLINE_SAVE_POST_COUNT = 50

/** Feed pages read at most (the first page plus appends) to reach [OFFLINE_SAVE_POST_COUNT]. */
private const val OFFLINE_SAVE_MAX_FEED_PAGES = 4

/** What one save did, apart from the worker so it can be tested without WorkManager. */
internal data class OfflineSaveRun(
    val posts: Int,
    val threads: Int,
    val images: Int,
    val feedFailed: Boolean,
)

/** Every image of a post shown in its card or gallery; videos are not saved (owner). */
internal fun offlineImageUris(post: Post): List<String> = buildList {
    post.remoteImageUri?.takeIf(String::isNotBlank)?.let(::add)
    post.gallery.forEach { item -> item.remoteImageUri?.takeIf(String::isNotBlank)?.let(::add) }
}.distinct()

/**
 * ISSUE265_SAVE_FOR_OFFLINE_V1: reads the feed until [OFFLINE_SAVE_POST_COUNT]
 * posts are stored (the feed store keeps them, as a pull to refresh does), then
 * each post's Best comment thread (stored as when the post is opened) and its
 * images (saved through [saveImage]). NSFW media is saved only while NSFW posts
 * are shown. A failed thread or image is skipped; the rest goes on.
 */
internal suspend fun runOfflineSave(
    accountStableId: String,
    target: AuthenticatedRedditFeedTarget,
    includeNsfw: Boolean,
    feedUpdater: AuthenticatedRedditFeedUpdater,
    storedPosts: (RepositoryQueryKey) -> List<Post>,
    commentLoader: AuthenticatedRedditCommentLoader?,
    saveImage: suspend (String) -> Boolean,
    progress: (done: Int, total: Int) -> Unit,
): OfflineSaveRun {
    var queryKey: RepositoryQueryKey? = null
    var pages = 0
    while (pages < OFFLINE_SAVE_MAX_FEED_PAGES) {
        val operation = if (pages == 0) RepositoryLoadOperation.REFRESH else RepositoryLoadOperation.APPEND
        val result = feedUpdater.synchronize(accountStableId, operation, target)
        pages += 1
        when (result) {
            is AuthenticatedRedditFeedUpdateResult.Applied -> {
                queryKey = result.queryKey
                if (result.endReached || storedPosts(result.queryKey).size >= OFFLINE_SAVE_POST_COUNT) break
            }
            // A failed later page keeps what the earlier pages stored.
            is AuthenticatedRedditFeedUpdateResult.Failed -> break
        }
    }
    val key = queryKey ?: return OfflineSaveRun(posts = 0, threads = 0, images = 0, feedFailed = true)
    val posts = storedPosts(key).take(OFFLINE_SAVE_POST_COUNT)
    var threads = 0
    var images = 0
    posts.forEachIndexed { index, post ->
        progress(index, posts.size)
        if (commentLoader != null && post.comments > 0) {
            val thread = try {
                commentLoader.synchronize(accountStableId, post.id, RedditCommentSort.BEST)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (thread is AuthenticatedRedditCommentResult.Loaded) threads += 1
        }
        if (includeNsfw || !post.nsfw) {
            offlineImageUris(post).forEach { uri ->
                val saved = try {
                    saveImage(uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (saved) images += 1
            }
        }
    }
    progress(posts.size, posts.size)
    return OfflineSaveRun(posts = posts.size, threads = threads, images = images, feedFailed = false)
}

internal object OfflineSaveScheduler {
    fun enqueue(
        context: Context,
        accountStableId: String,
        mainFeed: RedditMainFeed,
        sort: RedditFeedSort,
        timePeriod: RedditListingTimePeriod,
        includeNsfw: Boolean,
    ) {
        val request = OneTimeWorkRequest.Builder(OfflineSaveWorker::class.java)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(
                Data.Builder()
                    .putString(INPUT_ACCOUNT, accountStableId)
                    .putString(INPUT_FEED, mainFeed.name)
                    .putString(INPUT_SORT, sort.name)
                    .putString(INPUT_TIME_PERIOD, timePeriod.name)
                    .putBoolean(INPUT_INCLUDE_NSFW, includeNsfw)
                    .build(),
            )
            .addTag(OFFLINE_SAVE_TAG)
            .build()
        // A second tap while a save runs starts it again for the feed shown now.
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(OFFLINE_SAVE_UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}

internal class OfflineSaveWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val account = inputData.getString(INPUT_ACCOUNT)?.trim()?.takeIf(String::isNotEmpty)
            ?: return Result.success()
        val target = AuthenticatedRedditFeedTarget(
            mainFeed = enumOrNull<RedditMainFeed>(inputData.getString(INPUT_FEED)) ?: return Result.success(),
            sort = enumOrNull<RedditFeedSort>(inputData.getString(INPUT_SORT)) ?: RedditFeedSort.BEST,
            timePeriod = enumOrNull<RedditListingTimePeriod>(inputData.getString(INPUT_TIME_PERIOD))
                ?: RedditListingTimePeriod.ALL,
        )
        val notifications = OfflineSaveNotifications(applicationContext)
        val runtime = runCatching { AuthApplicationRuntimeFactory.create(applicationContext) }.getOrNull()
        val updater = runtime?.redditFeedUpdater
        if (
            runtime == null || updater == null ||
            inboxWorkerAccountSignedIn(runtime.accountSessionDirectory, account) != true
        ) {
            notifications.finished(null)
            return Result.success()
        }
        val repository = createPersistentSubshellRepository(applicationContext)
        val store = OfflineImageStore.forContext(applicationContext)
        RemoteImageLoader.offlineImageStore = store
        val run = runOfflineSave(
            accountStableId = account,
            target = target,
            includeNsfw = inputData.getBoolean(INPUT_INCLUDE_NSFW, false),
            feedUpdater = updater,
            storedPosts = { key ->
                when (
                    val result = repository.posts(
                        RepositoryPageRequest(limit = OFFLINE_SAVE_POST_COUNT, queryKey = key),
                    )
                ) {
                    is RepositoryResult.Content -> result.value
                    else -> emptyList()
                }
            },
            commentLoader = runtime.redditCommentLoader,
            saveImage = { uri -> RemoteImageLoader.saveForOffline(uri, store) },
            progress = notifications::progress,
        )
        notifications.finished(run)
        return Result.success()
    }
}

/** A progress notification while saving and one line when done; silent without permission. */
internal class OfflineSaveNotifications(context: Context) {
    private val appContext = context.applicationContext

    fun progress(done: Int, total: Int) {
        post(
            NotificationCompat.Builder(appContext, OFFLINE_SAVE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_subshell)
                .setContentTitle("Saving for offline")
                .setContentText("$done of $total posts")
                .setProgress(total, done, total == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true),
        )
    }

    fun finished(run: OfflineSaveRun?) {
        val text = when {
            run == null -> "Sign in to Reddit to save posts for offline."
            run.feedFailed -> "Couldn't read the feed from Reddit. Nothing new was saved."
            else -> "${run.posts} posts, ${run.threads} comment threads and ${run.images} images are ready offline."
        }
        post(
            NotificationCompat.Builder(appContext, OFFLINE_SAVE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_subshell)
                .setContentTitle(if (run != null && !run.feedFailed) "Saved for offline" else "Not saved for offline")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true),
        )
    }

    @SuppressLint("MissingPermission") // canPost checks it.
    private fun post(builder: NotificationCompat.Builder) {
        if (!canPost()) return
        ensureChannel()
        NotificationManagerCompat.from(appContext).notify(OFFLINE_SAVE_NOTIFICATION_ID, builder.build())
    }

    private fun canPost(): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(OFFLINE_SAVE_CHANNEL_ID, "Offline", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while posts are saved for offline reading"
            },
        )
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    enumValues<T>().firstOrNull { it.name == name }

private const val INPUT_ACCOUNT = "account"
private const val INPUT_FEED = "feed"
private const val INPUT_SORT = "sort"
private const val INPUT_TIME_PERIOD = "time_period"
private const val INPUT_INCLUDE_NSFW = "include_nsfw"
private const val OFFLINE_SAVE_TAG = "subshell-offline-save"
private const val OFFLINE_SAVE_UNIQUE_WORK = "subshell-offline-save"
internal const val OFFLINE_SAVE_CHANNEL_ID = "subshell_offline"
private const val OFFLINE_SAVE_NOTIFICATION_ID = 26_501
