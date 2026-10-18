package app.subshell.prototype.auth

import app.subshell.prototype.data.account.ModeratorContentControlConfirmation
import app.subshell.prototype.data.account.ModeratorContentControlsNotice
import app.subshell.prototype.data.account.ModeratorContentControlsUiState
import app.subshell.prototype.data.account.ModeratorReportTarget
import app.subshell.prototype.data.account.RedditModeratorActionFailureKind
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.contentControlConfirmationOrNull
import app.subshell.prototype.data.account.desiredDistinguishedState
import app.subshell.prototype.data.account.desiredLockedState
import app.subshell.prototype.data.account.desiredStateMatches
import app.subshell.prototype.data.account.reportTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditReportTargetResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModerationActionResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

// MODERATION02A3_EXACT_TARGET_CONTENT_CONTROL_COORDINATOR_V1
internal class ModeratorContentControlsCoordinator(
    private val loader: AuthenticatedRedditModerationLoader?,
    private val activeAccountStableId: () -> String?,
    private val activeAccountUsername: () -> String?,
    private val performAction: suspend (
        RedditModeratorActionRequest,
        suspend () -> Boolean,
    ) -> AuthenticatedRedditModerationActionResult,
) {
    private val mutableState = MutableStateFlow(ModeratorContentControlsUiState())
    val uiState: StateFlow<ModeratorContentControlsUiState> = mutableState.asStateFlow()
    private val mutationMutex = Mutex()
    private var generation = 0L

    fun accountChanged(accountStableId: String) {
        if (mutableState.value.accountStableId == accountStableId) return
        generation += 1L
        mutableState.value = ModeratorContentControlsUiState(
            accountStableId = accountStableId,
            revision = generation,
        )
    }

    fun open(accountStableId: String, target: ModeratorReportTarget) {
        if (activeAccountStableId() != accountStableId) return
        accountChanged(accountStableId)
        if (mutableState.value.mutating) return
        generation += 1L
        mutableState.value = ModeratorContentControlsUiState(
            accountStableId = accountStableId,
            visible = true,
            target = target,
            revision = generation,
        )
    }

    fun dismiss() {
        generation += 1L
        mutableState.value = ModeratorContentControlsUiState(
            accountStableId = mutableState.value.accountStableId,
            revision = generation,
        )
    }

    suspend fun refresh(accountStableId: String) {
        val before = mutableState.value
        val target = before.target ?: return
        if (
            !before.visible ||
            before.accountStableId != accountStableId ||
            before.busy ||
            activeAccountStableId() != accountStableId
        ) {
            return
        }
        generation += 1L
        val token = generation
        mutableState.value = before.copy(
            item = null,
            loading = true,
            revision = token,
            notice = null,
            failureDetails = null,
        )
        try {
            val result = read(accountStableId, target)
            if (!isCurrent(token, accountStableId)) return
            applyRead(result)
        } catch (cancelled: CancellationException) {
            if (isCurrent(token, accountStableId)) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    item = null,
                    notice = ModeratorContentControlsNotice.READ_FAILED,
                )
            }
            throw cancelled
        }
    }

    suspend fun confirm(confirmation: ModeratorContentControlConfirmation) {
        if (!mutationMutex.tryLock()) return
        try {
            val owner = activeAccountStableId().orEmpty()
            val actor = activeAccountUsername().orEmpty()
            val current = contentControlConfirmationOrNull(
                state = mutableState.value,
                activeAccountStableId = owner,
                activeUsername = actor,
                action = confirmation.action,
            )
            if (current != confirmation) return

            val desiredLocked = confirmation.action.desiredLockedState()
            val desiredDistinguished = confirmation.action.desiredDistinguishedState()
            if (desiredLocked == null && desiredDistinguished == null) return

            generation += 1L
            val token = generation
            mutableState.value = mutableState.value.copy(
                mutating = true,
                revision = token,
                notice = null,
                failureDetails = null,
            )
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
                        val fresh = read(
                            confirmation.accountStableId,
                            confirmation.target,
                        )
                        if (!isCurrent(token, confirmation.accountStableId)) {
                            false
                        } else {
                            val item =
                                (fresh as? AuthenticatedRedditReportTargetResult.Loaded)?.item
                            val actorNow = activeAccountUsername()
                                ?.trim()
                                ?.removePrefix("u/")
                            val ownerMatches =
                                actorNow != null &&
                                    actorNow.equals(
                                        confirmation.actorUsername,
                                        ignoreCase = true,
                                    )
                            val permitted =
                                item?.contentState?.canModerate == true &&
                                    item.reportDetails.canModerate == true
                            val stateMatches = when {
                                desiredLocked != null ->
                                    item?.contentState?.locked == !desiredLocked
                                desiredDistinguished != null ->
                                    item?.contentState?.distinguished ==
                                        !desiredDistinguished &&
                                        item.author.equals(
                                            confirmation.actorUsername,
                                            ignoreCase = true,
                                        )
                                else -> false
                            }
                            val matches =
                                item != null &&
                                    ownerMatches &&
                                    permitted &&
                                    stateMatches
                            if (!matches) {
                                preflightRejected = true
                                applyRead(fresh)
                                mutableState.value = mutableState.value.copy(
                                    notice = when {
                                        item == null -> mutableState.value.notice
                                        !ownerMatches ->
                                            ModeratorContentControlsNotice.ACCOUNT_CHANGED
                                        !permitted ->
                                            ModeratorContentControlsNotice.NO_PERMISSION
                                        else ->
                                            ModeratorContentControlsNotice.STATE_CHANGED
                                    },
                                )
                            }
                            matches
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                if (isCurrent(token, confirmation.accountStableId)) {
                    mutableState.value = mutableState.value.copy(
                        item = null,
                        mutating = false,
                        notice = ModeratorContentControlsNotice.NOT_CONFIRMED,
                    )
                }
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditModerationActionResult.Failed(
                    RedditModeratorActionFailureKind.OUTCOME_UNKNOWN,
                )
            }

            if (!isCurrent(token, confirmation.accountStableId)) return
            if (preflightRejected) {
                mutableState.value = mutableState.value.copy(mutating = false)
                return
            }
            when (result) {
                AuthenticatedRedditModerationActionResult.AuthorizationRequired ->
                    mutableState.value = mutableState.value.copy(
                        item = null,
                        mutating = false,
                        notice = ModeratorContentControlsNotice.AUTHORIZATION_REQUIRED,
                    )

                is AuthenticatedRedditModerationActionResult.Updated ->
                    verifyReadback(
                        confirmation = confirmation,
                        token = token,
                        unknownOutcome = false,
                    )

                is AuthenticatedRedditModerationActionResult.Failed -> {
                    if (
                        result.kind ==
                        RedditModeratorActionFailureKind.OUTCOME_UNKNOWN
                    ) {
                        verifyReadback(
                            confirmation = confirmation,
                            token = token,
                            unknownOutcome = true,
                        )
                    } else {
                        mutableState.value = mutableState.value.copy(
                            item = null,
                            mutating = false,
                            failureDetails = result.failureDetails,
                            notice = when (result.kind) {
                                RedditModeratorActionFailureKind.ALREADY_MUTATING ->
                                    ModeratorContentControlsNotice.BUSY
                                RedditModeratorActionFailureKind.ACCOUNT_CHANGED ->
                                    ModeratorContentControlsNotice.ACCOUNT_CHANGED
                                RedditModeratorActionFailureKind.AUTHORIZATION ->
                                    ModeratorContentControlsNotice.NO_PERMISSION
                                else ->
                                    ModeratorContentControlsNotice.WRITE_FAILED
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
        confirmation: ModeratorContentControlConfirmation,
        token: Long,
        unknownOutcome: Boolean,
    ) {
        mutableState.value = mutableState.value.copy(item = null, loading = true)
        val result = try {
            read(confirmation.accountStableId, confirmation.target)
        } catch (cancelled: CancellationException) {
            if (isCurrent(token, confirmation.accountStableId)) {
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    mutating = false,
                    notice = ModeratorContentControlsNotice.NOT_CONFIRMED,
                )
            }
            throw cancelled
        }
        if (!isCurrent(token, confirmation.accountStableId)) return
        applyRead(result)
        val item =
            (result as? AuthenticatedRedditReportTargetResult.Loaded)?.item
        val verified =
            item?.contentState?.canModerate == true &&
                item.reportDetails.canModerate == true &&
                confirmation.desiredStateMatches(item)
        mutableState.value = mutableState.value.copy(
            mutating = false,
            notice = when {
                !verified -> ModeratorContentControlsNotice.NOT_CONFIRMED
                unknownOutcome ->
                    ModeratorContentControlsNotice.VERIFIED_AFTER_UNKNOWN
                else -> ModeratorContentControlsNotice.VERIFIED
            },
        )
    }

    private fun isCurrent(token: Long, owner: String): Boolean =
        generation == token &&
            mutableState.value.visible &&
            mutableState.value.accountStableId == owner &&
            activeAccountStableId() == owner

    private suspend fun read(
        owner: String,
        target: ModeratorReportTarget,
    ): AuthenticatedRedditReportTargetResult {
        if (activeAccountStableId() != owner) {
            return AuthenticatedRedditReportTargetResult.NotFound
        }
        val result = try {
            loader?.loadReportTarget(owner, target)
                ?: return AuthenticatedRedditReportTargetResult.Failed(
                    AuthenticatedRedditModerationFailureKind.CLIENT_CONFIGURATION,
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return AuthenticatedRedditReportTargetResult.Failed(
                AuthenticatedRedditModerationFailureKind.TRANSPORT,
            )
        }
        if (result is AuthenticatedRedditReportTargetResult.Loaded) {
            val item = result.item
            if (
                item.fullname != target.fullname ||
                target.communityName == null ||
                target.communityName != item.communityName
            ) {
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
                val permitted =
                    item.contentState.canModerate == true &&
                        item.reportDetails.canModerate == true
                current.copy(
                    item = item,
                    target = target,
                    loading = false,
                    notice = when {
                        !permitted -> ModeratorContentControlsNotice.NO_PERMISSION
                        item.contentState.locked == null &&
                            item.contentState.distinguished == null ->
                            ModeratorContentControlsNotice.STATE_UNKNOWN
                        else -> null
                    },
                )
            }

            AuthenticatedRedditReportTargetResult.NotFound ->
                current.copy(
                    item = null,
                    loading = false,
                    notice = ModeratorContentControlsNotice.NOT_FOUND,
                )

            is AuthenticatedRedditReportTargetResult.Failed ->
                current.copy(
                    item = null,
                    loading = false,
                    notice = ModeratorContentControlsNotice.READ_FAILED,
                    failureDetails = result.details,
                )
        }
    }
}
