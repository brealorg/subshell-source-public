package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.RepositoryResult
import io.github.brealorg.subshell.data.SubshellRepository
import io.github.brealorg.subshell.data.createFakeSubshellRepository
import io.github.brealorg.subshell.model.Community
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PERF03 (#69): `communities`, `users` and `inbox` are read from the Room cache
 * (a blocking read and decode) and were read during composition: on every
 * keystroke in Create post (communityPickerCommunities), once per item in a
 * "My communities" search (matchesSearchArea), twice per Communities frame.
 * They are now read once per repository revision.
 */
class Perf03RepositoryListsOncePerRevisionTest {
    private val base = createFakeSubshellRepository()
    private var communityReads = 0
    private val repository = object : SubshellRepository by base {
        override fun communities(request: RepositoryPageRequest): RepositoryResult<List<Community>> {
            communityReads += 1
            return base.communities(request)
        }
    }

    @Test
    fun repeatedReadsWithinARevisionHitTheRepositoryOnce() {
        val state = SubshellState(repository = repository)
        communityReads = 0

        repeat(5) { state.communityPickerCommunities() }
        state.communities.forEach { community -> state.matchesSearchArea(community.name) }
        state.communities

        assertEquals(1, communityReads)
    }

    @Test
    fun aNewRepositoryRevisionReadsAgain() {
        val state = SubshellState(repository = repository)
        val post = state.posts.first()
        state.communities
        communityReads = 0

        state.addLocalComment(post, "A new local comment")
        state.communities
        state.communities

        assertEquals(1, communityReads)
    }
}
