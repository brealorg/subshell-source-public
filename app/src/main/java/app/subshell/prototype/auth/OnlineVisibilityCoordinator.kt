package app.subshell.prototype.auth

import app.subshell.prototype.data.account.OnlineVisibilityConfirmation
import app.subshell.prototype.data.account.OnlineVisibilityIssue
import app.subshell.prototype.data.account.OnlineVisibilityNotice
import app.subshell.prototype.data.account.OnlineVisibilityPhase
import app.subshell.prototype.data.account.OnlineVisibilityResult
import app.subshell.prototype.data.account.OnlineVisibilityUiState
import app.subshell.prototype.data.account.OnlineVisibilityWriteResult
import app.subshell.prototype.data.account.RedditOnlineVisibilityService
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** All entry points are called on the UI dispatcher. Network/auth run off the main thread. */
internal class OnlineVisibilityCoordinator(
    private val scope: CoroutineScope,
    private val service: RedditOnlineVisibilityService?,
    private val activeAccountId: () -> String?,
) {
    private val mutableState = MutableStateFlow(OnlineVisibilityUiState())
    val uiState = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var revision = 0L

    fun open(account: String) {
        invalidate()
        mutableState.value = OnlineVisibilityUiState(accountStableId = account, revision = ++revision)
        if (activeAccountId() != account || account.isBlank()) {
            fail(OnlineVisibilityIssue.ACCOUNT_CHANGED)
            return
        }
        refresh(account)
    }

    fun close(account: String) {
        if (!mutableState.value.matches(account)) return
        invalidate()
        mutableState.value = OnlineVisibilityUiState(revision = ++revision)
    }

    fun accountChanged() {
        val account = mutableState.value.accountStableId ?: return
        if (activeAccountId() != account) close(account)
    }

    private fun invalidate() {
        generation++
        job?.cancel()
        job = null
    }

    private fun current(account: String, token: Long): Boolean =
        generation == token && mutableState.value.matches(account) && activeAccountId() == account

    fun refresh(account: String) {
        val old = mutableState.value
        if (!old.matches(account) || old.busy || activeAccountId() != account) return
        val service = service ?: run { fail(OnlineVisibilityIssue.CONFIGURATION); return }
        val token = ++generation
        mutableState.value = old.copy(
            phase = OnlineVisibilityPhase.LOADING, value = null, confirmation = null,
            issue = null, httpStatusCode = null, failureDetails = null, notice = null, revision = ++revision,
        )
        job = scope.launch {
            try {
                val result = service.read(account) { current(account, token) }
                if (!current(account, token)) return@launch
                mutableState.value = when (result) {
                    is OnlineVisibilityResult.Known -> mutableState.value.copy(
                        value = result.value, phase = OnlineVisibilityPhase.READY,
                    )
                    OnlineVisibilityResult.Unavailable -> mutableState.value.copy(
                        value = null, phase = OnlineVisibilityPhase.UNAVAILABLE,
                    )
                    is OnlineVisibilityResult.Failed -> mutableState.value.copy(
                        value = null, phase = OnlineVisibilityPhase.FAILED,
                        issue = result.issue, httpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
                }
            } catch (cancelled: CancellationException) {
                if (current(account, token)) fail(OnlineVisibilityIssue.TRANSPORT)
                throw cancelled
            } catch (_: Exception) {
                if (current(account, token)) fail(OnlineVisibilityIssue.TRANSPORT)
            }
        }
    }

    fun propose(account: String, desired: Boolean) {
        val state = mutableState.value
        val value = state.value ?: return
        if (!state.matches(account) || activeAccountId() != account || !state.canChange ||
            state.confirmation != null || value == desired
        ) return
        mutableState.value = state.copy(
            confirmation = OnlineVisibilityConfirmation(account, value, desired, state.revision),
            notice = null,
        )
    }

    fun cancelConfirmation() {
        mutableState.value = mutableState.value.copy(confirmation = null)
    }

    fun confirm(confirmation: OnlineVisibilityConfirmation) {
        val old = mutableState.value
        if (old.confirmation != confirmation || !old.canChange ||
            old.revision != confirmation.revision || old.value != confirmation.expectedValue ||
            activeAccountId() != confirmation.accountStableId
        ) return
        val service = service ?: run { fail(OnlineVisibilityIssue.CONFIGURATION); return }
        val token = ++generation
        // Consume confirmation synchronously, before launching, so double taps cannot queue writes.
        mutableState.value = old.copy(
            phase = OnlineVisibilityPhase.SAVING, confirmation = null,
            value = null, issue = null, httpStatusCode = null, failureDetails = null, notice = null, revision = ++revision,
        )
        job = scope.launch {
            try {
                val result = service.change(confirmation) { current(confirmation.accountStableId, token) }
                if (!current(confirmation.accountStableId, token)) return@launch
                mutableState.value = when (result) {
                    is OnlineVisibilityWriteResult.Verified -> mutableState.value.copy(
                        value = result.value, phase = OnlineVisibilityPhase.READY,
                        notice = if (result.acknowledged) OnlineVisibilityNotice.SAVED
                            else OnlineVisibilityNotice.CURRENT_VALUE_VERIFIED,
                    )
                    is OnlineVisibilityWriteResult.Changed -> mutableState.value.copy(
                        value = result.value, phase = OnlineVisibilityPhase.READY,
                        issue = OnlineVisibilityIssue.CHANGED_ON_REDDIT,
                    )
                    OnlineVisibilityWriteResult.Unavailable -> mutableState.value.copy(
                        value = null, phase = OnlineVisibilityPhase.UNAVAILABLE,
                    )
                    is OnlineVisibilityWriteResult.Failed -> mutableState.value.copy(
                        // An uncertain write must not project an old value as current state.
                        value = null, phase = OnlineVisibilityPhase.FAILED,
                        issue = result.issue, httpStatusCode = result.httpStatusCode,
                        failureDetails = result.failureDetails,
                    )
                }
            } catch (cancelled: CancellationException) {
                if (current(confirmation.accountStableId, token)) fail(OnlineVisibilityIssue.OUTCOME_UNKNOWN)
                throw cancelled
            } catch (_: Exception) {
                if (current(confirmation.accountStableId, token)) fail(OnlineVisibilityIssue.OUTCOME_UNKNOWN)
            }
        }
    }

    private fun fail(issue: OnlineVisibilityIssue) {
        mutableState.value = mutableState.value.copy(
            value = null, phase = OnlineVisibilityPhase.FAILED, issue = issue,
            confirmation = null, notice = null, failureDetails = null,
        )
    }
}
