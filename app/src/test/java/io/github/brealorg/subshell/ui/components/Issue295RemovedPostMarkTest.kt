package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.data.FakeSubshellRepository
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.localFixtureAccountSummaries
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.ui.FeedSort
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.mainPostFeedQueryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #295 part 3: a removed post is marked for a moderator of its community only,
 * and a confirmed Remove or Approve changes the mark at once.
 * ISSUE295_REMOVED_POST_IS_MARKED_V1.
 */
class Issue295RemovedPostMarkTest {
    private fun post(removed: Boolean, community: String = "subshell") = Post(
        id = "t3_abc", community = community, author = "a", age = "1h", title = "t",
        score = 1, comments = 0, kind = PostKind.TEXT, removed = removed,
    )

    private val moderatorOfSubshell = ModeratorQuickActionsEntryPoints(
        canModerate = { it == "subshell" },
        moderate = { _, _ -> },
        requestRemoval = {},
    )

    @Test
    fun onlyAModeratorOfTheCommunitySeesTheMark() {
        assertTrue(postShowsRemoved(post(removed = true), moderatorOfSubshell))
        assertFalse(postShowsRemoved(post(removed = true, community = "android"), moderatorOfSubshell))
        assertFalse(postShowsRemoved(post(removed = true), null))
        assertFalse(postShowsRemoved(post(removed = false), moderatorOfSubshell))
        assertEquals(listOf("REMOVED", "SPOILER"), postLabels(post(removed = true).copy(spoiler = true), showRemoved = true))
        assertEquals(listOf("SPOILER"), postLabels(post(removed = true).copy(spoiler = true), showRemoved = false))
    }

    @Test
    fun aConfirmedRemoveOrApproveShowsInEveryLoadedFeed() {
        val accounts = localFixtureAccountSummaries(listOf("root"))
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = accounts,
            initialActiveAccountId = accounts.first().stableId,
        )
        val best = mainPostFeedQueryKey(state.activeAccountSession, state.feedSource, FeedSort.BEST)
        state.ensurePostPaging(best)
        val target = SampleData.posts.first()
        val other = SampleData.posts[1]

        state.applyModeratedPostRemoved(target.id, removed = true)
        val items = state.postPagingSnapshot(best).items
        assertTrue(items.single { it.id == target.id }.removed)
        assertFalse(items.single { it.id == other.id }.removed)

        state.applyModeratedPostRemoved(target.id, removed = false)
        assertFalse(state.postPagingSnapshot(best).items.single { it.id == target.id }.removed)
    }
}
