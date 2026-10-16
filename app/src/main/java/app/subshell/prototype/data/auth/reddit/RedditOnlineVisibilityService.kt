package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.OnlineVisibilityConfirmation
import app.subshell.prototype.data.account.OnlineVisibilityIssue
import app.subshell.prototype.data.account.OnlineVisibilityResult
import app.subshell.prototype.data.account.OnlineVisibilityWriteResult
import app.subshell.prototype.data.account.RedditOnlineVisibilityService
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.reddit.OnlineVisibilityNetwork
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex

internal val ONLINE_VISIBILITY_READ_SCOPES = setOf("identity")
internal val ONLINE_VISIBILITY_WRITE_SCOPES = setOf("identity", "account")

/** Existing account reconnect handles auth. No pending preference write is stored/replayed. */
internal class ConfiguredRedditOnlineVisibilityService(
    private val configuration: OAuthProviderConfiguration,
    private val activeAccountId: () -> String?,
    private val ensureSession: suspend (String, Set<String>) -> AuthSessionResult,
    // AUTH16_MISSING_SCOPE_IS_NOT_REAUTHENTICATION_V1 (#139): whether the
    // account's stored session grants the scopes, read without side effects.
    private val storedSessionGrants: (String, Set<String>) -> Boolean = { _, _ -> true },
    private val networkForSession: (StoredOAuthSession) -> OnlineVisibilityNetwork,
) : RedditOnlineVisibilityService {
    private val mutationMutex = Mutex()

    private fun current(account: String, isCurrent: () -> Boolean): Boolean =
        account.isNotBlank() && activeAccountId() == account && isCurrent()

    private var lastSessionFailureTransient = false

    private fun sessionIssue(account: String, isCurrent: () -> Boolean): OnlineVisibilityIssue = when {
        !current(account, isCurrent) -> OnlineVisibilityIssue.ACCOUNT_CHANGED
        lastSessionFailureTransient -> OnlineVisibilityIssue.TRANSPORT
        else -> OnlineVisibilityIssue.AUTHORIZATION
    }

    private suspend fun session(
        account: String,
        scopes: Set<String>,
        isCurrent: () -> Boolean,
    ): StoredOAuthSession? {
        lastSessionFailureTransient = false
        if (!current(account, isCurrent)) return null
        // A token without `account` (connected before 2026-08-25) made
        // ensureSession report ReauthenticationRequired and flag the whole
        // account "Sign in again". A missing scope only fails this action.
        if (!storedSessionGrants(account, scopes)) return null
        val result = ensureSession(account, scopes)
        if (!current(account, isCurrent)) return null
        // CORRECT02_TRANSIENT_SESSION_FAILURE_IS_NOT_SIGN_IN_V1 (OCB-512): a
        // token refresh that failed on the network or a store read that failed
        // is a retryable connection problem, not "Sign in again".
        lastSessionFailureTransient =
            result is AuthSessionResult.StorageFailure ||
            (result is AuthSessionResult.EndpointFailure && result.result.isRetryableEndpointFailure())
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

    override suspend fun read(account: String, isCurrent: () -> Boolean): OnlineVisibilityResult {
        return try {
            val session = session(account, ONLINE_VISIBILITY_READ_SCOPES, isCurrent)
                ?: return OnlineVisibilityResult.Failed(sessionIssue(account, isCurrent))
            if (!current(account, isCurrent)) return changedAccountRead()
            val result = networkForSession(session).read()
            if (current(account, isCurrent)) result else changedAccountRead()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            OnlineVisibilityResult.Failed(OnlineVisibilityIssue.TRANSPORT)
        }
    }

    override suspend fun change(
        confirmation: OnlineVisibilityConfirmation,
        isCurrent: () -> Boolean,
    ): OnlineVisibilityWriteResult {
        // No queued requests, relative toggle, retry or callback-resumed mutation.
        if (!mutationMutex.tryLock()) return failed(OnlineVisibilityIssue.OUTCOME_UNKNOWN)
        var dispatched = false
        try {
            val account = confirmation.accountStableId
            if (confirmation.expectedValue == confirmation.desiredValue) {
                return failed(OnlineVisibilityIssue.PROTOCOL)
            }
            val session = session(account, ONLINE_VISIBILITY_WRITE_SCOPES, isCurrent)
                ?: return failed(sessionIssue(account, isCurrent))
            val network = networkForSession(session)
            if (!current(account, isCurrent)) return failed(OnlineVisibilityIssue.ACCOUNT_CHANGED)
            val before = network.read()
            if (!current(account, isCurrent)) return failed(OnlineVisibilityIssue.ACCOUNT_CHANGED)
            when (before) {
                OnlineVisibilityResult.Unavailable -> return OnlineVisibilityWriteResult.Unavailable
                is OnlineVisibilityResult.Failed -> return failed(before.issue, before.httpStatusCode)
                is OnlineVisibilityResult.Known -> if (before.value != confirmation.expectedValue) {
                    return OnlineVisibilityWriteResult.Changed(before.value)
                }
            }
            if (!current(account, isCurrent)) return failed(OnlineVisibilityIssue.ACCOUNT_CHANGED)
            // CORRECT04_SWITCH_AFTER_PATCH_IS_UNKNOWN_V1 (OCB-513): once the PATCH
            // is sent, an account switch leaves its outcome unknown, not "changed".
            dispatched = true
            val response = try {
                network.patch(confirmation.desiredValue)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                OnlineVisibilityResult.Failed(OnlineVisibilityIssue.TRANSPORT)
            }
            if (!current(account, isCurrent)) return failed(OnlineVisibilityIssue.OUTCOME_UNKNOWN)
            val readback = try {
                network.read()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                OnlineVisibilityResult.Failed(OnlineVisibilityIssue.TRANSPORT)
            }
            if (!current(account, isCurrent)) return failed(OnlineVisibilityIssue.OUTCOME_UNKNOWN)
            if (readback is OnlineVisibilityResult.Known &&
                readback.value == confirmation.desiredValue
            ) {
                return OnlineVisibilityWriteResult.Verified(
                    value = readback.value,
                    acknowledged = response is OnlineVisibilityResult.Known &&
                        response.value == confirmation.desiredValue,
                )
            }
            // Never claim rollback or successful saving from HTTP status alone.
            return OnlineVisibilityWriteResult.Failed(
                issue = OnlineVisibilityIssue.OUTCOME_UNKNOWN,
                observedValue = (readback as? OnlineVisibilityResult.Known)?.value,
                httpStatusCode = (response as? OnlineVisibilityResult.Failed)?.httpStatusCode,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return failed(
                if (dispatched) OnlineVisibilityIssue.OUTCOME_UNKNOWN
                else OnlineVisibilityIssue.TRANSPORT,
            )
        } finally {
            mutationMutex.unlock()
        }
    }

    private fun changedAccountRead() =
        OnlineVisibilityResult.Failed(OnlineVisibilityIssue.ACCOUNT_CHANGED)
    private fun failed(issue: OnlineVisibilityIssue, code: Int? = null) =
        OnlineVisibilityWriteResult.Failed(issue, httpStatusCode = code)
}

private fun OAuthEndpointResult<*>.isRetryableEndpointFailure(): Boolean =
    when (this) {
        is OAuthEndpointResult.HttpFailure -> retryable
        is OAuthEndpointResult.TransportFailure -> retryable
        is OAuthEndpointResult.ProtocolFailure -> false
        is OAuthEndpointResult.Success -> false
    }
