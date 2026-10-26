package app.subshell.prototype.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.subshell.prototype.backup.PortableCommunityMute
import app.subshell.prototype.backup.normalizePortableCommunityMutesByAccount
import app.subshell.prototype.backup.normalizePortableOpaqueStableIdValuesByAccount
import app.subshell.prototype.backup.normalizePortableStableIdValuesByAccount
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.model.Community
import app.subshell.prototype.ui.CommunityMute
import app.subshell.prototype.ui.MutedCommunityDirectViewMode
import app.subshell.prototype.ui.RECENT_COMMUNITY_HISTORY_LIMIT
import app.subshell.prototype.ui.RecentCommunityVisit
import app.subshell.prototype.ui.activeCommunityMutesAt
import app.subshell.prototype.ui.activeMutedCommunityNamesAt
import app.subshell.prototype.ui.communityMutesForManagement
import app.subshell.prototype.ui.decodeCommunityMutes
import app.subshell.prototype.ui.decodeMutedCommunityDirectViewMode
import app.subshell.prototype.ui.encodeCommunityMutes
import app.subshell.prototype.ui.normalizeFavoriteCommunityNames
import app.subshell.prototype.ui.normalizeFavoriteCustomFeedPath
import app.subshell.prototype.ui.normalizeFavoriteCustomFeedPaths
import app.subshell.prototype.ui.normalizeJoinedCommunityIds
import app.subshell.prototype.ui.normalizeRecentCommunityVisits
import app.subshell.prototype.ui.shouldHideMutedCommunityPosts

/**
 * Per-account community lists: joined communities, favourite communities and
 * custom feeds, hidden communities, timed community mutes (with their clock
 * and the direct-view mode) and recent community visits.
 * Extracted verbatim from SubshellState, which holds one instance and
 * forwards its public API to it. The account-switch, session-sync and
 * backup capture/restore paths stay in SubshellState and still reload or
 * restore these lists in place through the forwarded fields; the
 * load/persist lambdas are the same instances SubshellState was built with.
 * Community sort preferences stay in SubshellState. Behaviour is unchanged.
 */
internal class CommunityPreferencesState(
    private val repository: SubshellRepository,
    private val communityFor: (String) -> Community?,
    private val communityIdentityFor: (String) -> Community,
    private val loadJoinedCommunityIds: (String) -> Set<String>,
    private val persistJoinedCommunityIds: (String, Set<String>) -> Unit,
    private val loadFavoriteCommunityNames: (String) -> Set<String>,
    private val persistFavoriteCommunityNames: (String, Set<String>) -> Unit,
    private val loadFavoriteCustomFeedPaths: (String) -> Set<String>,
    private val persistFavoriteCustomFeedPaths: (String, Set<String>) -> Unit,
    private val loadHiddenCommunityNames: (String) -> Set<String>,
    private val persistHiddenCommunityNames: (String, Set<String>) -> Unit,
    private val loadCommunityMuteEntries: (String) -> Set<String>,
    private val persistCommunityMuteEntries: (String, Set<String>) -> Unit,
    loadMutedCommunityDirectViewMode: () -> String?,
    private val persistMutedCommunityDirectViewMode: (String) -> Unit,
    loadRecentCommunityVisits: (String) -> List<RecentCommunityVisit>,
    private val persistRecentCommunityVisits: (String, List<RecentCommunityVisit>) -> Unit,
    private val currentTimeMillis: () -> Long,
    private val activeAccountIdProvider: () -> String,
    private val activeAccountUsernameProvider: () -> String,
    private val localMutationsAllowedProvider: () -> Boolean,
    private val accountSessionCatalogProvider: () -> List<AccountSessionSummary>,
) {
    private val activeAccountId: String
        get() = activeAccountIdProvider()
    private val activeAccountUsername: String
        get() = activeAccountUsernameProvider()
    private val localMutationsAllowed: Boolean
        get() = localMutationsAllowedProvider()
    private val accountSessionCatalog: List<AccountSessionSummary>
        get() = accountSessionCatalogProvider()

    // ROOMWAIT01_ONE_COMMUNITY_READ_PER_LOAD_V1 (#243): loadJoinedCommunityIds
    // returns canonical ids already (normalized, or the cached joined ones);
    // normalizing again read the whole community table a second time on Main.
    val joinedCommunityIds = mutableStateListOf<String>().apply {
        addAll(loadJoinedCommunityIds(activeAccountUsername))
    }
    val favoriteCommunityNames = mutableStateListOf<String>().apply {
        addAll(
            normalizeFavoriteCommunityNames(
                loadFavoriteCommunityNames(activeAccountId),
            ),
        )
    }
    val favoriteCustomFeedPaths = mutableStateListOf<String>().apply {
        addAll(
            normalizeFavoriteCustomFeedPaths(
                loadFavoriteCustomFeedPaths(activeAccountId),
            ),
        )
    }
    val hiddenCommunityNames = mutableStateListOf<String>().apply {
        addAll(
            normalizeFavoriteCommunityNames(
                loadHiddenCommunityNames(activeAccountId),
            ),
        )
    }
    var communityMuteClockEpochMillis by mutableLongStateOf(currentTimeMillis())
    val communityMutes = mutableStateListOf<CommunityMute>().apply {
        addAll(
            activeCommunityMutesAt(
                decodeCommunityMutes(loadCommunityMuteEntries(activeAccountId)),
                communityMuteClockEpochMillis,
            ),
        )
    }
    var mutedCommunityDirectViewMode by mutableStateOf(
        decodeMutedCommunityDirectViewMode(loadMutedCommunityDirectViewMode()),
    )

    var requestedCommunityMuteEditorName by mutableStateOf<String?>(null)

    val recentCommunityVisits = mutableStateListOf<RecentCommunityVisit>().apply {
        addAll(
            normalizeRecentCommunityVisits(
                loadRecentCommunityVisits(activeAccountId),
            ),
        )
    }

    // REDDITFAV01_SHOW_REDDIT_FAVORITES_V1: Reddit's favorites for the
    // account whose subscriptions were read, pushed in from the runtime.
    // They count only while sync is on and that account is the active one.
    var redditFavoriteSync by mutableStateOf(RedditFavoriteSyncSnapshot())

    // REDDITFAV02_FAVORITE_WRITE_V1: sends (account, community, favorite) to
    // Reddit; set from the runtime, null where no runtime is wired.
    var redditFavoriteWriter: ((String, String, Boolean) -> Unit)? = null

    // REDDITFAV04_CUSTOM_FEED_FAVORITES_V1: (account, feed path, favorite).
    var redditCustomFeedFavoriteWriter: ((String, String, Boolean) -> Unit)? = null

    private val redditFavoriteSyncActive: Boolean
        get() = redditFavoriteSync.enabled &&
            redditFavoriteSync.accountStableId != null &&
            redditFavoriteSync.accountStableId == activeAccountId

    /** Reddit's favorites as shown: the read list with sent changes applied. */
    private val shownRedditFavoriteNames: List<String>
        get() {
            if (!redditFavoriteSyncActive) return emptyList()
            val sync = redditFavoriteSync
            val names = sync.favoriteCommunityNames.filter { name ->
                sync.pending[name.lowercase()] != false
            }
            val added = sync.pending
                .filterValues { makeFavorite -> makeFavorite }
                .keys
                .filter { key -> names.none { name -> name.equals(key, ignoreCase = true) } }
                .map { key ->
                    sync.joinedCommunityNames.firstOrNull { name -> name.equals(key, ignoreCase = true) }
                        ?: key
                }
            return names + added
        }

    /** Local favorites plus, with sync on, Reddit's for the active account. */
    val effectiveFavoriteCommunityNames: List<String>
        get() {
            val local = favoriteCommunityNames.toList()
            val reddit = shownRedditFavoriteNames.filter { name ->
                local.none { favorite -> favorite.equals(name, ignoreCase = true) }
            }
            return (local + reddit).sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

    private fun isLocalFavorite(normalizedName: String): Boolean =
        favoriteCommunityNames.any { favorite ->
            favorite.equals(normalizedName, ignoreCase = true)
        }

    private fun isShownRedditFavorite(normalizedName: String): Boolean =
        shownRedditFavoriteNames.any { favorite ->
            favorite.equals(normalizedName, ignoreCase = true)
        }

    fun isCommunityFavorite(name: String): Boolean {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrNull() ?: return false
        return isLocalFavorite(normalizedName) || isShownRedditFavorite(normalizedName)
    }

    fun favoriteCommunities(
        candidates: Iterable<Community> = emptyList(),
    ): List<Community> {
        val candidatesByName = candidates
            .associateBy { community ->
                runCatching { normalizeRedditCommunityName(community.name) }
                    .getOrDefault(community.name)
                    .lowercase()
            }
        return effectiveFavoriteCommunityNames
            .map { favoriteName ->
                candidatesByName[favoriteName.lowercase()]
                    ?: communityFor(favoriteName)
                    ?: communityIdentityFor(favoriteName)
            }
            .distinctBy { community ->
                runCatching { normalizeRedditCommunityName(community.name) }
                    .getOrDefault(community.name)
                    .lowercase()
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { community -> community.name })
    }

    fun toggleFavoriteCommunity(name: String) {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrNull() ?: return
        val writer = redditFavoriteWriter
        val sync = redditFavoriteSync
        val action = favoriteToggleAction(
            localFavorite = isLocalFavorite(normalizedName),
            redditFavorite = isShownRedditFavorite(normalizedName),
            syncActive = redditFavoriteSyncActive && writer != null,
            joined = sync.joinedCommunityNames.any { joined ->
                joined.equals(normalizedName, ignoreCase = true)
            },
        )
        when (action) {
            FavoriteToggleAction.ADD_LOCAL,
            FavoriteToggleAction.REMOVE_LOCAL,
            -> toggleLocalFavoriteCommunity(normalizedName)
            FavoriteToggleAction.ADD_ON_REDDIT ->
                writer?.invoke(activeAccountId, normalizedName, true)
            FavoriteToggleAction.REMOVE_ON_REDDIT -> {
                if (isLocalFavorite(normalizedName)) {
                    toggleLocalFavoriteCommunity(normalizedName)
                }
                writer?.invoke(activeAccountId, normalizedName, false)
            }
        }
    }

    private fun toggleLocalFavoriteCommunity(normalizedName: String) {
        val existingIndex = favoriteCommunityNames.indexOfFirst { favorite ->
            favorite.equals(normalizedName, ignoreCase = true)
        }
        if (existingIndex >= 0) {
            favoriteCommunityNames.removeAt(existingIndex)
        } else {
            favoriteCommunityNames.add(normalizedName)
        }
        favoriteCommunityNames.sortWith(String.CASE_INSENSITIVE_ORDER)
        persistFavoriteCommunityNames(
            activeAccountId,
            favoriteCommunityNames.toSet(),
        )
    }

    /** Reddit's custom feed favorites as shown, sent changes applied. */
    private val shownRedditFavoriteCustomFeedPaths: List<String>
        get() {
            if (!redditFavoriteSyncActive) return emptyList()
            val sync = redditFavoriteSync
            return sync.customFeedPaths.filter { path ->
                sync.pendingCustomFeeds[path.lowercase()]
                    ?: sync.favoriteCustomFeedPaths.any { favorite -> favorite.equals(path, ignoreCase = true) }
            }
        }

    private fun isLocalFavoriteCustomFeed(normalizedPath: String): Boolean =
        favoriteCustomFeedPaths.any { favorite ->
            favorite.equals(normalizedPath, ignoreCase = true)
        }

    private fun isShownRedditFavoriteCustomFeed(normalizedPath: String): Boolean =
        shownRedditFavoriteCustomFeedPaths.any { favorite ->
            normalizeFavoriteCustomFeedPath(favorite).equals(normalizedPath, ignoreCase = true)
        }

    fun isCustomFeedFavorite(path: String): Boolean {
        val normalizedPath = normalizeFavoriteCustomFeedPath(path) ?: return false
        return isLocalFavoriteCustomFeed(normalizedPath) || isShownRedditFavoriteCustomFeed(normalizedPath)
    }

    fun favoriteCustomFeeds(
        candidates: Iterable<CustomFeedSummary>,
    ): List<CustomFeedSummary> {
        val candidatesByPath = candidates.mapNotNull { feed ->
            normalizeFavoriteCustomFeedPath(feed.path)?.let { normalizedPath ->
                normalizedPath.lowercase() to feed
            }
        }.toMap()
        val redditPaths = shownRedditFavoriteCustomFeedPaths
            .mapNotNull(::normalizeFavoriteCustomFeedPath)
        return (favoriteCustomFeedPaths + redditPaths)
            .mapNotNull { favoritePath ->
                candidatesByPath[favoritePath.lowercase()]
            }
            .distinctBy { feed ->
                normalizeFavoriteCustomFeedPath(feed.path)?.lowercase()
                    ?: feed.path.lowercase()
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { feed ->
                feed.displayName
            })
    }

    fun toggleFavoriteCustomFeed(path: String) {
        val normalizedPath = normalizeFavoriteCustomFeedPath(path) ?: return
        val writer = redditCustomFeedFavoriteWriter
        // The path as Reddit spells it, for the write.
        val redditPath = redditFavoriteSync.customFeedPaths.firstOrNull { ownPath ->
            normalizeFavoriteCustomFeedPath(ownPath).equals(normalizedPath, ignoreCase = true)
        }
        val action = favoriteToggleAction(
            localFavorite = isLocalFavoriteCustomFeed(normalizedPath),
            redditFavorite = isShownRedditFavoriteCustomFeed(normalizedPath),
            syncActive = redditFavoriteSyncActive && writer != null,
            joined = redditPath != null,
        )
        when (action) {
            FavoriteToggleAction.ADD_LOCAL,
            FavoriteToggleAction.REMOVE_LOCAL,
            -> toggleLocalFavoriteCustomFeed(normalizedPath)
            FavoriteToggleAction.ADD_ON_REDDIT ->
                redditPath?.let { writer?.invoke(activeAccountId, it, true) }
            FavoriteToggleAction.REMOVE_ON_REDDIT -> {
                if (isLocalFavoriteCustomFeed(normalizedPath)) {
                    toggleLocalFavoriteCustomFeed(normalizedPath)
                }
                writer?.invoke(activeAccountId, redditPath ?: path, false)
            }
        }
    }

    private fun toggleLocalFavoriteCustomFeed(normalizedPath: String) {
        val existingIndex = favoriteCustomFeedPaths.indexOfFirst { favorite ->
            favorite.equals(normalizedPath, ignoreCase = true)
        }
        if (existingIndex >= 0) {
            favoriteCustomFeedPaths.removeAt(existingIndex)
        } else {
            favoriteCustomFeedPaths.add(normalizedPath)
        }
        favoriteCustomFeedPaths.sortWith(String.CASE_INSENSITIVE_ORDER)
        persistFavoriteCustomFeedPaths(
            activeAccountId,
            favoriteCustomFeedPaths.toSet(),
        )
    }

    fun isCommunityHidden(name: String): Boolean {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrNull() ?: return false
        return hiddenCommunityNames.any { hiddenName ->
            hiddenName.equals(normalizedName, ignoreCase = true)
        }
    }

    fun toggleHiddenCommunity(name: String) {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrNull() ?: return
        val existingIndex = hiddenCommunityNames.indexOfFirst { hiddenName ->
            hiddenName.equals(normalizedName, ignoreCase = true)
        }
        if (existingIndex >= 0) {
            hiddenCommunityNames.removeAt(existingIndex)
        } else {
            hiddenCommunityNames.add(normalizedName)
        }
        hiddenCommunityNames.sortWith(String.CASE_INSENSITIVE_ORDER)
        persistHiddenCommunityNames(
            activeAccountId,
            hiddenCommunityNames.toSet(),
        )
    }

    fun updateMutedCommunityDirectViewMode(
        mode: MutedCommunityDirectViewMode,
    ) {
        if (mutedCommunityDirectViewMode == mode) return
        mutedCommunityDirectViewMode = mode
        persistMutedCommunityDirectViewMode(mode.name)
    }

    fun requestCommunityMuteEditor(name: String) {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrNull() ?: return
        requestedCommunityMuteEditorName = normalizedName
    }

    fun clearRequestedCommunityMuteEditor() {
        requestedCommunityMuteEditorName = null
    }

    fun shouldHidePostsForMutedCommunity(name: String): Boolean =
        shouldHideMutedCommunityPosts(
            isMuted = isCommunityMuted(name),
            mode = mutedCommunityDirectViewMode,
        )

    fun activeMutedCommunityNames(
        nowEpochMillis: Long = communityMuteClockEpochMillis,
    ): Set<String> = activeMutedCommunityNamesAt(
        mutes = communityMutes,
        nowEpochMillis = nowEpochMillis,
    )

    fun activeCommunityMuteEntries(
        nowEpochMillis: Long = communityMuteClockEpochMillis,
    ): List<CommunityMute> = communityMutesForManagement(
        mutes = communityMutes,
        nowEpochMillis = nowEpochMillis,
    )

    fun communityMuteFor(
        name: String,
        nowEpochMillis: Long = currentTimeMillis(),
    ): CommunityMute? = app.subshell.prototype.ui.communityMuteFor(
        mutes = communityMutes,
        communityName = name,
        nowEpochMillis = nowEpochMillis,
    )

    fun isCommunityMuted(
        name: String,
        nowEpochMillis: Long = currentTimeMillis(),
    ): Boolean = communityMuteFor(name, nowEpochMillis) != null

    val nextCommunityMuteExpiryEpochMillis: Long?
        get() = communityMutes
            .asSequence()
            .mapNotNull(CommunityMute::expiresAtEpochMillis)
            .filter { expiry -> expiry > communityMuteClockEpochMillis }
            .minOrNull()

    fun millisUntilNextCommunityMuteExpiry(): Long? =
        nextCommunityMuteExpiryEpochMillis?.let { expiry ->
            (expiry - currentTimeMillis()).coerceAtLeast(0L)
        }

    fun refreshCommunityMuteClock() {
        val now = currentTimeMillis()
        communityMuteClockEpochMillis = now
        pruneExpiredCommunityMutes(now)
    }

    fun muteCommunity(
        name: String,
        expiresAtEpochMillis: Long?,
    ): Boolean {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrNull() ?: return false
        val now = currentTimeMillis()
        if (expiresAtEpochMillis != null && expiresAtEpochMillis <= now) return false

        communityMuteClockEpochMillis = now
        communityMutes.removeAll { mute ->
            mute.communityName.equals(normalizedName, ignoreCase = true)
        }
        communityMutes.add(
            CommunityMute(
                communityName = normalizedName,
                expiresAtEpochMillis = expiresAtEpochMillis,
            ),
        )
        communityMutes.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER, CommunityMute::communityName))
        persistCurrentCommunityMutes()
        return true
    }

    fun unmuteCommunity(name: String): Boolean {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrNull() ?: return false
        val removed = communityMutes.removeAll { mute ->
            mute.communityName.equals(normalizedName, ignoreCase = true)
        }
        if (removed) persistCurrentCommunityMutes()
        return removed
    }

    private fun pruneExpiredCommunityMutes(nowEpochMillis: Long) {
        val active = activeCommunityMutesAt(communityMutes, nowEpochMillis)
        if (active == communityMutes.toList()) return
        communityMutes.clear()
        communityMutes.addAll(active)
        persistCurrentCommunityMutes()
    }

    private fun persistCurrentCommunityMutes() {
        persistCommunityMuteEntries(
            activeAccountId,
            encodeCommunityMutes(communityMutes),
        )
    }

    fun recentCommunities(
        candidates: Iterable<Community> = emptyList(),
        limit: Int = RECENT_COMMUNITY_HISTORY_LIMIT,
    ): List<Community> {
        val candidatesByName = candidates
            .associateBy { community ->
                runCatching { normalizeRedditCommunityName(community.name) }
                    .getOrDefault(community.name)
                    .lowercase()
            }
        return recentCommunityVisits
            .filterNot { visit -> isCommunityHidden(visit.communityName) }
            .asSequence()
            .map { visit ->
                candidatesByName[visit.communityName.lowercase()]
                    ?: communityFor(visit.communityName)
                    ?: communityIdentityFor(visit.communityName)
            }
            .distinctBy { community ->
                runCatching { normalizeRedditCommunityName(community.name) }
                    .getOrDefault(community.name)
                    .lowercase()
            }
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    fun clearRecentCommunities() {
        if (recentCommunityVisits.isEmpty()) return
        recentCommunityVisits.clear()
        persistRecentCommunityVisits(activeAccountId, emptyList())
    }

    fun recordRecentCommunityVisit(name: String) {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrNull() ?: return
        recentCommunityVisits.removeAll { visit ->
            visit.communityName.equals(normalizedName, ignoreCase = true)
        }
        recentCommunityVisits.add(
            0,
            RecentCommunityVisit(
                communityName = normalizedName,
                visitedAtEpochMillis = currentTimeMillis(),
            ),
        )
        val normalized = normalizeRecentCommunityVisits(recentCommunityVisits)
        recentCommunityVisits.clear()
        recentCommunityVisits.addAll(normalized)
        persistRecentCommunityVisits(activeAccountId, normalized)
    }

    fun toggleCommunity(communityId: String) {
        if (!localMutationsAllowed) return
        val canonicalCommunityId = normalizeJoinedCommunityIds(
            communityIds = listOf(communityId),
            repository = repository,
        ).singleOrNull()
            ?: return
        if (!joinedCommunityIds.remove(canonicalCommunityId)) {
            joinedCommunityIds.add(canonicalCommunityId)
        }
        persistJoinedCommunityIds(
            activeAccountUsername,
            joinedCommunityIds.toSet(),
        )
    }

    // BACKUP02A1B_ACCOUNT_SCOPED_JOINED_COMMUNITY_ROUNDTRIP_V1
    fun captureJoinedCommunityIdsByAccount(): Map<String, Set<String>> = buildMap {
        accountSessionCatalog
            .sortedBy { account -> account.username.trim().lowercase() }
            .forEach { account ->
                val username = account.username.trim().lowercase()
                    .takeIf(String::isNotEmpty)
                    ?: return@forEach
                val sourceIds = if (account.stableId == activeAccountId) {
                    joinedCommunityIds.toSet()
                } else {
                    loadJoinedCommunityIds(account.username)
                }
                put(
                    username,
                    normalizeJoinedCommunityIds(
                        communityIds = sourceIds,
                        repository = repository,
                    ).toSortedSet(),
                )
            }
    }

    fun captureFavoriteCommunityNamesByAccount(): Map<String, Set<String>> =
        normalizePortableStableIdValuesByAccount(
            buildMap {
                accountSessionCatalog
                    .sortedBy { account -> account.stableId.trim().lowercase() }
                    .forEach { account ->
                        val stableId = account.stableId.trim().lowercase()
                            .takeIf(String::isNotEmpty)
                            ?: return@forEach
                        val sourceNames = if (account.stableId == activeAccountId) {
                            favoriteCommunityNames.toSet()
                        } else {
                            loadFavoriteCommunityNames(account.stableId)
                        }
                        put(
                            stableId,
                            normalizeFavoriteCommunityNames(sourceNames).toSet(),
                        )
                    }
            },
        )

    fun captureFavoriteCustomFeedPathsByAccount(): Map<String, Set<String>> =
        normalizePortableStableIdValuesByAccount(
            buildMap {
                accountSessionCatalog
                    .sortedBy { account -> account.stableId.trim().lowercase() }
                    .forEach { account ->
                        val stableId = account.stableId.trim().lowercase()
                            .takeIf(String::isNotEmpty)
                            ?: return@forEach
                        val sourcePaths = if (account.stableId == activeAccountId) {
                            favoriteCustomFeedPaths.toSet()
                        } else {
                            loadFavoriteCustomFeedPaths(account.stableId)
                        }
                        put(
                            stableId,
                            normalizeFavoriteCustomFeedPaths(sourcePaths).toSet(),
                        )
                    }
            },
        )

    fun captureHiddenCommunityNamesByAccount(): Map<String, Set<String>> =
        normalizePortableOpaqueStableIdValuesByAccount(
            buildMap {
                accountSessionCatalog
                    .sortedBy { account -> account.stableId.trim() }
                    .forEach { account ->
                        val stableId = account.stableId.trim()
                            .takeIf(String::isNotEmpty)
                            ?: return@forEach
                        val sourceNames = if (account.stableId == activeAccountId) {
                            hiddenCommunityNames.toSet()
                        } else {
                            loadHiddenCommunityNames(account.stableId)
                        }
                        put(
                            stableId,
                            normalizeFavoriteCommunityNames(sourceNames).toSet(),
                        )
                    }
            },
        )

    fun captureCommunityMutesByAccount(): Map<String, List<PortableCommunityMute>> {
        val nowEpochMillis = currentTimeMillis()
        return normalizePortableCommunityMutesByAccount(
            value = buildMap {
                accountSessionCatalog
                    .sortedBy { account -> account.stableId.trim() }
                    .forEach { account ->
                        val stableId = account.stableId.trim()
                            .takeIf(String::isNotEmpty)
                            ?: return@forEach
                        val sourceMutes = if (account.stableId == activeAccountId) {
                            communityMutes.toList()
                        } else {
                            decodeCommunityMutes(loadCommunityMuteEntries(account.stableId))
                        }
                        put(
                            stableId,
                            activeCommunityMutesAt(sourceMutes, nowEpochMillis).map { mute ->
                                PortableCommunityMute(
                                    communityName = mute.communityName,
                                    expiresAtEpochMillis = mute.expiresAtEpochMillis,
                                )
                            },
                        )
                    }
            },
            nowEpochMillis = nowEpochMillis,
        )
    }

    fun normalizeJoinedCommunityRestoreSnapshot(
        snapshot: Map<String, Set<String>>?,
    ): Map<String, Set<String>>? {
        val rawByAccount = snapshot ?: return null
        val restoredByAccount = linkedMapOf<String, Set<String>>()

        rawByAccount.entries
            .sortedBy { (rawAccount, _) -> rawAccount.trim().lowercase() }
            .forEach { (rawAccount, rawIds) ->
                val username = rawAccount.trim().lowercase()
                    .takeIf(String::isNotEmpty)
                    ?: return@forEach
                val normalizedIds = normalizeJoinedCommunityIds(
                    communityIds = rawIds,
                    repository = repository,
                ).toSet()
                restoredByAccount[username] =
                    (restoredByAccount[username].orEmpty() + normalizedIds).toSortedSet()
            }

        return restoredByAccount
    }

    fun applyRestoredJoinedCommunityIdsToLiveState(
        restoredByAccount: Map<String, Set<String>>?,
    ) {
        val activeRestored = restoredByAccount?.get(
            activeAccountUsername.trim().lowercase(),
        ) ?: return
        joinedCommunityIds.clear()
        joinedCommunityIds.addAll(activeRestored)
    }

    fun restoreJoinedCommunityIdsByAccount(
        snapshot: Map<String, Set<String>>?,
    ) {
        val restoredByAccount =
            normalizeJoinedCommunityRestoreSnapshot(snapshot) ?: return
        restoredByAccount.forEach { (username, communityIds) ->
            persistJoinedCommunityIds(username, communityIds)
        }
        applyRestoredJoinedCommunityIdsToLiveState(restoredByAccount)
    }

    fun normalizeFavoriteCommunityRestoreSnapshot(
        snapshot: Map<String, Set<String>>?,
    ): Map<String, Set<String>>? = snapshot
        ?.let(::normalizePortableStableIdValuesByAccount)
        ?.mapValues { (_, rawNames) ->
            normalizeFavoriteCommunityNames(rawNames).toSet()
        }

    fun normalizeFavoriteCustomFeedRestoreSnapshot(
        snapshot: Map<String, Set<String>>?,
    ): Map<String, Set<String>>? = snapshot
        ?.let(::normalizePortableStableIdValuesByAccount)
        ?.mapValues { (_, rawPaths) ->
            normalizeFavoriteCustomFeedPaths(rawPaths).toSet()
        }

    fun applyRestoredFavoriteCommunityNamesToLiveState(
        restoredByAccount: Map<String, Set<String>>?,
    ) {
        val restored = restoredByAccount ?: return
        val activeStableId = activeAccountId.trim().lowercase()
        val activeRestored = if (restored.isEmpty()) {
            emptySet()
        } else {
            restored[activeStableId] ?: return
        }
        favoriteCommunityNames.clear()
        favoriteCommunityNames.addAll(normalizeFavoriteCommunityNames(activeRestored))
    }

    fun applyRestoredFavoriteCustomFeedPathsToLiveState(
        restoredByAccount: Map<String, Set<String>>?,
    ) {
        val restored = restoredByAccount ?: return
        val activeStableId = activeAccountId.trim().lowercase()
        val activeRestored = if (restored.isEmpty()) {
            emptySet()
        } else {
            restored[activeStableId] ?: return
        }
        favoriteCustomFeedPaths.clear()
        favoriteCustomFeedPaths.addAll(normalizeFavoriteCustomFeedPaths(activeRestored))
    }

    fun normalizeHiddenCommunityRestoreSnapshot(
        snapshot: Map<String, Set<String>>?,
    ): Map<String, Set<String>>? = snapshot
        ?.let(::normalizePortableOpaqueStableIdValuesByAccount)
        ?.mapValues { (_, rawNames) ->
            normalizeFavoriteCommunityNames(rawNames).toSet()
        }

    fun applyRestoredHiddenCommunityNamesToLiveState(
        restoredByAccount: Map<String, Set<String>>?,
    ) {
        val restored = restoredByAccount ?: return
        val activeRestored = if (restored.isEmpty()) {
            emptySet()
        } else {
            restored[activeAccountId] ?: return
        }
        hiddenCommunityNames.clear()
        hiddenCommunityNames.addAll(normalizeFavoriteCommunityNames(activeRestored))
    }

    fun normalizeCommunityMuteRestoreSnapshot(
        snapshot: Map<String, List<PortableCommunityMute>>?,
    ): Map<String, List<PortableCommunityMute>>? {
        val raw = snapshot ?: return null
        return normalizePortableCommunityMutesByAccount(
            value = raw,
            nowEpochMillis = currentTimeMillis(),
        )
    }

    fun encodePortableCommunityMuteEntriesByAccount(
        restoredByAccount: Map<String, List<PortableCommunityMute>>?,
    ): Map<String, Set<String>>? = restoredByAccount?.mapValues { (_, portableMutes) ->
        encodeCommunityMutes(
            portableMutes.map { mute ->
                CommunityMute(
                    communityName = mute.communityName,
                    expiresAtEpochMillis = mute.expiresAtEpochMillis,
                )
            },
        )
    }

    fun applyRestoredCommunityMutesToLiveState(
        restoredByAccount: Map<String, List<PortableCommunityMute>>?,
    ) {
        val restored = restoredByAccount ?: return
        val activeRestored = if (restored.isEmpty()) {
            emptyList()
        } else {
            restored[activeAccountId] ?: return
        }
        val nowEpochMillis = currentTimeMillis()
        communityMuteClockEpochMillis = nowEpochMillis
        communityMutes.clear()
        communityMutes.addAll(
            activeCommunityMutesAt(
                activeRestored.map { mute ->
                    CommunityMute(
                        communityName = mute.communityName,
                        expiresAtEpochMillis = mute.expiresAtEpochMillis,
                    )
                },
                nowEpochMillis,
            ),
        )
    }
}

/** REDDITFAV01_SHOW_REDDIT_FAVORITES_V1: what the runtime knows for one account. */
internal data class RedditFavoriteSyncSnapshot(
    val enabled: Boolean = false,
    val accountStableId: String? = null,
    val favoriteCommunityNames: Set<String> = emptySet(),
    val joinedCommunityNames: Set<String> = emptySet(),
    // Lowercased community name to the state sent and not yet read back.
    val pending: Map<String, Boolean> = emptyMap(),
    // REDDITFAV04: the account's own custom feeds as Reddit spells their
    // paths, Reddit's favorites among them, and sent changes by lowercased path.
    val customFeedPaths: Set<String> = emptySet(),
    val favoriteCustomFeedPaths: Set<String> = emptySet(),
    val pendingCustomFeeds: Map<String, Boolean> = emptyMap(),
)

internal enum class FavoriteToggleAction {
    ADD_LOCAL,
    REMOVE_LOCAL,
    ADD_ON_REDDIT,
    REMOVE_ON_REDDIT,
}

/**
 * REDDITFAV02_STAR_RULE_V1: with sync on, the star removes a favorite from
 * wherever it is (Reddit's from Reddit, a local one locally) and adds it on
 * Reddit when Reddit can hold it (a joined community; a missing grant is
 * asked for, REDDITFAV03); anything else stays a local favorite.
 */
internal fun favoriteToggleAction(
    localFavorite: Boolean,
    redditFavorite: Boolean,
    syncActive: Boolean,
    joined: Boolean,
): FavoriteToggleAction = when {
    !syncActive -> if (localFavorite) FavoriteToggleAction.REMOVE_LOCAL else FavoriteToggleAction.ADD_LOCAL
    redditFavorite -> FavoriteToggleAction.REMOVE_ON_REDDIT
    localFavorite -> FavoriteToggleAction.REMOVE_LOCAL
    joined -> FavoriteToggleAction.ADD_ON_REDDIT
    else -> FavoriteToggleAction.ADD_LOCAL
}
