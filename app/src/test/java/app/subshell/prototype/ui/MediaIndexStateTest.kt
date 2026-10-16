package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour test for the gallery index update that check-source-contract.sh
 * asserted only as SubshellState text: the index is remembered per post,
 * clamped at zero, mirrored into the open media route and reused when the
 * post's media is opened again.
 */
class MediaIndexStateTest {
    @Test
    fun updatingTheMediaIndexRemembersItPerPostAndRewritesTheOpenMediaRoute() {
        val state = SubshellState()
        val gallery = SampleData.posts.first { it.kind == PostKind.GALLERY }
        val other = SampleData.posts.first { it.id != gallery.id }

        state.updateMediaIndex(gallery.id, 2)
        assertEquals(2, state.galleryIndices[gallery.id])
        assertEquals(2, state.selectedMediaIndex)

        state.open(gallery)
        state.openMedia(gallery)
        val route = state.currentNavigationRoute as SubshellRoute.Media
        assertEquals(2, route.index)
        val depth = state.navigationRouteDepth

        state.updateMediaIndex(gallery.id, 1)
        assertEquals(1, (state.currentNavigationRoute as SubshellRoute.Media).index)
        assertEquals(depth, state.navigationRouteDepth)

        // Another post's index does not touch the open route; negatives clamp.
        state.updateMediaIndex(other.id, 4)
        assertEquals(1, (state.currentNavigationRoute as SubshellRoute.Media).index)
        assertEquals(4, state.galleryIndices[other.id])
        state.updateMediaIndex(gallery.id, -3)
        assertEquals(0, state.selectedMediaIndex)
        assertEquals(0, (state.currentNavigationRoute as SubshellRoute.Media).index)
        assertTrue(state.galleryIndices[gallery.id] == -3)
    }
}
