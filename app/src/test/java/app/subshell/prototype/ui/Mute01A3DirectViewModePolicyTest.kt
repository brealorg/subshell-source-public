package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mute01A3DirectViewModePolicyTest {
    @Test
    fun aboutOnlyRequiresActiveMuteAndStrictMode() {
        assertFalse(shouldHideMutedCommunityPosts(false, MutedCommunityDirectViewMode.ABOUT_ONLY))
        assertFalse(shouldHideMutedCommunityPosts(true, MutedCommunityDirectViewMode.SHOW_POSTS))
        assertTrue(shouldHideMutedCommunityPosts(true, MutedCommunityDirectViewMode.ABOUT_ONLY))
    }

    @Test
    fun storedModeFallsBackToShowPosts() {
        assertEquals(MutedCommunityDirectViewMode.SHOW_POSTS, decodeMutedCommunityDirectViewMode(null))
        assertEquals(MutedCommunityDirectViewMode.SHOW_POSTS, decodeMutedCommunityDirectViewMode("unknown"))
        assertEquals(MutedCommunityDirectViewMode.ABOUT_ONLY, decodeMutedCommunityDirectViewMode("ABOUT_ONLY"))
    }

    @Test
    fun stateOwnsOneShotCommunityMuteEditorRequest() {
        val state = SubshellState()
        assertNull(state.requestedCommunityMuteEditorName)
        state.requestCommunityMuteEditor("r/MotoGP")
        assertEquals("motogp", state.requestedCommunityMuteEditorName)
        state.clearRequestedCommunityMuteEditor()
        assertNull(state.requestedCommunityMuteEditorName)
    }
}
