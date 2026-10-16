package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UILOGIC2 (ui/ logic findings taken over from dev1): a deep stack keeps the
 * screen on top (OCB-094), scroll anchors are kept by recent use (095), own
 * profile tab anchors keep their tab (096), composer text is never read as a
 * tag (099), and the legacy navigation-labels key restores (296).
 */
class LowUiLogic2Test {
    private val catalog = SubshellProcessRestoreCatalog(
        postKindLookup = { null },
        cachedCommunityLookup = { name -> name },
    )

    private fun snapshot(
        stack: SubshellRouteStack = SubshellRouteStack.forDestination(MainDestination.HOME, FeedSource.HOME, FeedSort.BEST),
        anchors: Map<FeedScrollKey, FeedScrollAnchor> = emptyMap(),
        draft: PostComposerDraft = PostComposerDraft(),
    ) = SubshellProcessSnapshot(
        routeStack = stack,
        scrollAnchors = anchors,
        userProfileTabs = emptyMap(),
        postComposerDraft = draft,
        feedVideoMuted = true,
        backgroundResumeMediaId = null,
        backgroundResumeWasManual = false,
    )

    private fun roundTrip(value: SubshellProcessSnapshot) =
        decodeSubshellProcessSnapshot(encodeSubshellProcessSnapshot(value), catalog)

    @Test
    fun aDeepStackKeepsTheScreenOnTop() {
        var stack = SubshellRouteStack.forDestination(MainDestination.HOME, FeedSource.HOME, FeedSort.BEST)
        repeat(40) { index ->
            stack = stack.push(SubshellRoute.Community(MainDestination.HOME, "community$index", CommunitySort.HOT))
        }

        val restored = roundTrip(snapshot(stack = stack))!!

        assertEquals("community39", (restored.routeStack.current as SubshellRoute.Community).communityName)
        assertTrue(restored.routeStack.entries.first() is SubshellRoute.Main)
    }

    @Test
    fun anchorsAreKeptByRecentUseAndBounded() {
        val store = FeedScrollAnchorStore()
        val a = communityFeedScrollKey("a", CommunitySort.HOT)
        val b = communityFeedScrollKey("b", CommunitySort.HOT)
        store.update(a, FeedScrollAnchor("x", 1, 0))
        store.update(b, FeedScrollAnchor("y", 2, 0))
        store.update(a, FeedScrollAnchor("x", 3, 0))
        assertEquals(listOf(b, a), store.snapshot().keys.toList())

        repeat(FEED_SCROLL_ANCHORS_PER_ACCOUNT + 50) { index ->
            store.update(communityFeedScrollKey("c$index", CommunitySort.HOT), FeedScrollAnchor("z", index, 0))
        }
        assertEquals(FEED_SCROLL_ANCHORS_PER_ACCOUNT, store.snapshot().size)
    }

    @Test
    fun theSnapshotKeepsTheMostRecentAnchors() {
        val home = mainFeedScrollKey(FeedSource.HOME, FeedSort.BEST)
        val anchors = linkedMapOf<FeedScrollKey, FeedScrollAnchor>()
        repeat(256 + 10) { index ->
            anchors[communityFeedScrollKey("c$index", CommunitySort.HOT)] = FeedScrollAnchor("z", index, 0)
        }
        anchors[home] = FeedScrollAnchor("h", 9, 0)

        val restored = roundTrip(snapshot(anchors = anchors))!!.scrollAnchors

        assertEquals(256, restored.size)
        assertTrue(home in restored)
        assertTrue(communityFeedScrollKey("c0", CommunitySort.HOT) !in restored)
    }

    @Test
    fun ownProfileTabAnchorsKeepTheirTab() {
        val drafts = FeedScrollKey.User(username = "me", tab = 7, activityTab = 0)

        val restored = roundTrip(snapshot(anchors = mapOf(drafts to FeedScrollAnchor("p", 4, 0))))!!

        assertEquals(setOf(drafts), restored.scrollAnchors.keys)
    }

    @Test
    fun composerTextIsNeverReadAsATag() {
        val draft = PostComposerDraft(title = "crosspost.ideas", body = "mode=RICH_TEXT is how I write")

        val restored = roundTrip(snapshot(draft = draft))

        assertNotNull(restored)
        assertEquals("crosspost.ideas", restored!!.postComposerDraft.title)
        assertEquals("mode=RICH_TEXT is how I write", restored.postComposerDraft.body)
        assertEquals(draft.inputMode, restored.postComposerDraft.inputMode)
    }

    @Test
    fun theLegacyNavigationLabelsKeyRestores() {
        val (readable, _) = SubshellSettings.splitBackupSettingsValues(
            mapOf(SubshellSettings.LEGACY_NAVIGATION_LABELS_VISIBLE to "false"),
        )

        assertTrue(SubshellSettings.LEGACY_NAVIGATION_LABELS_VISIBLE in readable)
        assertTrue(SubshellSettings.NAVIGATION_LABEL_MODE in SubshellSettings.includedStorageKeys(readable))
        assertEquals(NavigationLabelMode.PARTIAL, SubshellSettings.fromStorageMap(readable).navigationLabelMode)
    }
}
