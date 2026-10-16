package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.ProfileActivityEvidence
import app.subshell.prototype.ui.isUnavailablePublicProfileHttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUser01PolicyTest {
    @Test
    fun privacyPresentationIsExplicitlyProbabilistic() {
        val presentation = profilePrivacyEmptyPresentation()

        assertEquals("Profile privacy may be enabled", presentation.title)
        assertTrue(presentation.description.contains("may have enabled"))
        assertTrue(presentation.description.contains("privacy settings"))
        assertFalse(presentation.description.contains("Reddit isn't showing"))
    }

    @Test
    fun neutralEmptyPresentationsDoNotClaimPrivacy() {
        val posts = publicProfileEmptyPresentation(
            username = "Example_User",
            activity = ProfileActivityEvidence.POST,
        )
        val comments = publicProfileEmptyPresentation(
            username = "Example_User",
            activity = ProfileActivityEvidence.COMMENT,
        )

        assertEquals("No public profile posts", posts.title)
        assertEquals("No public profile comments", comments.title)
        assertFalse(posts.description.contains("privacy", ignoreCase = true))
        assertFalse(comments.description.contains("privacy", ignoreCase = true))
    }

    @Test
    fun unavailableIdentityStatusIsNarrowAndCopyDoesNotOverclaimCause() {
        assertTrue(isUnavailablePublicProfileHttpStatus(404))
        assertFalse(isUnavailablePublicProfileHttpStatus(403))
        assertFalse(isUnavailablePublicProfileHttpStatus(410))
        assertFalse(isUnavailablePublicProfileHttpStatus(500))
        assertFalse(isUnavailablePublicProfileHttpStatus(null))

        val presentation = profileUnavailablePresentation()
        assertEquals("Profile unavailable", presentation.title)
        assertTrue(presentation.description.contains("could not be found"))
        assertTrue(presentation.description.contains("may have been deleted"))
        assertTrue(presentation.description.contains("suspended"))
        assertFalse(presentation.description.contains("404"))
    }
}
