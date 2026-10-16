package app.subshell.prototype.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.account.UserFlairUiState

/**
 * Transient bridge for user flair: the load/mutate/authorize requesters
 * SubshellApp installs, the last synchronised flair UI state, and the
 * load/set/authorize entry points the flair sheet uses. Extracted verbatim
 * from SubshellState, which holds one instance and delegates to it;
 * behaviour is unchanged.
 */
internal class UserFlairState(
    private val isAuthenticated: () -> Boolean,
    private val activeAccountIdProvider: () -> String,
) {
    private val activeAccountIsAuthenticated: Boolean
        get() = isAuthenticated()
    private val activeAccountId: String
        get() = activeAccountIdProvider()

    // USERFLAIR01A1_TRANSIENT_UI_RUNTIME_BRIDGE_V1
    private var requestAuthenticatedUserFlairLoad:
        (String, String, Boolean) -> Unit = { _, _, _ -> }
    private var requestAuthenticatedUserFlairMutation:
        (UserFlairMutationRequest) -> Unit = {}
    private var requestAuthenticatedUserFlairAuthorization:
        (String) -> Unit = {}
    var userFlairUiState by mutableStateOf(UserFlairUiState())
        private set

    fun setAuthenticatedUserFlairRequesters(
        loader: (String, String, Boolean) -> Unit,
        mutator: (UserFlairMutationRequest) -> Unit,
        authorizer: (String) -> Unit,
    ) {
        requestAuthenticatedUserFlairLoad = loader
        requestAuthenticatedUserFlairMutation = mutator
        requestAuthenticatedUserFlairAuthorization = authorizer
    }

    fun synchronizeUserFlairUiState(uiState: UserFlairUiState) {
        userFlairUiState = uiState
    }

    fun loadUserFlair(communityName: String, force: Boolean = false) {
        if (!activeAccountIsAuthenticated) return
        requestAuthenticatedUserFlairLoad(activeAccountId, communityName, force)
    }

    fun setUserFlair(communityName: String, templateId: String, text: String?) {
        if (!activeAccountIsAuthenticated) return
        requestAuthenticatedUserFlairMutation(
            UserFlairMutationRequest(
                accountStableId = activeAccountId,
                communityName = communityName,
                templateId = templateId,
                text = text,
            ),
        )
    }

    fun authorizeUserFlair() {
        if (!activeAccountIsAuthenticated) return
        requestAuthenticatedUserFlairAuthorization(activeAccountId)
    }
}
