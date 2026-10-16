package app.subshell.prototype.ui.components

import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostBodyEditEntryPolicyTest {
    @Test
    fun textPostUsesCanonicalEditEntry() {
        assertEquals(
            PostBodyEditEntryMode.CANONICAL,
            postBodyEditEntryMode(
                kind = PostKind.TEXT,
                body = "Body",
                canonicalEditAllowed = true,
                ownerDeleteAllowed = true,
            ),
        )
    }

    @Test
    fun ownedLinkWithBodyUsesProbeEntry() {
        assertEquals(
            PostBodyEditEntryMode.PROBE,
            postBodyEditEntryMode(
                kind = PostKind.LINK,
                body = "Context for this link",
                canonicalEditAllowed = false,
                ownerDeleteAllowed = true,
            ),
        )
    }

    @Test
    fun ownedMediaWithBodyUsesProbeEntry() {
        assertEquals(
            PostBodyEditEntryMode.PROBE,
            postBodyEditEntryMode(
                kind = PostKind.IMAGE,
                body = "Context for this image",
                canonicalEditAllowed = false,
                ownerDeleteAllowed = true,
            ),
        )
    }

    @Test
    fun nonTextWithoutBodyHasNoProbeEntry() {
        assertNull(
            postBodyEditEntryMode(
                kind = PostKind.LINK,
                body = "   ",
                canonicalEditAllowed = false,
                ownerDeleteAllowed = true,
            ),
        )
    }

    @Test
    fun otherUsersNonTextPostHasNoProbeEntry() {
        assertNull(
            postBodyEditEntryMode(
                kind = PostKind.LINK,
                body = "Context",
                canonicalEditAllowed = false,
                ownerDeleteAllowed = false,
            ),
        )
    }

    @Test
    fun textPostStillRequiresCanonicalEditPermission() {
        assertNull(
            postBodyEditEntryMode(
                kind = PostKind.TEXT,
                body = "Body",
                canonicalEditAllowed = false,
                ownerDeleteAllowed = true,
            ),
        )
    }
}
