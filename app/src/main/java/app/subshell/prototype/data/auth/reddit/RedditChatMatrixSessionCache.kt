package app.subshell.prototype.data.auth.reddit

import java.security.MessageDigest

internal data class RedditChatCachedMatrixIdentity(
    val tokenFingerprint: String,
    val providerAccountId: String,
    val matrixUserId: String,
)

internal data class RedditChatCachedDirectRoom(
    val tokenFingerprint: String,
    val providerAccountId: String,
    val participantUsername: String,
    val roomId: String,
    val validUntilEpochMs: Long,
)

/** Memory-only cache. It deliberately stores a token fingerprint, never the bearer itself. */
internal class RedditChatMatrixSessionCache(
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val roomTtlMs: Long = DEFAULT_ROOM_TTL_MS,
) {
    private var identity: RedditChatCachedMatrixIdentity? = null
    private val rooms = linkedMapOf<String, RedditChatCachedDirectRoom>()

    @Synchronized
    fun identityFor(token: String, providerAccountId: String): String? {
        val expectedToken = redditChatMatrixTokenFingerprint(token)
        val expectedProvider = providerAccountId.trim().lowercase()
        return identity
            ?.takeIf { it.tokenFingerprint == expectedToken }
            ?.takeIf { it.providerAccountId == expectedProvider }
            ?.matrixUserId
    }

    @Synchronized
    fun rememberIdentity(token: String, providerAccountId: String, matrixUserId: String) {
        val tokenFingerprint = redditChatMatrixTokenFingerprint(token)
        val provider = providerAccountId.trim().lowercase()
        val current = identity
        if (current?.tokenFingerprint != tokenFingerprint || current.providerAccountId != provider) {
            rooms.clear()
        }
        identity = RedditChatCachedMatrixIdentity(
            tokenFingerprint = tokenFingerprint,
            providerAccountId = provider,
            matrixUserId = matrixUserId,
        )
    }

    @Synchronized
    fun roomFor(token: String, providerAccountId: String, participantUsername: String): String? {
        val key = roomKey(token, providerAccountId, participantUsername)
        val cached = rooms[key] ?: return null
        if (nowEpochMs() >= cached.validUntilEpochMs) {
            rooms.remove(key)
            return null
        }
        return cached.roomId
    }

    @Synchronized
    fun rememberRoom(
        token: String,
        providerAccountId: String,
        participantUsername: String,
        roomId: String,
        tokenExpiresAtEpochMs: Long?,
    ) {
        val now = nowEpochMs()
        val ttlEnd = now + roomTtlMs
        val tokenEnd = tokenExpiresAtEpochMs
            ?.minus(TOKEN_EXPIRY_SKEW_MS)
            ?.coerceAtLeast(now)
        val validUntil = if (tokenEnd == null) ttlEnd else minOf(ttlEnd, tokenEnd)
        val provider = providerAccountId.trim().lowercase()
        val participant = participantUsername.trim().lowercase()
        val tokenFingerprint = redditChatMatrixTokenFingerprint(token)
        rooms[roomKey(token, providerAccountId, participantUsername)] = RedditChatCachedDirectRoom(
            tokenFingerprint = tokenFingerprint,
            providerAccountId = provider,
            participantUsername = participant,
            roomId = roomId,
            validUntilEpochMs = validUntil,
        )
    }

    @Synchronized
    fun invalidateRoom(token: String, providerAccountId: String, participantUsername: String) {
        rooms.remove(roomKey(token, providerAccountId, participantUsername))
    }

    @Synchronized
    fun invalidateToken(token: String) {
        val fingerprint = redditChatMatrixTokenFingerprint(token)
        if (identity?.tokenFingerprint == fingerprint) {
            identity = null
        }
        rooms.entries.removeAll { (_, room) -> room.tokenFingerprint == fingerprint }
    }

    private fun roomKey(token: String, providerAccountId: String, participantUsername: String): String =
        listOf(
            redditChatMatrixTokenFingerprint(token),
            providerAccountId.trim().lowercase(),
            participantUsername.trim().lowercase(),
        ).joinToString("|")

    companion object {
        internal const val DEFAULT_ROOM_TTL_MS = 30 * 60 * 1_000L
        internal const val TOKEN_EXPIRY_SKEW_MS = 5 * 60 * 1_000L
    }
}

internal fun redditChatMatrixTokenFingerprint(token: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
