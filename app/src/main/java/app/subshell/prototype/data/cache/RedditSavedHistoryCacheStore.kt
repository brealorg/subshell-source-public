package app.subshell.prototype.data.cache

import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.network.reddit.RedditSavedHistoryItem
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class RedditSavedHistoryCacheSnapshot(
    val items: List<RedditSavedHistoryItem>,
    val nextCursor: String?,
)

internal fun mergeRedditSavedHistoryItems(
    existing: List<RedditSavedHistoryItem>,
    incoming: List<RedditSavedHistoryItem>,
): List<RedditSavedHistoryItem> =
    (existing + incoming).distinctBy(RedditSavedHistoryItem::fullname)

internal class RedditSavedHistoryCacheStore(
    private val directory: File,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    @Synchronized
    fun read(
        queryKey: RepositoryQueryKey,
    ): RedditSavedHistoryCacheSnapshot? {
        val file = cacheFile(queryKey)
        if (!file.isFile) return null

        // CACHE03_SAVED_HISTORY_STORAGE_FAULTS_V1: a file this build cannot
        // read is removed, so it does not stay in the way of every append.
        return runCatching {
            val decoded = json.decodeFromString(
                RedditSavedHistoryCacheDto.serializer(),
                file.readText(StandardCharsets.UTF_8),
            )
            require(decoded.version == SAVED_HISTORY_CACHE_VERSION) {
                "Unsupported saved history cache version"
            }
            RedditSavedHistoryCacheSnapshot(
                items = decoded.items.map { it.toDomain() },
                nextCursor = decoded.nextCursor
                    ?.trim()
                    ?.takeIf(String::isNotEmpty),
            )
        }.getOrElse {
            file.delete()
            null
        }
    }

    @Synchronized
    fun replace(
        queryKey: RepositoryQueryKey,
        items: List<RedditSavedHistoryItem>,
        nextCursor: String?,
    ): RedditSavedHistoryCacheSnapshot {
        val snapshot = RedditSavedHistoryCacheSnapshot(
            items = items.distinctBy(RedditSavedHistoryItem::fullname),
            nextCursor = nextCursor?.trim()?.takeIf(String::isNotEmpty),
        )
        write(queryKey, snapshot)
        return snapshot
    }

    @Synchronized
    fun append(
        queryKey: RepositoryQueryKey,
        expectedCursor: String,
        items: List<RedditSavedHistoryItem>,
        nextCursor: String?,
    ): RedditSavedHistoryCacheSnapshot {
        val cursor = expectedCursor.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Saved history append cursor cannot be blank")
        val existing = read(queryKey)
            ?: throw SavedHistoryAppendNeedsRefreshException("Saved history append requires a cached refresh")
        if (existing.nextCursor != cursor) {
            throw SavedHistoryAppendNeedsRefreshException(
                "Saved history append cursor does not match the cached cursor",
            )
        }
        val snapshot = RedditSavedHistoryCacheSnapshot(
            items = mergeRedditSavedHistoryItems(
                existing = existing.items,
                incoming = items,
            ),
            nextCursor = nextCursor?.trim()?.takeIf(String::isNotEmpty),
        )
        write(queryKey, snapshot)
        return snapshot
    }

    @Synchronized
    fun clear(
        queryKey: RepositoryQueryKey,
    ) {
        cacheFile(queryKey).delete()
    }

    private fun write(
        queryKey: RepositoryQueryKey,
        snapshot: RedditSavedHistoryCacheSnapshot,
    ) {
        // CACHE03_SAVED_HISTORY_STORAGE_FAULTS_V1: local storage faults are
        // IOExceptions, which the loader reports as STORAGE (retryable), not
        // as a Reddit protocol failure.
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create saved history cache directory")
        }
        val target = cacheFile(queryKey)
        val temp = File(
            directory,
            ".${target.name}.${System.nanoTime()}.tmp",
        )
        val encoded = json.encodeToString(
            RedditSavedHistoryCacheDto(
                nextCursor = snapshot.nextCursor,
                items = snapshot.items.map(RedditSavedHistoryCacheEntryDto::fromDomain),
            ),
        )
        try {
            FileOutputStream(temp).use { output ->
                OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                    writer.write(encoded)
                    writer.flush()
                    output.fd.sync()
                }
            }
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) {
                    throw IOException("Could not atomically replace saved history cache")
                }
            }
        } finally {
            temp.delete()
        }
    }

    private fun cacheFile(
        queryKey: RepositoryQueryKey,
    ): File = File(
        directory,
        sha256(queryKey.value) + ".json",
    )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
}

@Serializable
private data class RedditSavedHistoryCacheDto(
    val version: Int = SAVED_HISTORY_CACHE_VERSION,
    val nextCursor: String? = null,
    val items: List<RedditSavedHistoryCacheEntryDto> = emptyList(),
)

@Serializable
private data class RedditSavedHistoryCacheEntryDto(
    val kind: String,
    val payload: String,
) {
    fun toDomain(): RedditSavedHistoryItem = when (kind) {
        SAVED_HISTORY_POST -> RedditSavedHistoryItem.PostItem(
            post = SubshellCacheCodec.decodePost(payload),
        )
        SAVED_HISTORY_COMMENT -> RedditSavedHistoryItem.CommentItem(
            comment = SubshellCacheCodec.decodeProfileComment(payload),
        )
        else -> error("Unsupported saved history cache item kind")
    }

    companion object {
        fun fromDomain(
            item: RedditSavedHistoryItem,
        ): RedditSavedHistoryCacheEntryDto = when (item) {
            is RedditSavedHistoryItem.PostItem ->
                RedditSavedHistoryCacheEntryDto(
                    kind = SAVED_HISTORY_POST,
                    payload = SubshellCacheCodec.encodePost(item.post),
                )
            is RedditSavedHistoryItem.CommentItem ->
                RedditSavedHistoryCacheEntryDto(
                    kind = SAVED_HISTORY_COMMENT,
                    payload = SubshellCacheCodec.encodeProfileComment(item.comment),
                )
        }
    }
}

private const val SAVED_HISTORY_CACHE_VERSION = 1
private const val SAVED_HISTORY_POST = "post"
private const val SAVED_HISTORY_COMMENT = "comment"

/**
 * LOW_DATA_SAVED_APPEND_WITHOUT_CACHE_REFRESHES_V1 (OCB-618): the cached
 * list an append continues is gone (an unreadable file is dropped) or was
 * replaced meanwhile. Not a Reddit protocol fault: the loader starts the list
 * again from the top.
 */
internal class SavedHistoryAppendNeedsRefreshException(message: String) : IllegalStateException(message)
