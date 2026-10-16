package app.subshell.prototype.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import app.subshell.prototype.data.RedditListingTimePeriod
import kotlinx.coroutines.flow.distinctUntilChanged

internal sealed interface FeedScrollKey {
    data class MainFeed(
        val source: FeedSource,
        val sort: FeedSort,
        val timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
    ) : FeedScrollKey

    data class Community(
        val communityName: String,
        val sort: CommunitySort,
        val timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
    ) : FeedScrollKey

    data class Search(
        val destination: MainDestination,
        val originCommunity: String?,
        val query: String,
        val scope: SearchScope,
        val area: SearchArea,
    ) : FeedScrollKey

    data class User(
        val username: String,
        val tab: Int,
        val activityTab: Int,
    ) : FeedScrollKey

    data class You(
        val tab: Int,
    ) : FeedScrollKey
}

internal data class FeedScrollAnchor(
    val itemKey: String?,
    val fallbackIndex: Int,
    val pixelOffset: Int,
) {
    init {
        require(fallbackIndex >= 0) { "A feed anchor index cannot be negative" }
        require(pixelOffset >= 0) { "A feed anchor offset cannot be negative" }
    }

    fun resolveIndex(itemKeys: List<String>): Int {
        if (itemKeys.isEmpty()) return 0
        val stableIndex = itemKey
            ?.let(itemKeys::indexOf)
            ?.takeIf { index -> index >= 0 }
        return (stableIndex ?: fallbackIndex).coerceIn(0, itemKeys.lastIndex)
    }

    fun resolveOffset(itemKeys: List<String>): Int =
        if (itemKeys.isEmpty()) 0 else pixelOffset

    companion object {
        val Start = FeedScrollAnchor(itemKey = null, fallbackIndex = 0, pixelOffset = 0)
    }
}

internal data class FeedReturnAnchor(
    val itemKey: String,
    val itemOffset: Int,
) {
    init {
        require(itemKey.isNotBlank()) { "A feed return anchor key cannot be blank" }
    }
}

// ISSUE64_FEED_SCROLL_ANCHORS_PER_ACCOUNT_V1: feed positions are kept per
// account. They were shared, so account B opened Home at account A's row
// and A's deep position held back B's first automatic refresh; switching back
// now finds A where A left off. A process snapshot carries the active
// account's anchors, which belong to that account again on restore.
internal class FeedScrollAnchorStore(
    initialAnchors: Map<FeedScrollKey, FeedScrollAnchor> = emptyMap(),
    private val activeAccount: () -> String = { "" },
) {
    private class AccountAnchors {
        val anchors = linkedMapOf<FeedScrollKey, FeedScrollAnchor>()
        val capturedKeys = mutableSetOf<FeedScrollKey>()
        val returnAnchors = mutableMapOf<FeedScrollKey, FeedReturnAnchor>()
    }

    // LOWUI_SCROLL_ANCHORS_KEPT_MOST_RECENT_V1 (OCB-095): anchors were never
    // evicted, and the process snapshot kept 256 picked by the alphabetical
    // order of their encoded text. The map is kept in use order and bounded;
    // the snapshot keeps the most recently used.
    private fun AccountAnchors.putRecent(key: FeedScrollKey, anchor: FeedScrollAnchor) {
        anchors.remove(key)
        anchors[key] = anchor
        val excess = anchors.size - FEED_SCROLL_ANCHORS_PER_ACCOUNT
        if (excess > 0) {
            anchors.keys
                .filterNot(capturedKeys::contains)
                .take(excess)
                .forEach(anchors::remove)
        }
    }

    private val byAccount = mutableMapOf<String, AccountAnchors>()
    private var pendingInitialAnchors: Map<FeedScrollKey, FeedScrollAnchor>? =
        initialAnchors.takeIf { it.isNotEmpty() }

    private fun current(): AccountAnchors {
        val account = activeAccount()
        val anchors = byAccount.getOrPut(account) { AccountAnchors() }
        pendingInitialAnchors?.let { restored ->
            pendingInitialAnchors = null
            anchors.anchors.putAll(restored)
        }
        return anchors
    }

    fun anchor(key: FeedScrollKey): FeedScrollAnchor = current().anchors[key] ?: FeedScrollAnchor.Start

    fun update(key: FeedScrollKey, anchor: FeedScrollAnchor) {
        val current = current()
        if (key in current.capturedKeys) return
        current.putRecent(key, anchor)
    }

    fun capture(key: FeedScrollKey, anchor: FeedScrollAnchor) {
        val current = current()
        current.putRecent(key, anchor)
        current.capturedKeys += key
    }

    fun release(key: FeedScrollKey) {
        current().capturedKeys -= key
    }

    fun captureReturn(key: FeedScrollKey, anchor: FeedReturnAnchor) {
        current().returnAnchors[key] = anchor
    }

    fun returnAnchor(key: FeedScrollKey): FeedReturnAnchor? = current().returnAnchors[key]

    fun releaseReturn(key: FeedScrollKey) {
        current().returnAnchors.remove(key)
    }

    fun snapshot(): Map<FeedScrollKey, FeedScrollAnchor> = current().anchors.toMap()
}

internal const val FEED_SCROLL_ANCHORS_PER_ACCOUNT = 512

internal fun mainFeedScrollKey(
    source: FeedSource,
    sort: FeedSort,
    timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
): FeedScrollKey = FeedScrollKey.MainFeed(
    source = source,
    sort = sort,
    timePeriod = timePeriod.takeIf { sort.supportsTimePeriod }
        ?: RedditListingTimePeriod.ALL,
)

internal fun communityFeedScrollKey(
    communityName: String,
    sort: CommunitySort,
    timePeriod: RedditListingTimePeriod = RedditListingTimePeriod.ALL,
): FeedScrollKey = FeedScrollKey.Community(
    communityName = normalizeFeedScrollToken(communityName),
    sort = sort,
    timePeriod = timePeriod.takeIf { sort.supportsTimePeriod }
        ?: RedditListingTimePeriod.ALL,
)

internal fun searchFeedScrollKey(
    destination: MainDestination,
    originCommunity: String?,
    query: String,
    scope: SearchScope,
    area: SearchArea,
): FeedScrollKey = FeedScrollKey.Search(
    destination = destination,
    originCommunity = originCommunity?.let(::normalizeFeedScrollToken),
    query = normalizeFeedScrollToken(query),
    scope = scope,
    area = area,
)

internal fun userFeedScrollKey(
    username: String,
    tab: Int,
    activityTab: Int,
): FeedScrollKey {
    val normalizedTab = tab.coerceAtLeast(0)
    return FeedScrollKey.User(
        username = normalizeFeedScrollToken(username),
        tab = normalizedTab,
        activityTab = if (normalizedTab == 2) activityTab.coerceAtLeast(0) else 0,
    )
}

internal fun youFeedScrollKey(tab: Int): FeedScrollKey =
    FeedScrollKey.You(tab = tab.coerceAtLeast(0))

internal fun normalizeFeedScrollToken(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")

internal fun captureFeedScrollAnchor(
    state: SubshellState,
    scrollKey: FeedScrollKey,
    itemKeys: List<String>,
    listState: LazyListState,
) {
    val index = listState.firstVisibleItemIndex
    state.captureFeedScrollAnchor(
        key = scrollKey,
        itemKey = itemKeys.getOrNull(index),
        fallbackIndex = index,
        pixelOffset = listState.firstVisibleItemScrollOffset,
    )
}

internal fun captureExactFeedReturnAnchor(
    state: SubshellState,
    scrollKey: FeedScrollKey,
    itemKey: String,
    listState: LazyListState,
) {
    val visibleItem = listState.layoutInfo.visibleItemsInfo
        .firstOrNull { item -> item.key.toString() == itemKey }
        ?: return
    state.captureFeedReturnAnchor(
        key = scrollKey,
        itemKey = itemKey,
        itemOffset = visibleItem.offset,
    )
}

@Composable
internal fun rememberFeedScrollState(
    state: SubshellState,
    scrollKey: FeedScrollKey,
    itemKeys: List<String>,
): LazyListState {
    val stableItemKeys = remember(itemKeys) { itemKeys.toList() }
    // ISSUE64_FEED_SCROLL_ANCHORS_PER_ACCOUNT_V1: a switch starts the list
    // from the new account's own anchor instead of keeping the old position.
    val account = state.activeAccountId
    val initialAnchor = remember(state, scrollKey, account) { state.feedScrollAnchor(scrollKey) }
    val initialIndex = initialAnchor.resolveIndex(stableItemKeys)
    val initialOffset = initialAnchor.resolveOffset(stableItemKeys)
    val listState = key(account, scrollKey) {
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialIndex,
            initialFirstVisibleItemScrollOffset = initialOffset,
        )
    }

    // FEEDS03_RESTORE_ONCE_THEN_FOLLOW_THE_READER_V1: this effect restarts on
    // every append or hide. Restoring the stored anchor each time snapped a
    // fling back to the position recorded a few frames earlier (scrollToItem
    // also cancels the fling). The general restore now stops once it has
    // placed the anchor's own row, or as soon as the reader drags; the exact
    // return anchor keeps retrying until its row appears.
    val restoreSettled = remember(state, scrollKey, listState) { mutableStateOf(false) }

    LaunchedEffect(state, scrollKey, listState, stableItemKeys) {
        val restored = state.feedScrollAnchor(scrollKey)
        val exactReturn = state.feedReturnAnchor(scrollKey)
        if (stableItemKeys.isNotEmpty()) {
            val exactReturnIndex = exactReturn
                ?.itemKey
                ?.let(stableItemKeys::indexOf)
                ?.takeIf { index -> index >= 0 }
            if (exactReturn != null && exactReturnIndex != null) {
                listState.scrollToItem(
                    index = exactReturnIndex,
                    scrollOffset = -exactReturn.itemOffset,
                )
                withFrameNanos { }
                val restoredItem = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { item -> item.key.toString() == exactReturn.itemKey }
                if (restoredItem != null) {
                    val delta = restoredItem.offset - exactReturn.itemOffset
                    if (delta != 0) listState.scrollBy(delta.toFloat())
                    state.releaseFeedReturnAnchor(scrollKey)
                    restoreSettled.value = true
                }
            } else if (!restoreSettled.value) {
                val targetIndex = restored.resolveIndex(stableItemKeys)
                val targetOffset = restored.resolveOffset(stableItemKeys)
                if (
                    listState.firstVisibleItemIndex != targetIndex ||
                    listState.firstVisibleItemScrollOffset != targetOffset
                ) {
                    listState.scrollToItem(targetIndex, targetOffset)
                }
                if (restored.itemKey == null || restored.itemKey in stableItemKeys) {
                    restoreSettled.value = true
                }
            }
            state.releaseFeedScrollAnchor(scrollKey)
        }

        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                // ISSUE64_FEED_SCROLL_ANCHORS_PER_ACCOUNT_V1: an empty list (a
                // reload after an account switch) or a restore that has not
                // reached its row yet is not a position the reader chose; it
                // used to overwrite the saved anchor with row 0.
                if (stableItemKeys.isEmpty() || !restoreSettled.value) return@collect
                state.updateFeedScrollAnchor(
                    key = scrollKey,
                    itemKey = stableItemKeys.getOrNull(index),
                    fallbackIndex = index,
                    pixelOffset = offset,
                )
            }
    }

    LaunchedEffect(state, scrollKey, listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                restoreSettled.value = true
                state.releaseFeedReturnAnchor(scrollKey)
            }
        }
    }

    DisposableEffect(state, scrollKey, listState, stableItemKeys) {
        onDispose {
            // Only the account that owned this list keeps its last position;
            // an account switch disposes the old list after the switch.
            if (
                state.activeAccountId != account ||
                stableItemKeys.isEmpty() ||
                !restoreSettled.value
            ) {
                return@onDispose
            }
            val index = listState.firstVisibleItemIndex
            state.updateFeedScrollAnchor(
                key = scrollKey,
                itemKey = stableItemKeys.getOrNull(index),
                fallbackIndex = index,
                pixelOffset = listState.firstVisibleItemScrollOffset,
            )
        }
    }

    return listState
}
