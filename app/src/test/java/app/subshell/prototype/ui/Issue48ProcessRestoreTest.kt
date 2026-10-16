package app.subshell.prototype.ui

import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.createFixtureFreeLocalSubshellRepository
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE48_RESTORE_LOOKS_UP_THE_CACHE_V1 / ISSUE49_COMMENT_DRAFT_SURVIVES_RESTORE_V1:
 * in the app the repository's list pages are empty (live posts and inbox rows
 * live only in the cache), so restore used to drop every open post, the media
 * viewer, a community search and the unsent reply. The repository here has the
 * production shape: empty lists, the live post found by id.
 */
class Issue48ProcessRestoreTest {
    private val livePost: Post = SampleData.posts
        .first { post -> post.kind == PostKind.VIDEO }
        .copy(id = "t3_live48", community = "Kotlin")

    private val productionShaped: SubshellRepository =
        object : SubshellRepository by createFixtureFreeLocalSubshellRepository() {
            override fun post(postId: String): RepositoryResult<Post> =
                if (postId == livePost.id) {
                    RepositoryResult.Content(livePost)
                } else {
                    RepositoryResult.Empty("not cached")
                }

            // ISSUE70: the screens read the active account's copy.
            override fun postForAccount(postId: String, accountStableId: String?): RepositoryResult<Post> =
                post(postId)
        }

    private fun state(token: String? = null) = SubshellState(
        repository = productionShaped,
        initialProcessSnapshotToken = token,
        initialCommentActionPlacement = CommentActionPlacement.RIGHT,
    )

    @Test
    fun anOpenLivePostItsMediaAndACommunitySearchSurviveRestore() {
        val state = state()
        state.openSubreddit("Kotlin")
        state.openSearch(refocus = false)
        state.updateSearchArea(SearchArea.CURRENT_COMMUNITY)
        state.updateSearchQuery("coroutines")
        val searchedCommunity = requireNotNull(state.searchOriginCommunity)
        state.open(livePost)
        state.openMedia(livePost, index = 0)

        val restored = state(encodeSubshellProcessSnapshot(state.processStateSnapshot()))

        assertEquals(
            listOf("Main", "Community", "Search", "Post", "Media"),
            restored.navigationRouteKinds,
        )
        assertEquals(livePost.id, restored.selectedMediaPost?.id)
        assertTrue(restored.navigateBack())
        assertEquals(livePost.id, restored.selectedPost?.id)
        assertTrue(restored.navigateBack())
        assertEquals(SearchArea.CURRENT_COMMUNITY, restored.searchArea)
        assertEquals(searchedCommunity, restored.searchOriginCommunity)
    }

    @Test
    fun anUnsentReplySurvivesRestoreEvenForAPostThatIsNotCached() {
        val state = state()
        val uncached = livePost.copy(id = "t3_notcached")
        state.open(uncached)
        assertTrue(state.openCommentComposer(uncached))
        state.updateCommentComposerDraft(
            requireNotNull(state.commentComposerDraft).copy(body = "a long reply"),
        )

        val restored = state(encodeSubshellProcessSnapshot(state.processStateSnapshot()))

        assertEquals("a long reply", restored.commentComposerDraft?.body)
        assertEquals("t3_notcached", restored.commentComposerDraft?.target?.postId)
    }
}
