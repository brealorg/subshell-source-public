package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorReportConfirmation
import app.subshell.prototype.data.account.ModeratorReportTarget
import app.subshell.prototype.data.account.ModeratorReportsNotice
import app.subshell.prototype.data.account.ModeratorReportsUiState
import app.subshell.prototype.data.account.RedditModeratorActionFailureKind
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.desiredReportsIgnored
import app.subshell.prototype.data.account.reportConfirmationOrNull
import app.subshell.prototype.data.account.reportTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditReportTargetResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModerationActionResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

// MODERATION02A2_EXACT_TARGET_REPORT_REVIEW_AND_VERIFIED_READBACK_V1
internal class ModeratorReportsCoordinator(
    private val loader: AuthenticatedRedditModerationLoader?,
    private val activeAccountStableId: () -> String?,
    private val performAction: suspend (
        RedditModeratorActionRequest,
        suspend () -> Boolean,
    ) -> AuthenticatedRedditModerationActionResult,
) {
    private val mutableState = MutableStateFlow(ModeratorReportsUiState())
    val uiState: StateFlow<ModeratorReportsUiState> = mutableState.asStateFlow()
    private val mutationMutex = Mutex()
    private var generation = 0L

    fun accountChanged(accountStableId: String) {
        if (mutableState.value.accountStableId == accountStableId) return
        generation += 1
        mutableState.value = ModeratorReportsUiState(accountStableId = accountStableId)
    }

    fun open(accountStableId: String, target: ModeratorReportTarget?) {
        if (activeAccountStableId() != accountStableId) return
        accountChanged(accountStableId)
        if (mutableState.value.mutating) return
        generation += 1
        mutableState.value = ModeratorReportsUiState(
            accountStableId = accountStableId,
            visible = true,
            target = target,
            revision = generation,
            recentTargets = mutableState.value.recentTargets,
        )
    }

    fun dismiss() {
        // Do not cancel a sent write. A late result remains owner-bound and the
        // user can reopen the inspector via exact target lookup.
        generation += 1
        mutableState.value = ModeratorReportsUiState(
            accountStableId = mutableState.value.accountStableId,
            recentTargets = mutableState.value.recentTargets,
            revision = generation,
        )
    }

    suspend fun refresh(accountStableId: String) {
        val before = mutableState.value
        val target = before.target ?: return
        if (!before.visible || before.accountStableId != accountStableId || before.busy) return
        if (activeAccountStableId() != accountStableId) return
        generation += 1
        val token = generation
        mutableState.value = before.copy(item = null, loading = true, revision = token, notice = null, failureDetails = null)
        try {
            val result = read(accountStableId, target)
            if (!isCurrent(token, accountStableId)) return
            applyRead(result)
        } catch (cancelled: CancellationException) {
            if (isCurrent(token, accountStableId)) {
                mutableState.value = mutableState.value.copy(loading = false, item = null, notice = ModeratorReportsNotice.READ_FAILED)
            }
            throw cancelled
        }
    }

    suspend fun confirm(confirmation: ModeratorReportConfirmation) {
        if (!mutationMutex.tryLock()) return
        try {
            if (reportConfirmationOrNull(mutableState.value, activeAccountStableId().orEmpty()) != confirmation) return
            val desired = confirmation.action.desiredReportsIgnored() ?: return
            generation += 1 // Consume this confirmation before the first suspension.
            val token = generation
            mutableState.value = mutableState.value.copy(mutating = true, revision = token, notice = null, failureDetails = null)
            val request = RedditModeratorActionRequest(
                accountStableId = confirmation.accountStableId,
                communityName = requireNotNull(confirmation.target.communityName),
                thingFullname = confirmation.target.fullname,
                action = confirmation.action,
            )
            var preflightRejected = false
            val result = try {
                performAction(request) {
                    if (!isCurrent(token, confirmation.accountStableId)) {
                        false
                    } else {
                        val fresh = read(confirmation.accountStableId, confirmation.target)
                        if (!isCurrent(token, confirmation.accountStableId)) {
                            false
                        } else {
                            val item = (fresh as? AuthenticatedRedditReportTargetResult.Loaded)?.item
                            val permitted = item?.reportDetails?.canModerate == true
                            val matches = item != null && permitted && item.reportDetails.ignored == !desired
                            if (!matches) {
                                preflightRejected = true
                                applyRead(fresh)
                                mutableState.value = mutableState.value.copy(
                                    notice = when {
                                        item == null -> mutableState.value.notice
                                        !permitted -> ModeratorReportsNotice.NO_PERMISSION
                                        item.reportDetails.ignored == null -> ModeratorReportsNotice.STATE_UNKNOWN
                                        else -> ModeratorReportsNotice.SETTINGS_CHANGED
                                    },
                                )
                            }
                            matches
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                if (isCurrent(token, confirmation.accountStableId)) {
                    mutableState.value = mutableState.value.copy(item = null, mutating = false, notice = ModeratorReportsNotice.NOT_CONFIRMED)
                }
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditModerationActionResult.Failed(RedditModeratorActionFailureKind.OUTCOME_UNKNOWN)
            }
            if (!isCurrent(token, confirmation.accountStableId)) return
            if (preflightRejected) {
                mutableState.value = mutableState.value.copy(mutating = false)
                return
            }
            when (result) {
                AuthenticatedRedditModerationActionResult.AuthorizationRequired ->
                    mutableState.value = mutableState.value.copy(item = null, mutating = false, notice = ModeratorReportsNotice.AUTHORIZATION_REQUIRED)
                is AuthenticatedRedditModerationActionResult.Updated -> verifyReadback(confirmation, token, desired, false)
                is AuthenticatedRedditModerationActionResult.Failed -> {
                    if (result.kind == RedditModeratorActionFailureKind.OUTCOME_UNKNOWN) {
                        verifyReadback(confirmation, token, desired, true)
                    } else {
                        mutableState.value = mutableState.value.copy(
                            item = null, mutating = false,
                            failureDetails = result.failureDetails,
                            notice = when (result.kind) {
                                RedditModeratorActionFailureKind.ALREADY_MUTATING -> ModeratorReportsNotice.BUSY
                                RedditModeratorActionFailureKind.ACCOUNT_CHANGED -> ModeratorReportsNotice.ACCOUNT_CHANGED
                                RedditModeratorActionFailureKind.AUTHORIZATION -> ModeratorReportsNotice.NO_PERMISSION
                                else -> ModeratorReportsNotice.WRITE_FAILED
                            },
                        )
                    }
                }
            }
        } finally {
            mutationMutex.unlock()
        }
    }

    private suspend fun verifyReadback(
        confirmation: ModeratorReportConfirmation,
        token: Long,
        desired: Boolean,
        unknownOutcome: Boolean,
    ) {
        mutableState.value = mutableState.value.copy(item = null, loading = true)
        val result = try {
            read(confirmation.accountStableId, confirmation.target)
        } catch (cancelled: CancellationException) {
            if (isCurrent(token, confirmation.accountStableId)) {
                mutableState.value = mutableState.value.copy(item = null, loading = false, mutating = false, notice = ModeratorReportsNotice.NOT_CONFIRMED)
            }
            throw cancelled
        }
        if (!isCurrent(token, confirmation.accountStableId)) return
        applyRead(result)
        val item = (result as? AuthenticatedRedditReportTargetResult.Loaded)?.item
        val verified = item?.reportDetails?.canModerate == true && item.reportDetails.ignored == desired
        mutableState.value = mutableState.value.copy(
            mutating = false,
            notice = when {
                !verified -> ModeratorReportsNotice.NOT_CONFIRMED
                unknownOutcome -> ModeratorReportsNotice.VERIFIED_AFTER_UNKNOWN
                else -> ModeratorReportsNotice.VERIFIED
            },
        )
    }

    private fun isCurrent(token: Long, owner: String): Boolean =
        generation == token && mutableState.value.visible &&
            mutableState.value.accountStableId == owner && activeAccountStableId() == owner

    private suspend fun read(owner: String, target: ModeratorReportTarget): AuthenticatedRedditReportTargetResult {
        if (activeAccountStableId() != owner) return AuthenticatedRedditReportTargetResult.NotFound
        val result = try {
            loader?.loadReportTarget(owner, target)
                ?: return AuthenticatedRedditReportTargetResult.Failed(
                    app.subshell.prototype.data.auth.AuthenticatedRedditModerationFailureKind.CLIENT_CONFIGURATION,
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return AuthenticatedRedditReportTargetResult.Failed(
                app.subshell.prototype.data.auth.AuthenticatedRedditModerationFailureKind.TRANSPORT,
            )
        }
        if (result is AuthenticatedRedditReportTargetResult.Loaded) {
            val item = result.item
            if (item.fullname != target.fullname || (target.communityName != null && target.communityName != item.communityName)) {
                return AuthenticatedRedditReportTargetResult.NotFound
            }
        }
        return result
    }

    private fun applyRead(result: AuthenticatedRedditReportTargetResult) {
        val current = mutableState.value
        mutableState.value = when (result) {
            is AuthenticatedRedditReportTargetResult.Loaded -> {
                val item = result.item
                val target = item.reportTarget()
                val allowed = item.reportDetails.canModerate == true
                current.copy(
                    item = item, target = target, loading = false,
                    recentTargets = if (allowed) (listOf(target) + current.recentTargets).distinct().take(20) else current.recentTargets,
                    notice = when {
                        !allowed -> ModeratorReportsNotice.NO_PERMISSION
                        item.reportDetails.ignored == null -> ModeratorReportsNotice.STATE_UNKNOWN
                        else -> null
                    },
                )
            }
            AuthenticatedRedditReportTargetResult.NotFound -> current.copy(item = null, loading = false, notice = ModeratorReportsNotice.NOT_FOUND)
            is AuthenticatedRedditReportTargetResult.Failed -> current.copy(
                item = null, loading = false, notice = ModeratorReportsNotice.READ_FAILED, failureDetails = result.details,
            )
        }
    }
}
