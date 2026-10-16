package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.NsfwPreferencesResult
import app.subshell.prototype.data.account.QuarantineOptInResult
import app.subshell.prototype.data.account.RedditNsfwPreferencesReader
import app.subshell.prototype.data.account.RedditQuarantineOptIn
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.reddit.sendQuarantineOptIn
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.reddit.NsfwPreferencesNetwork
import kotlin.coroutines.cancellation.CancellationException

internal val NSFW_PREFERENCES_READ_SCOPES = setOf("identity")

// ISSUE211_QUARANTINE_OPT_IN_NEEDS_SUBSCRIBE_V1: POST api/quarantine_optin is
// @require_oauth2_scope("subscribe") in Reddit's source.
internal val QUARANTINE_OPT_IN_SCOPES = setOf("identity", "subscribe")

/**
 * ISSUE211_ACCOUNT_NSFW_PREFERENCE_V1 (#211): reads the active account's
 * over_18 preference with its own session (the identity scope every account
 * has), the way ConfiguredRedditOnlineVisibilityService reads show_presence.
 * Any session problem or account change is Failed: the notice says nothing.
 */
internal class ConfiguredRedditCommunityAccessService(
    private val configuration: OAuthProviderConfiguration,
    private val activeAccountId: () -> String?,
    private val ensureSession: suspend (String, Set<String>) -> AuthSessionResult,
    private val storedSessionGrants: (String, Set<String>) -> Boolean = { _, _ -> true },
    private val networkForSession: (StoredOAuthSession) -> NsfwPreferencesNetwork,
    // ISSUE211_QUARANTINE_OPT_IN_V1: a non-retrying client for the opt-in.
    private val writeClientForSession: ((StoredOAuthSession) -> SubshellHttpClient)? = null,
) : app.subshell.prototype.data.account.RedditCommunityAccessService {
    private fun current(account: String, isCurrent: () -> Boolean): Boolean =
        account.isNotBlank() && activeAccountId() == account && isCurrent()

    override suspend fun read(account: String, isCurrent: () -> Boolean): NsfwPreferencesResult = try {
        val session = session(account, isCurrent)
        if (session == null || !current(account, isCurrent)) {
            // ISSUE211_ACCOUNT_PREFERENCE_FAILURE_IS_NAMED_V1 (debug only).
            app.subshell.prototype.auth.authDebugLog(
                "subshell-nsfw",
                "account-prefs no-session current=${current(account, isCurrent)} " +
                    "grants=${storedSessionGrants(account, NSFW_PREFERENCES_READ_SCOPES)}",
            )
            NsfwPreferencesResult.Failed
        } else {
            val result = networkForSession(session).read()
            if (current(account, isCurrent)) result else NsfwPreferencesResult.Failed
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        app.subshell.prototype.auth.authDebugLog("subshell-nsfw", "account-prefs threw ${failure.javaClass.simpleName}")
        NsfwPreferencesResult.Failed
    }

    override suspend fun optIn(
        account: String,
        communityName: String,
        isCurrent: () -> Boolean,
    ): QuarantineOptInResult {
        val client = writeClientForSession ?: return QuarantineOptInResult.Refused(null)
        if (!current(account, isCurrent)) return QuarantineOptInResult.Refused(null)
        if (!storedSessionGrants(account, QUARANTINE_OPT_IN_SCOPES)) {
            return QuarantineOptInResult.MissingPermission
        }
        val session = session(account, isCurrent, QUARANTINE_OPT_IN_SCOPES)
            ?: return QuarantineOptInResult.Refused(null)
        if (!current(account, isCurrent)) return QuarantineOptInResult.Refused(null)
        return try {
            client(session).sendQuarantineOptIn(communityName)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            QuarantineOptInResult.OutcomeUnknown
        }
    }

    private suspend fun session(
        account: String,
        isCurrent: () -> Boolean,
        scopes: Set<String> = NSFW_PREFERENCES_READ_SCOPES,
    ): StoredOAuthSession? {
        if (!current(account, isCurrent)) return null
        if (!storedSessionGrants(account, scopes)) return null
        val result = ensureSession(account, scopes)
        if (!current(account, isCurrent)) return null
        val session = (result as? AuthSessionResult.Authenticated)?.session ?: return null
        val identity = session.identity ?: return null
        return session.takeIf {
            it.provider == configuration.provider &&
                it.clientMode == configuration.clientMode &&
                it.clientIdFingerprint == configuration.clientIdFingerprint &&
                it.tokens.grants(scopes) &&
                authenticatedAccountId(it.provider, it.clientMode, identity.providerAccountId) == account
        }
    }
}
