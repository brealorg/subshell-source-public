package app.subshell.prototype.ui.state

import androidx.compose.runtime.mutableStateMapOf
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.normalizeRedditCommunityName
import app.subshell.prototype.data.normalizeRedditUsername
import app.subshell.prototype.data.valueOrNull
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.ProfileActivityEvidence
import app.subshell.prototype.ui.isUnavailablePublicProfileHttpStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Community and user identity metadata: overrides refreshed from the identity
 * repository, lookup caches, in-flight guards, unavailable-profile marks and
 * public-profile activity evidence. Extracted verbatim from SubshellState,
 * which owns one instance and delegates to it; behaviour is unchanged.
 */
internal class IdentityMetadataState(
    private val repository: SubshellRepository,
    private val identityMetadataRepository: IdentityMetadataRepository?,
    private val isAuthenticated: () -> Boolean,
) {
    private val activeAccountIsAuthenticated: Boolean
        get() = isAuthenticated()

    private val userProfileActivityEvidence =
        mutableStateMapOf<String, Set<ProfileActivityEvidence>>()
    private val communityIdentityOverrides = mutableStateMapOf<String, Community>()
    private val userIdentityOverrides = mutableStateMapOf<String, UserFixture>()
    private val unavailableUserProfiles = mutableStateMapOf<String, Boolean>()
    private val communityIdentityCache = mutableMapOf<String, Community>()
    private val userIdentityCache = mutableMapOf<String, UserFixture>()
    private val loadedCommunityIdentities = mutableSetOf<String>()
    private val loadedUserIdentities = mutableSetOf<String>()
    private val loadingCommunityIdentities = mutableSetOf<String>()
    private val loadingUserIdentities = mutableSetOf<String>()
    private val cachedCommunityIdentityReads = mutableSetOf<String>()

    // ISSUE22_AUTHOR_AVATAR_BATCH_LOOKUP_V1
    // Comment author avatars by username key: a resolved entry is a value
    // (null = account known, no usable avatar); unresolved accounts are
    // absent. Bounded and process-local; avatars are public data, so the
    // cache is not account-scoped. In-flight keys are guarded so overlapping
    // threads do not repeat a lookup.
    private val authorAvatars = mutableStateMapOf<String, String?>()
    private val authorAvatarOrder = ArrayDeque<String>()
    private val loadingAuthorAvatars = mutableSetOf<String>()

    fun communityFor(name: String): Community? {
        val normalizedName = runCatching {
            normalizeRedditCommunityName(name)
        }.getOrNull() ?: return null
        val key = identityMetadataKey(normalizedName, prefix = "r/")
        communityIdentityOverrides[key]?.let { return it }
        communityIdentityCache[key]?.let { return it }
        val resolved = if (activeAccountIsAuthenticated) {
            Community(
                id = "live:$normalizedName",
                name = normalizedName,
                description = "Live Reddit community",
                members = "Reddit",
                accent = 0xFFFF8A3D,
            )
        } else {
            repository.community(normalizedName).valueOrNull()
        }
        return resolved?.also { community ->
            communityIdentityCache[key] = community
        }
    }

    fun communityIdentityFor(name: String): Community {
        val displayName = name.trim()
            .removePrefix("/r/")
            .removePrefix("r/")
            .ifBlank { "unknown" }
        val key = identityMetadataKey(displayName, prefix = "r/")
        communityIdentityOverrides[key]?.let { return it }
        communityIdentityCache[key]?.let { return it }
        return (
            if (activeAccountIsAuthenticated) {
                null
            } else {
                repository.community(key).valueOrNull()
            } ?: Community(
                id = "remote:$key",
                name = displayName,
                description = "Public community preview.",
                members = "—",
                accent = 0xFF6750A4L,
            )
        ).also { community ->
            communityIdentityCache[key] = community
        }
    }

    fun userFor(username: String): UserFixture {
        val key = identityMetadataKey(username, prefix = "u/")
        userIdentityOverrides[key]?.let { return it }
        userIdentityCache[key]?.let { return it }
        return (
            if (activeAccountIsAuthenticated) {
                null
            } else {
                repository.user(username).valueOrNull()
            } ?: UserFixture(
                username = username.trim().ifBlank { "unknown" },
                description = "Public profile preview.",
                karma = "—",
            )
        ).also { user ->
            userIdentityCache[key] = user
        }
    }

    suspend fun ensureCommunityIdentity(name: String) {
        if (!activeAccountIsAuthenticated) return
        val identityRepository = identityMetadataRepository ?: return
        val key = identityMetadataKey(name, prefix = "r/")
        if (key.isEmpty() || key in loadedCommunityIdentities || !loadingCommunityIdentities.add(key)) {
            return
        }
        try {
            val fallback = communityIdentityFor(name)
            val result = withContext(Dispatchers.IO) {
                identityRepository.refreshCommunityIdentity(
                    name = name,
                    fallback = fallback,
                )
            }
            result.valueOrNull()?.let { community ->
                communityIdentityOverrides[key] = community
                communityIdentityCache[key] = community
                if (result is RepositoryResult.Content) loadedCommunityIdentities += key
            }
        } finally {
            loadingCommunityIdentities -= key
        }
    }

    // FEEDS04_CACHED_COMMUNITY_IDENTITY_OFF_MAIN_V1: Favorites and Recent
    // read each community's stored identity from Room during composition, on
    // the main thread and on every recomposition. This reads it once per name
    // on the IO dispatcher into the snapshot map that communityFor serves, so
    // the lists fill in when it lands. Cache only: no Reddit request.
    suspend fun loadCachedCommunityIdentities(names: Iterable<String>) {
        val identityRepository = identityMetadataRepository ?: return
        val pendingByKey = linkedMapOf<String, String>()
        names.forEach { name ->
            val key = identityMetadataKey(name, prefix = "r/")
            if (
                key.isNotEmpty() &&
                key !in communityIdentityOverrides &&
                cachedCommunityIdentityReads.add(key)
            ) {
                pendingByKey[key] = name.trim()
            }
        }
        if (pendingByKey.isEmpty()) return
        val found = withContext(Dispatchers.IO) {
            pendingByKey.mapNotNull { (key, name) ->
                identityRepository.cachedCommunityIdentity(name)?.let { community -> key to community }
            }
        }
        found.forEach { (key, community) ->
            if (key !in communityIdentityOverrides) {
                communityIdentityOverrides[key] = community
                communityIdentityCache[key] = community
            }
        }
    }

    suspend fun ensureCommunityIdentities(names: Iterable<String>) {
        if (!activeAccountIsAuthenticated) return
        val identityRepository = identityMetadataRepository ?: return
        val pendingByKey = linkedMapOf<String, String>()
        names.forEach { name ->
            val key = identityMetadataKey(name, prefix = "r/")
            if (
                key.isNotEmpty() &&
                key !in loadedCommunityIdentities &&
                loadingCommunityIdentities.add(key)
            ) {
                pendingByKey[key] = name.trim()
            }
        }
        if (pendingByKey.isEmpty()) return
        try {
            val fallbacks = pendingByKey.values.map(::communityIdentityFor)
            val result = withContext(Dispatchers.IO) {
                identityRepository.refreshCommunityIdentities(
                    fallbacks = fallbacks,
                )
            }
            result.valueOrNull().orEmpty().forEach { community ->
                val key = identityMetadataKey(community.name, prefix = "r/")
                if (key.isNotEmpty()) {
                    communityIdentityOverrides[key] = community
                    communityIdentityCache[key] = community
                    if (result is RepositoryResult.Content) loadedCommunityIdentities += key
                }
            }
        } finally {
            loadingCommunityIdentities.removeAll(pendingByKey.keys)
        }
    }

    suspend fun ensureUserIdentity(username: String) {
        if (!activeAccountIsAuthenticated) return
        val identityRepository = identityMetadataRepository ?: return
        val key = identityMetadataKey(username, prefix = "u/")
        if (key.isEmpty() || key in loadedUserIdentities || !loadingUserIdentities.add(key)) {
            return
        }
        try {
            val fallback = userFor(username)
            val result = withContext(Dispatchers.IO) {
                identityRepository.refreshUserIdentity(
                    username = username,
                    fallback = fallback,
                )
            }
            val unavailableStatus = (result as? RepositoryResult.Error)
                ?.failureDetails
                ?.httpStatusCode
            if (isUnavailablePublicProfileHttpStatus(unavailableStatus)) {
                unavailableUserProfiles[key] = true
                userIdentityOverrides.remove(key)
            } else {
                result.valueOrNull()?.let { user ->
                    userIdentityOverrides[key] = user
                    userIdentityCache[key] = user
                }
                if (result is RepositoryResult.Content) {
                    unavailableUserProfiles.remove(key)
                    loadedUserIdentities += key
                }
            }
        } finally {
            loadingUserIdentities -= key
        }
    }

    fun avatarUriForAuthor(username: String): String? {
        val key = identityMetadataKey(username, prefix = "u/")
        if (key.isEmpty()) return null
        return authorAvatars[key]
            ?: userIdentityOverrides[key]?.avatarImageUri
    }

    /**
     * Resolves avatars for the authors in [fullnamesByUsername] that are not
     * yet known, in one batch request per 100 accounts. Failures leave the
     * authors unresolved so a later thread can retry; cancellation is
     * rethrown.
     */
    suspend fun ensureAuthorAvatars(fullnamesByUsername: Map<String, String>) {
        if (!activeAccountIsAuthenticated) return
        val identityRepository = identityMetadataRepository ?: return
        val pending = linkedMapOf<String, String>()
        val pendingKeys = mutableListOf<String>()
        for ((username, fullname) in fullnamesByUsername) {
            val key = identityMetadataKey(username, prefix = "u/")
            if (key.isEmpty() || authorAvatars.containsKey(key) || key in loadingAuthorAvatars) continue
            if (pending.putIfAbsent(username, fullname) == null) pendingKeys += key
        }
        if (pending.isEmpty()) return
        loadingAuthorAvatars += pendingKeys
        try {
            val result = withContext(Dispatchers.IO) {
                identityRepository.refreshAuthorAvatars(pending)
            }
            result.valueOrNull()?.forEach { (username, uri) ->
                rememberAuthorAvatar(identityMetadataKey(username, prefix = "u/"), uri)
            }
        } finally {
            loadingAuthorAvatars -= pendingKeys.toSet()
        }
    }

    private fun rememberAuthorAvatar(key: String, uri: String?) {
        if (key.isEmpty()) return
        if (!authorAvatars.containsKey(key)) {
            authorAvatarOrder.addLast(key)
            while (authorAvatarOrder.size > MAX_AUTHOR_AVATARS) {
                authorAvatars.remove(authorAvatarOrder.removeFirst())
            }
        }
        authorAvatars[key] = uri
    }

    fun publicUserProfileUnavailable(username: String): Boolean {
        val key = identityMetadataKey(username, prefix = "u/")
        return key.isNotEmpty() && unavailableUserProfiles[key] == true
    }

    fun hasPublicProfileActivityEvidence(
        username: String,
        evidence: ProfileActivityEvidence,
    ): Boolean {
        val key = runCatching { normalizeRedditUsername(username) }.getOrNull() ?: return false
        return evidence in userProfileActivityEvidence[key].orEmpty()
    }

    internal fun notePublicProfileActivity(
        username: String,
        evidence: ProfileActivityEvidence,
    ) {
        val key = runCatching { normalizeRedditUsername(username) }.getOrNull() ?: return
        userProfileActivityEvidence[key] = userProfileActivityEvidence[key].orEmpty() + evidence
    }
}

internal const val MAX_AUTHOR_AVATARS = 2_000

private fun identityMetadataKey(value: String, prefix: String): String {
    val normalized = value.trim().removePrefix(prefix)
    return normalized
        .takeIf { candidate ->
            candidate.length in 1..100 && candidate.all { character ->
                character.isLetterOrDigit() || character == '_' || character == '-'
            }
        }
        ?.lowercase()
        .orEmpty()
}
