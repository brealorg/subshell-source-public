package app.subshell.prototype.data.account

import kotlinx.coroutines.flow.StateFlow

enum class AccountSessionSource {
    LOCAL_FIXTURE,
    AUTHENTICATED,
}

enum class AccountSessionState {
    LOCAL_ONLY,
    CONNECTED,
    REAUTHENTICATION_REQUIRED,
}

data class AccountSessionSummary(
    val stableId: String,
    val username: String,
    val source: AccountSessionSource,
    val state: AccountSessionState,
    val providerLabel: String? = null,
) {
    init {
        require(stableId.isNotBlank()) { "Account stable ID cannot be blank" }
        require(username.isNotBlank()) { "Account username cannot be blank" }
        require(providerLabel == null || providerLabel.isNotBlank()) {
            "Account provider label cannot be blank"
        }
    }
}

data class AccountSessionDirectorySnapshot(
    val accounts: List<AccountSessionSummary>,
    val activeAccountId: String,
) {
    init {
        require(accounts.isNotEmpty()) { "Account session directory cannot be empty" }
        require(accounts.map(AccountSessionSummary::stableId).distinct().size == accounts.size) {
            "Account stable IDs must be unique"
        }
        require(activeAccountId in accounts.map(AccountSessionSummary::stableId)) {
            "Active account must exist in the account session directory"
        }
    }
}

interface AccountSessionDirectory {
    val revision: StateFlow<Long>?
        get() = null

    fun snapshot(): AccountSessionDirectorySnapshot

    fun selectAccount(stableId: String): AccountSessionDirectorySnapshot

    // INBOX06_WORKER_WAITS_FOR_ACCOUNT_DIRECTORY_V1: the snapshot once stored
    // accounts are loaded. A freshly created runtime hydrates asynchronously,
    // so snapshot() can still be the placeholder without them.
    suspend fun hydratedSnapshot(): AccountSessionDirectorySnapshot = snapshot()

    // ISSUE119_COMPOSE_AFTER_THE_ACCOUNT_DIRECTORY_V1: true once the stored
    // accounts are loaded (no wait needed before composing).
    val isHydrated: Boolean
        get() = true
}

fun localFixtureAccountId(username: String): String =
    "local:" + username.trim().lowercase()

fun localFixtureAccountSummaries(usernames: Iterable<String>): List<AccountSessionSummary> =
    usernames
        .map { username -> username.trim() }
        .filter { username -> username.isNotEmpty() }
        .distinctBy { username -> username.lowercase() }
        .map { username ->
            AccountSessionSummary(
                stableId = localFixtureAccountId(username),
                username = username,
                source = AccountSessionSource.LOCAL_FIXTURE,
                state = AccountSessionState.LOCAL_ONLY,
            )
        }
