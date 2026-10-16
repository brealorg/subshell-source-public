package app.subshell.prototype.auth

import app.subshell.prototype.data.account.NsfwPreferencesResult
import app.subshell.prototype.data.account.QuarantineOptInResult
import app.subshell.prototype.data.account.RedditCommunityAccessService
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryTarget
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort

internal const val RANDOM_NSFW_CANDIDATES_TTL_MS = 10L * 60L * 1000L

/**
 * #216: the #211 community access block moved out of AuthConnectViewModel —
 * the account's NSFW preference read, the random NSFW community pick and the
 * quarantine opt-in. Each answer is bound to [activeAccountId].
 */
internal class CommunityAccessCoordinator(
    private val accessService: RedditCommunityAccessService?,
    private val discoveryLoader: AuthenticatedRedditCommunityDiscoveryLoader?,
    private val activeAccountId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    // ISSUE211_ACCOUNT_NSFW_PREFERENCE_V1 (#211): the account's own over_18,
    // read for the tray notice; Failed while it is not the active account.
    suspend fun readNsfwAccountPreferences(account: String): NsfwPreferencesResult {
        val reader = accessService
            ?: return NsfwPreferencesResult.Failed
        val result = reader.read(account) { activeAccountId() == account }
        authDebugLog(
            "subshell-nsfw",
            "account-prefs account=${account.takeLast(6)} result=$result",
        )
        return result
    }

    // ISSUE211_RANDOM_NSFW_COMMUNITY_V1: a random NSFW community for the
    // active account, or null when Reddit offers none. The candidates are
    // kept per account for RANDOM_NSFW_CANDIDATES_TTL_MS, so a second tap
    // asks Reddit nothing (the owner found each pick slow). A pick is not
    // checked against its about page any more: candidates come from Reddit's
    // own listings, and a community that cannot be shown says why itself.
    private var lastRandomNsfwCommunity: String? = null
    private var randomNsfwCandidates: Triple<String, Long, List<String>>? = null

    suspend fun randomNsfwCommunity(account: String): String? {
        val now = now()
        val cached = randomNsfwCandidates
            ?.takeIf { (cachedAccount, at, names) ->
                cachedAccount == account && now - at < RANDOM_NSFW_CANDIDATES_TTL_MS && names.isNotEmpty()
            }
            ?.third
        val names = cached ?: run {
            val discovery = discoveryLoader ?: return null
            fun items(result: AuthenticatedRedditCommunityDiscoveryResult) =
                (result as? AuthenticatedRedditCommunityDiscoveryResult.Loaded)
                    ?.items.orEmpty()
            val popular = items(
                discovery.synchronize(
                    account,
                    AuthenticatedRedditCommunityDiscoveryTarget.popular(true),
                ),
            ).filter { it.nsfw }
            val candidates = popular.ifEmpty {
                items(
                    discovery.synchronize(
                        account,
                        AuthenticatedRedditCommunityDiscoveryTarget.search(
                            rawQuery = "nsfw",
                            sort = CommunityDiscoverySort.RELEVANCE,
                            includeOver18 = true,
                        ),
                    ),
                ).filter { it.nsfw }
            }
            candidates.map { it.community.name }.also { fetched ->
                randomNsfwCandidates = Triple(account, now, fetched)
            }
        }
        val picked = pickRandomNsfwCommunity(
            candidates = names.map { name ->
                CommunityDiscoveryItem(
                    community = Community(
                        id = name, name = name, description = "", members = "", accent = 0L,
                    ),
                    nsfw = true,
                )
            },
            previous = lastRandomNsfwCommunity,
            random = kotlin.random.Random.Default,
            isAvailable = { true },
        )
        if (picked != null) lastRandomNsfwCommunity = picked
        authDebugLog("subshell-nsfw", "random-nsfw cached=${cached != null} candidates=${names.size} picked=$picked")
        return picked
    }

    // ISSUE211_QUARANTINE_OPT_IN_V1: "View anyway" on a quarantined community,
    // sent once on the reader's tap; the caller reloads the feed (read-back).
    suspend fun optInToQuarantinedCommunity(
        account: String,
        communityName: String,
    ): QuarantineOptInResult {
        val service = accessService
            ?: return QuarantineOptInResult.Refused(null)
        val result = service.optIn(account, communityName) {
            activeAccountId() == account
        }
        authDebugLog("subshell-nsfw", "quarantine-optin community=$communityName result=$result")
        return result
    }
}
