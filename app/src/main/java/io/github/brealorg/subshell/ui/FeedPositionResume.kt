package io.github.brealorg.subshell.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import io.github.brealorg.subshell.data.account.RedditFeedUiState
import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.RepositoryLoadOperation
import io.github.brealorg.subshell.ui.error.LocalViewportErrorHostState
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/*
 * ISSUE290_FEEDS_RESUME_WHERE_YOU_LEFT_OFF_V1 (owner, #290): with "Resume where
 * you left off" on, the positions of Home and community feeds are kept on the
 * device across a restart. One small no-backup file holds, per account (a
 * SHA-256 prefix of its stable ID, never the name), each feed's anchor and when
 * the reader was last there. It is written when the app goes to the background
 * and read off the main thread at a cold start; a process snapshot (Android
 * stopping the app) still wins. Device-local state: never backed up, deleted
 * when the setting is turned off.
 */

internal const val FEED_POSITION_FILE_HEADER = "feed-positions-v1"
internal const val FEED_POSITION_FILE_NAME = "feed-positions-v1.txt"
internal const val FEED_POSITIONS_PER_ACCOUNT_ON_DISK = 64

/** A feed position as it is kept across a restart. */
internal data class StoredFeedPosition(
    val accountHash: String,
    val key: FeedScrollKey,
    val anchor: FeedScrollAnchor,
    val usedAtMillis: Long,
)

/** Only Home's feeds and community feeds are resumed after a restart. */
internal fun FeedScrollKey.resumesAcrossRestart(): Boolean =
    this is FeedScrollKey.MainFeed || this is FeedScrollKey.Community

internal fun feedPositionAccountHash(accountStableId: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(accountStableId.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString("") { byte -> "%02x".format(byte) }

internal fun encodeFeedPositions(positions: List<StoredFeedPosition>): String = buildString {
    append(FEED_POSITION_FILE_HEADER)
    positions.forEach { position ->
        val keyFields = when (val key = position.key) {
            is FeedScrollKey.MainFeed -> listOf("MAIN", key.source.name, key.sort.name, key.timePeriod.name)
            is FeedScrollKey.Community -> listOf("COMMUNITY", key.communityName, key.sort.name, key.timePeriod.name)
            else -> return@forEach
        }
        val fields = listOf(position.accountHash) + keyFields + listOf(
            position.anchor.itemKey.orEmpty(),
            position.anchor.fallbackIndex.toString(),
            position.anchor.pixelOffset.toString(),
            position.usedAtMillis.toString(),
        )
        if (fields.any { field -> '\t' in field || '\n' in field || '\r' in field }) return@forEach
        append('\n')
        append(fields.joinToString("\t"))
    }
}

/**
 * The positions in [text] still young enough for [maxAgeMillis] (null = no
 * limit) at [nowMillis]. An unreadable line is left out; a file with another
 * header is read as empty.
 */
internal fun decodeFeedPositions(
    text: String,
    nowMillis: Long,
    maxAgeMillis: Long?,
): List<StoredFeedPosition> {
    val lines = text.lines()
    if (lines.firstOrNull() != FEED_POSITION_FILE_HEADER) return emptyList()
    return lines.drop(1).mapNotNull { line ->
        val fields = line.split('\t')
        if (fields.size != 9) return@mapNotNull null
        val accountHash = fields[0].takeIf(String::isNotBlank) ?: return@mapNotNull null
        val period = enumNamed(fields[4], RedditListingTimePeriod.entries) ?: return@mapNotNull null
        val key = when (fields[1]) {
            "MAIN" -> mainFeedScrollKey(
                source = enumNamed(fields[2], FeedSource.entries) ?: return@mapNotNull null,
                sort = enumNamed(fields[3], FeedSort.entries) ?: return@mapNotNull null,
                timePeriod = period,
            )
            "COMMUNITY" -> communityFeedScrollKey(
                communityName = fields[2].takeIf(String::isNotBlank) ?: return@mapNotNull null,
                sort = enumNamed(fields[3], CommunitySort.entries) ?: return@mapNotNull null,
                timePeriod = period,
            )
            else -> return@mapNotNull null
        }
        val index = fields[6].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
        val offset = fields[7].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
        val usedAt = fields[8].toLongOrNull() ?: return@mapNotNull null
        if (maxAgeMillis != null && nowMillis - usedAt > maxAgeMillis) return@mapNotNull null
        StoredFeedPosition(
            accountHash = accountHash,
            key = key,
            anchor = FeedScrollAnchor(
                itemKey = fields[5].takeIf(String::isNotBlank),
                fallbackIndex = index,
                pixelOffset = offset,
            ),
            usedAtMillis = usedAt,
        )
    }
}

private fun <T : Enum<T>> enumNamed(name: String, entries: List<T>): T? =
    entries.firstOrNull { entry -> entry.name == name }

/**
 * Reads and writes the position file; every call runs on the caller's
 * (background) thread, which is also where the folder is first looked up.
 */
class FeedPositionFile(locate: () -> File) {
    private val file by lazy(locate)

    internal fun read(nowMillis: Long, maxAgeMillis: Long?): List<StoredFeedPosition> =
        runCatching {
            if (!file.isFile) emptyList() else decodeFeedPositions(file.readText(), nowMillis, maxAgeMillis)
        }.getOrDefault(emptyList())

    internal fun write(positions: List<StoredFeedPosition>) {
        runCatching {
            file.parentFile?.mkdirs()
            val partial = File(file.parentFile, "${file.name}.partial")
            partial.writeText(encodeFeedPositions(positions))
            if (!partial.renameTo(file)) {
                file.delete()
                partial.renameTo(file)
            }
        }
    }

    internal fun delete() {
        runCatching { file.delete() }
    }
}

/** How the search for a resumed position after "Load new posts" ended. */
internal enum class ResumedPlaceSearch {
    /** The post is in the new list; the feed goes there. */
    FOUND,

    /** Not in the first [RESUMED_PLACE_SEARCH_PAGES] pages; the feed starts at the top. */
    NOT_FOUND,

    /** Reddit could not be read; the stored list stays and the feed goes to the old place. */
    KEEP_STORED,

    /** The reader scrolled before the search ended; nothing is moved. */
    READER_MOVED,
}

internal const val RESUMED_PLACE_SEARCH_PAGES = 4
private const val RESUMED_PLACE_WAIT_MILLIS = 30_000L
private const val RESUMED_PLACE_STEADY_MILLIS = 750L
private const val RESUMED_PLACE_STEADY_LIMIT_MILLIS = 5_000L

private suspend fun awaitSteadyRows(itemKeys: () -> List<String>) {
    withTimeoutOrNull(RESUMED_PLACE_STEADY_LIMIT_MILLIS) {
        while (true) {
            val before = itemKeys()
            delay(RESUMED_PLACE_STEADY_MILLIS)
            if (itemKeys() == before) break
        }
    }
}

/**
 * ISSUE290_LOAD_NEW_FINDS_THE_PLACE_WITHIN_FOUR_PAGES_V1: with "Load new posts
 * and find my place", a feed resumed after a restart is refreshed, then read on
 * one page at a time (up to [RESUMED_PLACE_SEARCH_PAGES] pages in all, no tap)
 * until the post the reader left at shows up. Not found: the feed starts at
 * the top and the tray says so.
 */
@Composable
internal fun FindTheResumedFeedPlace(
    state: SubshellState,
    scrollKey: FeedScrollKey,
    feedState: RedditFeedUiState,
    itemKeys: List<String>,
    canAppend: Boolean,
    onAppend: () -> Unit,
) {
    val currentFeedState by rememberUpdatedState(feedState)
    val currentItemKeys by rememberUpdatedState(itemKeys)
    val currentCanAppend by rememberUpdatedState(canAppend)
    val currentOnAppend by rememberUpdatedState(onAppend)
    val notices = LocalViewportErrorHostState.current
    LaunchedEffect(
        state,
        scrollKey,
        state.feedPositionsResumePending,
        state.feedPositionResumeRevision,
    ) {
        if (state.feedPositionsResumePending) return@LaunchedEffect
        val target = state.feedPositionAwaitingFind(scrollKey) ?: return@LaunchedEffect
        val outcome = searchForResumedPlace(
            target = target,
            stillSearching = { state.feedPositionAwaitingFind(scrollKey) == target },
            feedState = { currentFeedState },
            itemKeys = { currentItemKeys },
            canAppend = { currentCanAppend },
            append = { currentOnAppend() },
            log = { message -> uiDebugLog("subshell-issue290", message) },
        )
        uiDebugLog(
            "subshell-issue290",
            "resume-find outcome=${outcome.name.lowercase()} at=${currentItemKeys.indexOf(target)} of=${currentItemKeys.size}",
        )
        state.endFeedPositionFind(scrollKey, outcome)
        if (outcome == ResumedPlaceSearch.NOT_FOUND) {
            notices.noticeInfo(
                "Couldn't find where you left off",
                "It wasn't in the newest posts, so the feed starts at the top.",
            )
        }
    }
}

internal suspend fun searchForResumedPlace(
    target: String,
    stillSearching: () -> Boolean,
    feedState: () -> RedditFeedUiState,
    itemKeys: () -> List<String>,
    canAppend: () -> Boolean,
    append: () -> Unit,
    log: (String) -> Unit = {},
): ResumedPlaceSearch {
    val refreshed = withTimeoutOrNull(RESUMED_PLACE_WAIT_MILLIS) {
        snapshotFlow { feedState() }.first { feed ->
            !feed.inProgress && (
                feed.lastAppliedOperation == RepositoryLoadOperation.REFRESH ||
                    feed.failedOperation == RepositoryLoadOperation.REFRESH
                )
        }
    } ?: return ResumedPlaceSearch.KEEP_STORED
    // ISSUE290_THE_SEARCH_WAITS_FOR_THE_NEW_LIST_V1: Reddit's answer is marked
    // applied before the feed shows it (the stored pages are read and
    // replaced on the paging thread), so the old list could "find" the post
    // just before the new one replaced it. The search starts once the rows
    // have stopped changing.
    if (refreshed.failedOperation != RepositoryLoadOperation.REFRESH) awaitSteadyRows(itemKeys)
    log(
        "resume-find refreshed applied=${refreshed.lastAppliedOperation} failed=${refreshed.failedOperation} " +
            "rows=${itemKeys().size} found=${target in itemKeys()}",
    )
    if (refreshed.failedOperation == RepositoryLoadOperation.REFRESH) return ResumedPlaceSearch.KEEP_STORED
    var pagesRead = 1
    while (true) {
        if (!stillSearching()) return ResumedPlaceSearch.READER_MOVED
        if (target in itemKeys()) return ResumedPlaceSearch.FOUND
        if (pagesRead >= RESUMED_PLACE_SEARCH_PAGES || !canAppend()) return ResumedPlaceSearch.NOT_FOUND
        val countBefore = itemKeys().size
        append()
        pagesRead += 1
        log("resume-find append page=$pagesRead rows=$countBefore")
        val appended = withTimeoutOrNull(RESUMED_PLACE_WAIT_MILLIS) {
            snapshotFlow { feedState() to itemKeys().size }.first { (feed, count) ->
                !feed.inProgress && (
                    count != countBefore ||
                        feed.failedOperation == RepositoryLoadOperation.APPEND
                    )
            }
        } ?: return ResumedPlaceSearch.NOT_FOUND
        if (appended.first.failedOperation == RepositoryLoadOperation.APPEND) return ResumedPlaceSearch.NOT_FOUND
    }
}

/**
 * How a Home or community feed's first entry treats a position resumed after a
 * restart (consumed: later entries are ordinary). Show where I left off needs
 * the stored list: when it turns out empty the feed starts at the top and
 * refreshes as before.
 */
internal suspend fun resumedFeedEntry(
    state: SubshellState,
    scrollKey: FeedScrollKey,
    pagingKey: io.github.brealorg.subshell.data.RepositoryQueryKey,
): FeedPositionStart? {
    val resumed = state.feedPositionResume(scrollKey) ?: return null
    if (resumed == FeedPositionStart.SHOW_STORED) {
        snapshotFlow { state.postPagingStoreRead(pagingKey) }.first { read -> read }
        if (state.postPagingSnapshot(pagingKey).items.isEmpty()) {
            state.consumeFeedPositionResume(scrollKey)
            state.forgetResumedFeedPosition(scrollKey)
            return null
        }
    }
    state.consumeFeedPositionResume(scrollKey)
    return resumed
}
