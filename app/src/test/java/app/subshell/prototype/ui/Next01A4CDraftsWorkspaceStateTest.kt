package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour tests for the drafts workspace guarantees that
 * the retired check-next01a4c-contract.sh pinned as text: the You-origin fence,
 * draft-to-draft rebinding without growing the route stack, delete safety
 * for the active draft, and archive-before-close. Written so that the text
 * pins on SubshellState can be retired.
 */
class Next01A4CDraftsWorkspaceStateTest {
    private val persisted = mutableListOf<List<PostComposerDraft>>()

    @Test
    fun expandedDraftsListDetailIsSupportedOnlyWithTheComposerOverTheYouSurface() {
        val state = state()
        assertFalse(state.currentPostComposerSupportsExpandedYouDraftsListDetail())

        state.navigate(MainDestination.YOU)
        assertFalse(state.currentPostComposerSupportsExpandedYouDraftsListDetail())

        state.openPostComposer()
        assertTrue(state.currentPostComposerSupportsExpandedYouDraftsListDetail())

        state.closePostComposer()
        state.navigate(MainDestination.HOME)
        state.openPostComposer()
        assertFalse(state.currentPostComposerSupportsExpandedYouDraftsListDetail())
    }

    @Test
    fun selectingAnotherDraftRebindsInPlaceAndArchivesTheCurrentOne() {
        val saved = draft("saved", title = "Saved draft")
        val other = draft("other", title = "Other draft")
        val withDrafts = state(initial = listOf(saved, other))

        // Without an open composer nothing is rebound.
        assertFalse(withDrafts.replaceCurrentPostComposerDraftSelection(saved.id))

        withDrafts.navigate(MainDestination.YOU)
        withDrafts.openPostComposer()
        val depth = withDrafts.navigationRouteDepth
        val fresh = withDrafts.postComposerDraft
        withDrafts.updatePostComposerDraft(fresh.copy(title = "Typed before switching"))

        assertTrue(withDrafts.replaceCurrentPostComposerDraftSelection(saved.id))
        assertEquals(saved.id, withDrafts.postComposerDraft.id)
        assertEquals("Saved draft", withDrafts.postComposerDraft.title)
        assertEquals(depth, withDrafts.navigationRouteDepth)
        assertTrue(withDrafts.postComposerVisible)
        // The draft that was being edited is archived at the front of the list.
        assertEquals(fresh.id, withDrafts.postComposerDrafts.first().id)
        assertEquals("Typed before switching", withDrafts.postComposerDrafts.first().title)
        assertTrue(persisted.isNotEmpty())

        // Same draft again and an unknown id are accepted no-ops.
        val persistCount = persisted.size
        assertTrue(withDrafts.replaceCurrentPostComposerDraftSelection(saved.id))
        assertTrue(withDrafts.replaceCurrentPostComposerDraftSelection("missing"))
        assertEquals(saved.id, withDrafts.postComposerDraft.id)
        assertEquals(persistCount, persisted.size)
        assertEquals(depth, withDrafts.navigationRouteDepth)

        // Switching to the other saved draft keeps the stack flat too.
        assertTrue(withDrafts.replaceCurrentPostComposerDraftSelection(other.id))
        assertEquals(other.id, withDrafts.postComposerDraft.id)
        assertEquals(depth, withDrafts.navigationRouteDepth)
    }

    @Test
    fun deletingTheActiveDraftFromTheListDiscardsItAndClosesTheComposer() {
        val saved = draft("saved", title = "Saved draft")
        val state = state(initial = listOf(saved))
        assertFalse(state.deleteCurrentPostComposerDraftSelection(saved.id))

        state.navigate(MainDestination.YOU)
        assertTrue(state.openPostComposerDraft(saved.id))
        val depth = state.navigationRouteDepth
        assertFalse(state.deleteCurrentPostComposerDraftSelection("another"))
        assertTrue(state.postComposerVisible)

        assertTrue(state.deleteCurrentPostComposerDraftSelection(saved.id))
        assertFalse(state.postComposerVisible)
        assertEquals(depth - 1, state.navigationRouteDepth)
        assertNull(state.postComposerDrafts.firstOrNull { it.id == saved.id })
        assertFalse(state.postComposerDraft.hasContent)
        assertEquals(emptyList<PostComposerDraft>(), persisted.last())
    }

    @Test
    fun closingArchivesADraftWithContentAndDropsAnEmptyOne() {
        val state = state()
        state.navigate(MainDestination.YOU)
        state.openPostComposer()
        val depth = state.navigationRouteDepth
        val edited = state.postComposerDraft.copy(title = "Keep me")
        state.updatePostComposerDraft(edited)

        state.closePostComposer()
        assertEquals(depth - 1, state.navigationRouteDepth)
        assertFalse(state.postComposerVisible)
        assertEquals(edited.id, state.postComposerDrafts.first().id)
        assertEquals(listOf(edited.id), persisted.last().map { it.id })
        assertFalse(state.postComposerDraft.hasContent)

        // An untouched composer leaves nothing behind.
        state.openPostComposer()
        val emptyId = state.postComposerDraft.id
        state.closePostComposer()
        assertNull(state.postComposerDrafts.firstOrNull { it.id == emptyId })
        assertEquals(listOf(edited.id), state.postComposerDrafts.map { it.id })
    }

    private fun draft(id: String, title: String): PostComposerDraft =
        PostComposerDraft(id = id, accountStableId = "local:root", community = "subshell", title = title)

    private fun state(initial: List<PostComposerDraft> = emptyList()): SubshellState = SubshellState(
        initialPostComposerDrafts = initial,
        persistPostComposerDrafts = { persisted += it },
    )
}
