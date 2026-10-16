package app.subshell.prototype.inbox

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.subshell.prototype.auth.AuthApplicationRuntimeFactory
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailUnreadResult
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.INBOX_NOTIFICATIONS_ON_BY_DEFAULT
import app.subshell.prototype.ui.SubshellSettings
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

internal data class InboxBackgroundEventDecision(
    val initializedBefore: Boolean,
    val newEventKeys: List<String>,
    val retainedEventKeys: List<String>,
)

internal fun reconcileInboxBackgroundEventHistory(
    initialized: Boolean,
    previousEventKeys: List<String>,
    currentEventKeys: List<String>,
    maxHistory: Int = INBOX_BACKGROUND_EVENT_HISTORY_LIMIT,
): InboxBackgroundEventDecision {
    require(maxHistory > 0) { "Inbox background event history limit must be positive" }
    val previous = previousEventKeys
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
    val current = currentEventKeys
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
    val previousSet = previous.toHashSet()
    val newKeys = if (initialized) {
        current.filterNot(previousSet::contains)
    } else {
        emptyList()
    }
    val retained = (current + previous)
        .distinct()
        .take(maxHistory)
    return InboxBackgroundEventDecision(
        initializedBefore = initialized,
        newEventKeys = newKeys,
        retainedEventKeys = retained,
    )
}

internal fun InboxItem.inboxBackgroundEventKey(): String {
    if (kind == ActivityKind.MESSAGE) {
        conversation
            .asReversed()
            .firstOrNull { bubble -> !bubble.outgoing && bubble.id.isNotBlank() }
            ?.id
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { messageId -> return "message:$messageId" }
        return "message:${id.trim()}"
    }
    return "activity:${id.trim()}"
}

internal fun inboxBackgroundUnreadEventKeys(
    items: List<InboxItem>,
    locallySeenIds: Set<String>,
): List<String> = items
    .asSequence()
    .filter { item -> item.unread && item.id !in locallySeenIds }
    .map(InboxItem::inboxBackgroundEventKey)
    .filter { key -> key.substringAfter(':').isNotBlank() }
    .distinct()
    .toList()

// MODMAIL06_SHARED_BACKGROUND_NOTIFICATION_PIPELINE_V1
internal fun RedditModmailConversation.modmailBackgroundEventKey(): String {
    val eventIdentity = latestMessageId
        ?: lastUpdatedEpochSeconds?.toString()
        ?: lastUnreadEpochSeconds?.toString()
        ?: id
    return "modmail:${id.trim()}:$eventIdentity"
}

internal fun modmailBackgroundUnreadEventKeys(
    conversations: List<RedditModmailConversation>,
): List<String> = conversations
    .asSequence()
    .filter(RedditModmailConversation::unread)
    .map(RedditModmailConversation::modmailBackgroundEventKey)
    .distinct()
    .toList()

/**
 * INBOX08_LOGOUT_FORGETS_ACCOUNT_NOTIFICATIONS_V1 (#147): logout of an account
 * cancels its posted inbox/modmail notifications, drops their records and
 * resets its background baseline (a later sign-in starts fresh, not from the
 * old account's last-seen events). Each part is best effort.
 */
internal fun forgetInboxNotificationsForAccount(context: Context, accountStableId: String) {
    val account = accountStableId.trim()
    if (account.isEmpty()) return
    runCatching { InboxNotificationCenter.cancelForAccount(context, account) }
    runCatching { InboxBackgroundEventStore(context).reset(account) }
}

internal class InboxBackgroundEventStore(
    context: Context,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        INBOX_BACKGROUND_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun reset(accountStableId: String) {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Inbox background account stable ID cannot be blank" }
        val suffix = accountPreferenceSuffix(account)
        // A failed write throws; the settings tap reports it and keeps
        // notifications off (LOWUI1_FAILED_NOTIFICATION_BASELINE_IS_NOT_A_CRASH_V1).
        check(
            preferences.edit()
                .remove("$INBOX_BACKGROUND_INITIALIZED_PREFIX$suffix")
                .remove("$INBOX_BACKGROUND_INITIALIZED_PREFIX$suffix$BASELINE_TAKEN_AT_SUFFIX")
                .remove("$INBOX_BACKGROUND_EVENTS_PREFIX$suffix")
                .remove("$MODMAIL_BACKGROUND_INITIALIZED_PREFIX$suffix")
                .remove("$MODMAIL_BACKGROUND_INITIALIZED_PREFIX$suffix$BASELINE_TAKEN_AT_SUFFIX")
                .remove("$MODMAIL_BACKGROUND_EVENTS_PREFIX$suffix")
                .commit(),
        ) {
            "Inbox background event baseline could not be reset"
        }
    }

    fun reconcile(
        accountStableId: String,
        currentEventKeys: List<String>,
    ): InboxBackgroundEventDecision =
        reconcileNamespace(
            accountStableId = accountStableId,
            currentEventKeys = currentEventKeys,
            initializedPrefix = INBOX_BACKGROUND_INITIALIZED_PREFIX,
            eventsPrefix = INBOX_BACKGROUND_EVENTS_PREFIX,
        )

    fun reconcileModmail(
        accountStableId: String,
        currentEventKeys: List<String>,
    ): InboxBackgroundEventDecision =
        reconcileNamespace(
            accountStableId = accountStableId,
            currentEventKeys = currentEventKeys,
            initializedPrefix = MODMAIL_BACKGROUND_INITIALIZED_PREFIX,
            eventsPrefix = MODMAIL_BACKGROUND_EVENTS_PREFIX,
        )

    private fun reconcileNamespace(
        accountStableId: String,
        currentEventKeys: List<String>,
        initializedPrefix: String,
        eventsPrefix: String,
    ): InboxBackgroundEventDecision {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Inbox background account stable ID cannot be blank" }
        val suffix = accountPreferenceSuffix(account)
        val initializedKey = "$initializedPrefix$suffix"
        val takenAtKey = "$initializedKey$BASELINE_TAKEN_AT_SUFFIX"
        val eventsKey = "$eventsPrefix$suffix"
        val previous = preferences
            .getString(eventsKey, null)
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        val now = nowEpochMs()
        val decision = reconcileInboxBackgroundEventHistory(
            initialized = preferences.getBoolean(initializedKey, false) &&
                inboxBackgroundBaselineFresh(preferences.getLong(takenAtKey, 0L), now),
            previousEventKeys = previous,
            currentEventKeys = currentEventKeys,
        )
        check(
            preferences.edit()
                .putBoolean(initializedKey, true)
                .putLong(takenAtKey, now)
                .putString(eventsKey, decision.retainedEventKeys.joinToString("\n"))
                .commit(),
        ) {
            "Inbox background event state could not be persisted"
        }
        return decision
    }

    private fun accountPreferenceSuffix(accountStableId: String): String =
        Base64.encodeToString(
            accountStableId.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
}

internal object InboxBackgroundSyncScheduler {
    fun schedule(
        context: Context,
        accountStableId: String,
    ) {
        val account = accountStableId.trim()
        require(account.isNotEmpty()) { "Inbox background account stable ID cannot be blank" }
        val request = PeriodicWorkRequest.Builder(
            InboxBackgroundSyncWorker::class.java,
            INBOX_BACKGROUND_SYNC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                Data.Builder()
                    .putString(INBOX_BACKGROUND_ACCOUNT_INPUT, account)
                    .build(),
            )
            .addTag(INBOX_BACKGROUND_SYNC_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                INBOX_BACKGROUND_SYNC_UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }

    // INBOX02_SYNC_ONLY_WITH_NOTIFICATIONS_V1
    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(INBOX_BACKGROUND_SYNC_UNIQUE_WORK)
    }
}

// INBOX02_SYNC_ONLY_WITH_NOTIFICATIONS_V1: the only consumer of the
// background sync is inbox notifications (on by default in the main app,
// NOTIFY02_ON_BY_DEFAULT_IN_THE_MAIN_APP_V1).
internal fun inboxNotificationsEnabled(context: Context): Boolean =
    runCatching {
        context.getSharedPreferences(INBOX_SUBSHELL_PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, INBOX_NOTIFICATIONS_ON_BY_DEFAULT)
    }.getOrDefault(false)

private const val INBOX_SUBSHELL_PREFERENCES = "subshell_preferences"

internal class InboxBackgroundSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val account = inputData
            .getString(INBOX_BACKGROUND_ACCOUNT_INPUT)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return Result.success()

        // INBOX02_SYNC_ONLY_WITH_NOTIFICATIONS_V1: work scheduled before
        // notifications were turned off stops itself without syncing.
        if (!inboxNotificationsEnabled(applicationContext)) {
            Log.d(INBOX_BACKGROUND_LOG_TAG, "notifications-off account=$account")
            InboxBackgroundSyncScheduler.cancel(applicationContext)
            return Result.success()
        }
        Log.d(INBOX_BACKGROUND_LOG_TAG, "start account=$account")
        val diagnosticStore = DiagnosticEventStore(applicationContext)
        // INBOX07_PRUNE_RECORDS_ONCE_PER_RUN_V1: before this run posts anything.
        runCatching { InboxNotificationCenter.pruneRecordsOfNotificationsNotShown(applicationContext) }

        val runtime = runCatching {
            AuthApplicationRuntimeFactory.create(applicationContext)
        }.getOrElse {
            Log.d(INBOX_BACKGROUND_LOG_TAG, "runtime-unavailable account=$account")
            diagnosticStore.recordBackgroundFailureSafely(
                scopeKey = "inbox_background_runtime",
                details = FailureDetails(
                    retryable = false,
                    localKind = LocalFailureKind.CONFIGURATION,
                ),
            )
            return Result.success()
        }
        // INBOX02_SYNC_ONLY_WITH_NOTIFICATIONS_V1: an account that was logged
        // out or removed is no longer synced.
        val accountStillSignedIn = inboxWorkerAccountSignedIn(runtime.accountSessionDirectory, account)
        if (accountStillSignedIn == false) {
            Log.d(INBOX_BACKGROUND_LOG_TAG, "account-gone account=$account")
            InboxBackgroundSyncScheduler.cancel(applicationContext)
            return Result.success()
        }
        val eventStore = InboxBackgroundEventStore(applicationContext)

        val inboxLoader = runtime.redditInboxLoader
        if (inboxLoader == null) {
            Log.d(INBOX_BACKGROUND_LOG_TAG, "inbox-loader-unavailable account=$account")
            diagnosticStore.recordBackgroundFailureSafely(
                scopeKey = "inbox_background_loader",
                details = FailureDetails(
                    retryable = false,
                    localKind = LocalFailureKind.CONFIGURATION,
                ),
            )
        } else {
            // INBOX01_BACKGROUND_SYNC_RETHROWS_CANCELLATION_V1
            val refreshed = runCatching {
                inboxLoader.synchronize(account)
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
            }.getOrElse {
                Log.d(INBOX_BACKGROUND_LOG_TAG, "inbox-sync-exception account=$account")
                diagnosticStore.recordBackgroundFailureSafely(
                    scopeKey = "inbox_background_sync",
                    details = FailureDetails(
                        retryable = true,
                        localKind = LocalFailureKind.UNKNOWN,
                    ),
                )
                null
            }
            when (refreshed) {
                is AuthenticatedRedditInboxResult.Loaded -> {
                    // LOW_INBOX_WORKER_CLEARS_READ_NOTIFICATIONS_V1 (OCB-561):
                    // items read elsewhere leave the shade without the app open.
                    runCatching {
                        InboxNotificationCenter.reconcileInboxReadState(
                            context = applicationContext,
                            accountStableId = account,
                            items = refreshed.snapshot.items,
                            locallySeenIds = refreshed.snapshot.locallySeenIds,
                            readMutationItemIds = emptySet(),
                        )
                    }
                    val currentKeys = inboxBackgroundUnreadEventKeys(
                        items = refreshed.snapshot.items,
                        locallySeenIds = refreshed.snapshot.locallySeenIds,
                    )
                    val decision = runCatching {
                        eventStore.reconcile(
                            accountStableId = account,
                            currentEventKeys = currentKeys,
                        )
                    }.getOrElse {
                        Log.d(INBOX_BACKGROUND_LOG_TAG, "inbox-state-failed account=$account")
                        diagnosticStore.recordBackgroundFailureSafely(
                            scopeKey = "inbox_background_state",
                            details = FailureDetails(
                                retryable = false,
                                localKind = LocalFailureKind.STORAGE,
                            ),
                        )
                        null
                    }
                    if (decision != null) {
                        if (!decision.initializedBefore) {
                            Log.d(
                                INBOX_BACKGROUND_LOG_TAG,
                                "inbox-baseline account=$account unread=${currentKeys.size}",
                            )
                        } else {
                            val posted = InboxNotificationCenter.postNewEvents(
                                context = applicationContext,
                                accountStableId = account,
                                items = refreshed.snapshot.items,
                                locallySeenIds = refreshed.snapshot.locallySeenIds,
                                newEventKeys = decision.newEventKeys,
                            )
                            Log.d(
                                INBOX_BACKGROUND_LOG_TAG,
                                "inbox-applied account=$account unread=${currentKeys.size} " +
                                    "new=${decision.newEventKeys.size} posted=$posted",
                            )
                        }
                    }
                }
                AuthenticatedRedditInboxResult.AuthorizationRequired -> {
                    Log.d(INBOX_BACKGROUND_LOG_TAG, "inbox-authorization-required account=$account")
                    diagnosticStore.recordBackgroundAuthorizationRequiredSafely(
                        scopeKey = "inbox_background_authorization",
                    )
                }
                AuthenticatedRedditInboxResult.NotCached ->
                    Log.d(INBOX_BACKGROUND_LOG_TAG, "inbox-not-cached account=$account")
                is AuthenticatedRedditInboxResult.Failed -> {
                    Log.d(
                        INBOX_BACKGROUND_LOG_TAG,
                        "inbox-failed account=$account kind=${refreshed.kind.name.lowercase()}",
                    )
                    diagnosticStore.recordBackgroundFailureSafely(
                        scopeKey = "inbox_background_sync",
                        details = refreshed.details ?: FailureDetails(
                            retryable = refreshed.retryable,
                        ),
                    )
                }
                null -> Unit
            }
        }

        // LOW_INBOX_WORKER_SKIPS_UNWANTED_MODMAIL_V1 (OCB-562): with Modmail
        // notifications off nothing is fetched.
        val modmailLoader = runtime.redditModmailLoader
        if (!InboxNotificationCenter.modmailNotificationsWanted(applicationContext)) {
            Log.d(INBOX_BACKGROUND_LOG_TAG, "modmail-off account=$account")
        } else if (modmailLoader == null) {
            Log.d(INBOX_BACKGROUND_LOG_TAG, "modmail-loader-unavailable account=$account")
            diagnosticStore.recordBackgroundFailureSafely(
                scopeKey = "modmail_background_loader",
                details = FailureDetails(
                    retryable = false,
                    localKind = LocalFailureKind.CONFIGURATION,
                ),
            )
        } else {
            val unreadResult = runCatching {
                modmailLoader.loadUnread(account)
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
            }.getOrElse {
                Log.d(INBOX_BACKGROUND_LOG_TAG, "modmail-sync-exception account=$account")
                diagnosticStore.recordBackgroundFailureSafely(
                    scopeKey = "modmail_background_sync",
                    details = FailureDetails(
                        retryable = true,
                        localKind = LocalFailureKind.UNKNOWN,
                    ),
                )
                null
            }
            when (unreadResult) {
                is AuthenticatedRedditModmailUnreadResult.Loaded -> {
                    runCatching {
                        InboxNotificationCenter.reconcileModmailUnreadList(
                            context = applicationContext,
                            accountStableId = account,
                            unreadConversations = unreadResult.conversations,
                        )
                    }
                    val currentKeys = modmailBackgroundUnreadEventKeys(
                        unreadResult.conversations,
                    )
                    val decision = runCatching {
                        eventStore.reconcileModmail(
                            accountStableId = account,
                            currentEventKeys = currentKeys,
                        )
                    }.getOrElse {
                        Log.d(INBOX_BACKGROUND_LOG_TAG, "modmail-state-failed account=$account")
                        diagnosticStore.recordBackgroundFailureSafely(
                            scopeKey = "modmail_background_state",
                            details = FailureDetails(
                                retryable = false,
                                localKind = LocalFailureKind.STORAGE,
                            ),
                        )
                        null
                    }
                    if (decision != null) {
                        if (!decision.initializedBefore) {
                            Log.d(
                                INBOX_BACKGROUND_LOG_TAG,
                                "modmail-baseline account=$account unread=${currentKeys.size}",
                            )
                        } else {
                            val posted = InboxNotificationCenter.postNewModmailEvents(
                                context = applicationContext,
                                accountStableId = account,
                                conversations = unreadResult.conversations,
                                newEventKeys = decision.newEventKeys,
                            )
                            Log.d(
                                INBOX_BACKGROUND_LOG_TAG,
                                "modmail-applied account=$account unread=${currentKeys.size} " +
                                    "new=${decision.newEventKeys.size} posted=$posted",
                            )
                        }
                    }
                }
                AuthenticatedRedditModmailUnreadResult.AuthorizationRequired -> {
                    Log.d(
                        INBOX_BACKGROUND_LOG_TAG,
                        "modmail-authorization-required account=$account",
                    )
                    // OCB-562: Modmail's scope is asked for only when a
                    // moderator opens Modmail; without it this is the normal
                    // state (most accounts moderate nothing), not a failure to
                    // record every 15 minutes.
                }
                is AuthenticatedRedditModmailUnreadResult.Failed -> {
                    Log.d(
                        INBOX_BACKGROUND_LOG_TAG,
                        "modmail-failed account=$account kind=${unreadResult.kind.name.lowercase()}",
                    )
                    diagnosticStore.recordBackgroundFailureSafely(
                        scopeKey = "modmail_background_sync",
                        details = unreadResult.details ?: FailureDetails(
                            httpStatusCode = unreadResult.httpStatusCode,
                            retryable = unreadResult.retryable,
                        ),
                    )
                }
                null -> Unit
            }
        }

        return Result.success()
    }
}

private fun DiagnosticEventStore.recordBackgroundFailureSafely(
    scopeKey: String,
    details: FailureDetails?,
) {
    runCatching {
        recordBackgroundFailure(
            scopeKey = scopeKey,
            details = details,
        )
    }
}

private fun DiagnosticEventStore.recordBackgroundAuthorizationRequiredSafely(
    scopeKey: String,
) {
    runCatching {
        recordBackgroundAuthorizationRequired(scopeKey = scopeKey)
    }
}

internal const val INBOX_BACKGROUND_SYNC_INTERVAL_MINUTES = 15L
internal const val INBOX_BACKGROUND_EVENT_HISTORY_LIMIT = 512
internal const val INBOX_BACKGROUND_ACCOUNT_INPUT = "account_stable_id"
internal const val INBOX_BACKGROUND_SYNC_TAG = "subshell-inbox-background-sync"
internal const val INBOX_BACKGROUND_SYNC_UNIQUE_WORK = "subshell-inbox-background-sync-active-account"
private const val INBOX_BACKGROUND_PREFERENCES = "subshell_inbox_background"
private const val BASELINE_TAKEN_AT_SUFFIX = "@at"

/**
 * LOW_INBOX_STALE_BASELINE_IS_RETAKEN_V1 (OCB-557): the worker runs every
 * 15 minutes for the active account only. A baseline older than this (another
 * account was active, or notifications were off for weeks) no longer says
 * what the reader has seen; it is retaken silently instead of announcing
 * everything that arrived since as new. A baseline without a time (written
 * before this rule) or from the future counts as stale too.
 */
internal const val INBOX_BACKGROUND_BASELINE_MAX_AGE_MS = 2L * 60L * 60L * 1_000L

internal fun inboxBackgroundBaselineFresh(takenAtEpochMs: Long, nowEpochMs: Long): Boolean =
    takenAtEpochMs > 0L &&
        takenAtEpochMs <= nowEpochMs + 5L * 60L * 1_000L &&
        nowEpochMs - takenAtEpochMs <= INBOX_BACKGROUND_BASELINE_MAX_AGE_MS
private const val INBOX_BACKGROUND_INITIALIZED_PREFIX = "initialized:"
private const val INBOX_BACKGROUND_EVENTS_PREFIX = "events:"
private const val MODMAIL_BACKGROUND_INITIALIZED_PREFIX = "modmail_initialized:"
private const val MODMAIL_BACKGROUND_EVENTS_PREFIX = "modmail_events:"
private const val INBOX_BACKGROUND_LOG_TAG = "subshell-inbox-bg"

// INBOX06_WORKER_WAITS_FOR_ACCOUNT_DIRECTORY_V1: the worker's runtime is new,
// so it waits for the stored accounts before deciding the account is gone
// (the placeholder snapshot holds none and cancelled every sync). Null when
// it cannot tell, which never cancels.
internal suspend fun inboxWorkerAccountSignedIn(
    directory: AccountSessionDirectory?,
    accountStableId: String,
): Boolean? {
    val snapshot = directory ?: return null
    val hydrated = try {
        snapshot.hydratedSnapshot()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        return null
    }
    return hydrated.accounts.any { candidate ->
        candidate.stableId == accountStableId &&
            candidate.source == AccountSessionSource.AUTHENTICATED
    }
}
