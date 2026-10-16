package app.subshell.prototype.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditReportReasonPolicy
import app.subshell.prototype.data.account.RedditReportTargetKind
import app.subshell.prototype.data.account.toRedditReportReasonPolicy

/**
 * Transient bridge for the community report-reason policy: the requester
 * MainActivity/SubshellApp install, the last synchronised community-info UI
 * state, and the ensure/policy/loading/failed queries the report dialogs
 * use. Extracted verbatim from SubshellState, which holds one instance and
 * delegates to it; behaviour is unchanged.
 */
internal class ReportReasonPolicyState(
    private val isAuthenticated: () -> Boolean,
    private val activeAccountIdProvider: () -> String,
) {
    private val activeAccountIsAuthenticated: Boolean
        get() = isAuthenticated()
    private val activeAccountId: String
        get() = activeAccountIdProvider()

    // REPORT01A1_TRANSIENT_COMMUNITY_REPORT_POLICY_BRIDGE_V1
    private var requestAuthenticatedReportCommunityInfo:
        (String, String, Boolean) -> Unit = { _, _, _ -> }
    var reportReasonCommunityInfoState by mutableStateOf(RedditCommunityInfoUiState())
        private set

    fun setAuthenticatedReportReasonInfoRequester(
        requester: (String, String, Boolean) -> Unit,
    ) {
        requestAuthenticatedReportCommunityInfo = requester
    }

    fun synchronizeReportReasonCommunityInfo(
        uiState: RedditCommunityInfoUiState,
    ) {
        reportReasonCommunityInfoState = uiState
    }

    fun ensureReportReasonPolicy(
        communityName: String,
        force: Boolean = false,
    ) {
        if (!activeAccountIsAuthenticated) return
        val normalizedCommunity = communityName.trim().removePrefix("r/").trim()
        if (normalizedCommunity.isEmpty()) return
        val current = reportReasonCommunityInfoState
        if (
            !force &&
            current.matches(activeAccountId, normalizedCommunity) &&
            (current.loading || current.loaded)
        ) {
            return
        }
        requestAuthenticatedReportCommunityInfo(
            activeAccountId,
            normalizedCommunity,
            force,
        )
    }

    fun reportReasonPolicy(
        communityName: String,
        targetKind: RedditReportTargetKind,
    ): RedditReportReasonPolicy? {
        val current = reportReasonCommunityInfoState
        val normalizedCommunity = communityName.trim().removePrefix("r/").trim()
        if (
            !current.matches(activeAccountId, normalizedCommunity) ||
            !current.loaded
        ) {
            return null
        }
        return current.snapshot?.toRedditReportReasonPolicy(targetKind)
    }

    fun reportReasonPolicyLoading(communityName: String): Boolean {
        val current = reportReasonCommunityInfoState
        val normalizedCommunity = communityName.trim().removePrefix("r/").trim()
        return current.matches(activeAccountId, normalizedCommunity) && current.loading
    }

    fun reportReasonPolicyFailed(communityName: String): Boolean {
        val current = reportReasonCommunityInfoState
        val normalizedCommunity = communityName.trim().removePrefix("r/").trim()
        return current.matches(activeAccountId, normalizedCommunity) &&
            !current.loading &&
            current.failureKind != null
    }
}
