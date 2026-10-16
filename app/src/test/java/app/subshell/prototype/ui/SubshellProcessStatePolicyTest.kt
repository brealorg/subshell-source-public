package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.model.PostKind
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubshellProcessStatePolicyTest {
    private val videoPost = SampleData.posts.first { post -> post.kind == PostKind.VIDEO }
    private val focusedCommentId = SampleData.comments.first().id

    @Test
    fun capturedFeedAnchorIgnoresLateDisposalUntilTheFeedReturns() {
        val key = mainFeedScrollKey(FeedSource.HOME, FeedSort.BEST)
        val store = FeedScrollAnchorStore()
        val captured = FeedScrollAnchor("post-7", fallbackIndex = 7, pixelOffset = 31)

        store.capture(key, captured)
        store.update(key, FeedScrollAnchor("post-7", fallbackIndex = 7, pixelOffset = 46))
        assertEquals(captured, store.anchor(key))

        store.release(key)
        val resumed = FeedScrollAnchor("post-7", fallbackIndex = 7, pixelOffset = 52)
        store.update(key, resumed)
        assertEquals(resumed, store.anchor(key))
    }

    @Test
    fun fullRouteStackAnchorsTabsComposerAndMediaStateRoundTrip() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.feedSource = FeedSource.POPULAR
        state.feedSort = FeedSort.NEW
        state.navigate(MainDestination.SUBS)
        state.openSubreddit(videoPost.community)
        state.communitySort = CommunitySort.TOP
        state.openSearch(refocus = false)
        state.updateSearchQuery("  full   process restore ")
        state.open(videoPost, commentId = focusedCommentId)
        state.openUser(videoPost.author)
        state.openMedia(videoPost, index = 2)
        state.playVideo(videoPost.id)
        state.openPostComposer()
        state.updatePostComposerDraft(
            PostComposerDraft(
                community = videoPost.community,
                type = ComposerPostType.MEDIA,
                title = "Process-safe draft",
                body = "The route stack and every keyed anchor survive together.",
                inputMode = ComposerInputMode.MARKDOWN,
                media = listOf(
                    ComposerMediaAttachment(
                        uri = "content://subshell.test/draft.gif",
                        mimeType = "image/gif",
                        kind = ComposerMediaKind.GIF,
                        sourceLinkUri = "https://giphy.com/gifs/subshell-test-id",
                    ),
                ),
            ),
        )
        state.selectUserProfileTab(state.activeAccountUsername, 2)
        state.toggleVideoMuted()

        val homeKey = mainFeedScrollKey(
            FeedSource.POPULAR,
            FeedSort.TOP,
            RedditListingTimePeriod.WEEK,
        )
        val communityKey = communityFeedScrollKey(
            videoPost.community,
            CommunitySort.TOP,
            RedditListingTimePeriod.WEEK,
        )
        val searchKey = searchFeedScrollKey(
            destination = MainDestination.SUBS,
            originCommunity = videoPost.community,
            query = "full process restore",
            scope = SearchScope.POSTS,
            area = SearchArea.CURRENT_COMMUNITY,
        )
        val userKey = userFeedScrollKey(state.activeAccountUsername, tab = 2, activityTab = 1)
        val youKey = youFeedScrollKey(2)
        listOf(homeKey, communityKey, searchKey, userKey, youKey)
            .forEachIndexed { index, key ->
                state.updateFeedScrollAnchor(
                    key = key,
                    itemKey = "stable-$index",
                    fallbackIndex = index + 2,
                    pixelOffset = 17 + index,
                )
            }

        val encoded = encodeSubshellProcessSnapshot(state.processStateSnapshot())
        val restored = SubshellState(
            initialProcessSnapshotToken = encoded,
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )

        assertEquals(
            listOf("Main", "Main", "Community", "Search", "Post", "User", "Media", "PostComposer"),
            restored.navigationRouteKinds,
        )
        assertEquals(8, restored.navigationRouteDepth)
        assertEquals("Process-safe draft", restored.postComposerDraft.title)
        assertEquals(ComposerInputMode.MARKDOWN, restored.postComposerDraft.inputMode)
        assertEquals(
            listOf(
                ComposerMediaAttachment(
                    uri = "content://subshell.test/draft.gif",
                    mimeType = "image/gif",
                    kind = ComposerMediaKind.GIF,
                    sourceLinkUri = "https://giphy.com/gifs/subshell-test-id",
                ),
            ),
            restored.postComposerDraft.media,
        )
        assertEquals(5, restored.feedScrollAnchorSnapshot().size)
        assertEquals(17, restored.feedScrollAnchor(homeKey).pixelOffset)
        assertEquals(18, restored.feedScrollAnchor(communityKey).pixelOffset)
        assertEquals(2, restored.userProfileTab(state.activeAccountUsername))
        assertFalse(restored.feedVideoMuted)
        assertEquals(videoPost.id, restored.navigationLifecycleMediaId)
        assertTrue(restored.navigationLifecycleMediaWasManual)

        assertTrue(restored.navigateBack())
        assertTrue(restored.currentNavigationRoute is SubshellRoute.Media)
        assertEquals(videoPost.id, restored.selectedMediaPost?.id)
        assertEquals(2, restored.selectedMediaIndex)

        assertTrue(restored.navigateBack())
        assertTrue(restored.currentNavigationRoute is SubshellRoute.User)
        assertEquals(videoPost.author, restored.selectedUser)

        assertTrue(restored.navigateBack())
        assertTrue(restored.currentNavigationRoute is SubshellRoute.Post)
        assertEquals(videoPost.id, restored.selectedPost?.id)
        assertEquals(focusedCommentId, restored.focusedCommentId)

        assertTrue(restored.navigateBack())
        assertTrue(restored.searchVisible)
        assertEquals("  full   process restore ", restored.searchQuery)
        assertEquals(videoPost.community, restored.searchOriginCommunity)
    }

    @Test
    fun missingPostDropsThePostAndEveryDependentRouteButKeepsValidAnchors() {
        var stack = SubshellRouteStack.forDestination(
            destination = MainDestination.SUBS,
            source = FeedSource.ALL,
            sort = FeedSort.HOT,
        )
        stack = stack.push(
            SubshellRoute.Post(
                FeedPostRoute(
                    postId = "missing-post",
                    focusedCommentId = "missing-comment",
                    visitId = 9,
                    origin = FeedPostOrigin.MainFeed(
                        destination = MainDestination.SUBS,
                        source = FeedSource.ALL,
                        sort = FeedSort.HOT,
                    ),
                ),
            ),
        )
        stack = stack.push(
            SubshellRoute.User(
                destination = MainDestination.SUBS,
                username = "dependent-user",
            ),
        )
        stack = stack.push(SubshellRoute.PostComposer)

        val key = mainFeedScrollKey(FeedSource.ALL, FeedSort.HOT)
        val encoded = encodeSubshellProcessSnapshot(
            SubshellProcessSnapshot(
                routeStack = stack,
                scrollAnchors = mapOf(
                    key to FeedScrollAnchor("still-valid", fallbackIndex = 4, pixelOffset = 23),
                ),
                userProfileTabs = emptyMap(),
                postComposerDraft = PostComposerDraft(title = "dependent draft"),
                feedVideoMuted = true,
                backgroundResumeMediaId = null,
                backgroundResumeWasManual = false,
            ),
        )

        val decoded = requireNotNull(decodeSubshellProcessSnapshot(encoded))
        assertEquals(listOf("Main", "Main"), decoded.routeStack.entries.map(SubshellRoute::kindName))
        assertEquals(23, decoded.scrollAnchors.getValue(key).pixelOffset)
    }

    @Test
    fun unknownEnumsUseSafeDefaultsAndCorruptSnapshotsFallBackToHome() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.feedSource = FeedSource.POPULAR
        state.feedSort = FeedSort.NEW
        state.navigate(MainDestination.SUBS)
        val encoded = encodeSubshellProcessSnapshot(state.processStateSnapshot())
        val mutated = encoded.replace(
            oldValue = snapshotField("NEW"),
            newValue = snapshotField("REMOVED_SORT"),
        )

        val decoded = requireNotNull(decodeSubshellProcessSnapshot(mutated))
        val root = decoded.routeStack.entries.first() as SubshellRoute.Main
        assertEquals(FeedSort.BEST, root.sort)

        assertNull(decodeSubshellProcessSnapshot("VERSION|not-a-valid-field"))
        val fallback = SubshellState(
            initialProcessSnapshotToken = "VERSION|not-a-valid-field",
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        assertEquals(MainDestination.HOME, fallback.destination)
        assertEquals(listOf("Main"), fallback.navigationRouteKinds)
        assertTrue(fallback.feedScrollAnchorSnapshot().isEmpty())
    }

    @Test
    fun activeDraftKeepsRemoteCommunityAndCompleteFlairAcrossProcessRestore() {
        val flair = ComposerFlair(
            templateId = "remote-template",
            text = "Release",
            editable = true,
            textColor = "light",
            backgroundColor = "#ff4500",
            richText = listOf(
                ComposerFlairRichText(
                    elementType = "emoji",
                    emojiRef = ":subshell:",
                    imageUrl = "https://example.com/flair.png",
                ),
            ),
        )
        val stack = SubshellRouteStack
            .forDestination(MainDestination.HOME, FeedSource.HOME, FeedSort.BEST)
            .push(SubshellRoute.PostComposer)
        val encoded = encodeSubshellProcessSnapshot(
            SubshellProcessSnapshot(
                routeStack = stack,
                scrollAnchors = emptyMap(),
                userProfileTabs = emptyMap(),
                postComposerDraft = PostComposerDraft(
                    community = "norge",
                    title = "Remote community draft",
                    flair = flair,
                ),
                feedVideoMuted = true,
                backgroundResumeMediaId = null,
                backgroundResumeWasManual = false,
            ),
        )

        val restored = requireNotNull(decodeSubshellProcessSnapshot(encoded))
        assertEquals("norge", restored.postComposerDraft.community)
        assertEquals(flair, restored.postComposerDraft.flair)
    }

    // LOWUI4_LIBRARY_RETIRED_V1 / LOWUI4_UNREAD_TAB_STATE_RETIRED_V1: a snapshot
    // from an older build with a Library route and the retired tab records
    // still restores; the Library route is skipped.
    @Test
    fun anOlderSnapshotWithALibraryRouteAndRetiredTabsStillRestores() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.openSecondary(SecondaryDestination.SETTINGS)
        val older = encodeSubshellProcessSnapshot(state.processStateSnapshot())
            .replace(snapshotField("SETTINGS"), snapshotField("LIBRARY")) +
            "\nLIBRARY_TAB|" + snapshotField("3") +
            "\nYOU_TAB|" + snapshotField("2") +
            "\nUSER_ACTIVITY_TAB|" + snapshotField("someone") + "|" + snapshotField("1")

        val decoded = requireNotNull(decodeSubshellProcessSnapshot(older))

        assertEquals(listOf("Main"), decoded.routeStack.entries.map { route -> route.kindName })
    }

    private fun snapshotField(value: String): String =
        "s" + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}
