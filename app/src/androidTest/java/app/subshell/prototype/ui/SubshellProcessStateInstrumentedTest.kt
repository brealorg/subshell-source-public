package app.subshell.prototype.ui

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubshellProcessStateInstrumentedTest {
    @Test
    fun writeSnapshotBeforeExternalProcessRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = createRuntimeFixture()
        val token = encodeSubshellProcessSnapshot(state.processStateSnapshot())
        val persisted = context.getSharedPreferences(RUNTIME_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(RUNTIME_TOKEN, token)
            .commit()
        assertTrue(persisted)
        assertEquals(8, state.navigationRouteDepth)
        assertEquals(3, state.feedScrollAnchorSnapshot().size)

        Log.i(
            LOG_TAG,
            "FEEDPOST_V2E_PHASE=WRITE SNAPSHOT_VERSION=v1 ROUTE_DEPTH=8 " +
                "ANCHOR_COUNT=3 COMPOSER_DRAFT=true",
        )
    }

    @Test
    fun restoreSnapshotAfterExternalProcessRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(RUNTIME_PREFERENCES, Context.MODE_PRIVATE)
        val token = requireNotNull(preferences.getString(RUNTIME_TOKEN, null))
        val state = SubshellState(
            initialProcessSnapshotToken = token,
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        )
        val videoPost = SampleData.posts.first { post -> post.kind == PostKind.VIDEO }
        val homeKey = mainFeedScrollKey(FeedSource.POPULAR, FeedSort.NEW)
        val communityKey = communityFeedScrollKey(videoPost.community, CommunitySort.TOP)
        val searchKey = searchFeedScrollKey(
            destination = MainDestination.SUBS,
            originCommunity = videoPost.community,
            query = "runtime process restore",
            scope = SearchScope.POSTS,
            area = SearchArea.CURRENT_COMMUNITY,
        )

        assertEquals(
            listOf("Main", "Main", "Community", "Search", "Post", "User", "Media", "PostComposer"),
            state.navigationRouteKinds,
        )
        assertEquals("Runtime process draft", state.postComposerDraft.title)
        assertFalse(state.feedVideoMuted)
        assertEquals(2, state.userProfileTab(state.activeAccountUsername))
        assertEquals(videoPost.id, state.navigationLifecycleMediaId)
        assertTrue(state.navigationLifecycleMediaWasManual)
        assertEquals(31, state.feedScrollAnchor(homeKey).pixelOffset)
        assertEquals(27, state.feedScrollAnchor(communityKey).pixelOffset)
        assertEquals(19, state.feedScrollAnchor(searchKey).pixelOffset)

        assertTrue(state.navigateBack())
        assertTrue(state.currentNavigationRoute is SubshellRoute.Media)
        assertEquals(videoPost.id, state.selectedMediaPost?.id)
        assertTrue(state.navigateBack())
        assertTrue(state.currentNavigationRoute is SubshellRoute.User)
        assertTrue(state.navigateBack())
        assertEquals(videoPost.id, state.selectedPost?.id)
        assertTrue(state.navigateBack())
        assertTrue(state.searchVisible)
        assertEquals("runtime process restore", state.searchQuery)
        assertEquals(videoPost.community, state.searchOriginCommunity)

        assertTrue(preferences.edit().clear().commit())
        Log.i(
            LOG_TAG,
            "FEEDPOST_V2E_PHASE=RESTORE FULL_STACK=true KEYED_ANCHORS=true " +
                "TABS=true COMPOSER_DRAFT=true MEDIA_RESUME=true SAFE_BACK_UNWIND=true",
        )
        Log.i(
            LOG_TAG,
            "RESULT=SUBSHELL_FEEDPOST_V2E_PROCESS_STATE_RUNTIME_PASS",
        )
    }

    private fun createRuntimeFixture(): SubshellState {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val videoPost = SampleData.posts.first { post -> post.kind == PostKind.VIDEO }
        val commentId = SampleData.comments.first().id

        state.feedSource = FeedSource.POPULAR
        state.feedSort = FeedSort.NEW
        state.navigate(MainDestination.SUBS)
        state.openSubreddit(videoPost.community)
        state.communitySort = CommunitySort.TOP
        state.openSearch(refocus = false)
        state.updateSearchQuery("runtime process restore")
        state.open(videoPost, commentId = commentId)
        state.openUser(videoPost.author)
        state.openMedia(videoPost, index = 2)
        state.playVideo(videoPost.id)
        state.openPostComposer()
        state.updatePostComposerDraft(
            PostComposerDraft(
                community = videoPost.community,
                title = "Runtime process draft",
                body = "Persisted between separate instrumentation processes.",
            ),
        )
        state.toggleVideoMuted()
        state.selectUserProfileTab(state.activeAccountUsername, 2)

        state.updateFeedScrollAnchor(
            mainFeedScrollKey(FeedSource.POPULAR, FeedSort.NEW),
            videoPost.id,
            fallbackIndex = 7,
            pixelOffset = 31,
        )
        state.updateFeedScrollAnchor(
            communityFeedScrollKey(videoPost.community, CommunitySort.TOP),
            videoPost.id,
            fallbackIndex = 5,
            pixelOffset = 27,
        )
        state.updateFeedScrollAnchor(
            searchFeedScrollKey(
                destination = MainDestination.SUBS,
                originCommunity = videoPost.community,
                query = "runtime process restore",
                scope = SearchScope.POSTS,
                area = SearchArea.CURRENT_COMMUNITY,
            ),
            "search-${videoPost.id}",
            fallbackIndex = 4,
            pixelOffset = 19,
        )
        return state
    }

    private companion object {
        const val LOG_TAG = "subshell-feedpost-v2e"
        const val RUNTIME_PREFERENCES = "subshell_feedpost_v2e_runtime"
        const val RUNTIME_TOKEN = "process_snapshot_token"
    }
}
