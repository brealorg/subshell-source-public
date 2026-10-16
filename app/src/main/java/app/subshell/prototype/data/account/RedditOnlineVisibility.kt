package app.subshell.prototype.data.account

/** USERSTATUS01A1: an account preference, never evidence of current activity. */
enum class OnlineVisibilityPhase { IDLE, LOADING, READY, SAVING, UNAVAILABLE, FAILED }
enum class OnlineVisibilityIssue {
    AUTHORIZATION, ACCOUNT_CHANGED, HTTP, TRANSPORT, PROTOCOL, CONFIGURATION,
    CHANGED_ON_REDDIT, OUTCOME_UNKNOWN,
}
enum class OnlineVisibilityNotice { SAVED, CURRENT_VALUE_VERIFIED }

data class OnlineVisibilityConfirmation(
    val accountStableId: String,
    val expectedValue: Boolean,
    val desiredValue: Boolean,
    val revision: Long,
)

data class OnlineVisibilityUiState(
    val accountStableId: String? = null,
    val value: Boolean? = null,
    val phase: OnlineVisibilityPhase = OnlineVisibilityPhase.IDLE,
    val issue: OnlineVisibilityIssue? = null,
    val httpStatusCode: Int? = null,
    val notice: OnlineVisibilityNotice? = null,
    val confirmation: OnlineVisibilityConfirmation? = null,
    val revision: Long = 0L,
) {
    val busy: Boolean get() = phase == OnlineVisibilityPhase.LOADING ||
        phase == OnlineVisibilityPhase.SAVING
    val canChange: Boolean get() = phase == OnlineVisibilityPhase.READY && value != null
    fun matches(account: String): Boolean = accountStableId == account
}

internal sealed interface OnlineVisibilityResult {
    data class Known(val value: Boolean) : OnlineVisibilityResult
    data object Unavailable : OnlineVisibilityResult
    data class Failed(
        val issue: OnlineVisibilityIssue,
        val httpStatusCode: Int? = null,
    ) : OnlineVisibilityResult
}

internal sealed interface OnlineVisibilityWriteResult {
    data class Verified(
        val value: Boolean,
        // A matching GET after an ambiguous response proves current state, not causation.
        val acknowledged: Boolean,
    ) : OnlineVisibilityWriteResult
    data class Changed(val value: Boolean) : OnlineVisibilityWriteResult
    data object Unavailable : OnlineVisibilityWriteResult
    data class Failed(
        val issue: OnlineVisibilityIssue,
        val observedValue: Boolean? = null,
        val httpStatusCode: Int? = null,
    ) : OnlineVisibilityWriteResult
}

internal interface RedditOnlineVisibilityService {
    suspend fun read(account: String, isCurrent: () -> Boolean): OnlineVisibilityResult
    suspend fun change(
        confirmation: OnlineVisibilityConfirmation,
        isCurrent: () -> Boolean,
    ): OnlineVisibilityWriteResult
}
