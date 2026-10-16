package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnContentActionPolicyTest {
    @Test
    fun ownCommentCanEditAndDelete() {
        val actions = ownContentActions(
            activeUsername = "brealorg",
            author = "u/BRealOrg",
            contentType = OwnContentType.COMMENT,
        )

        assertTrue(actions.canEdit)
        assertTrue(actions.canDelete)
    }

    @Test
    fun ownSelfPostCanEditBodyAndDelete() {
        val actions = ownContentActions(
            activeUsername = "/u/brealorg",
            author = "brealorg",
            contentType = OwnContentType.SELF_POST,
        )

        assertTrue(actions.canEdit)
        assertTrue(actions.canDelete)
    }

    @Test
    fun ownNonSelfPostCanDeleteButCannotEdit() {
        val actions = ownContentActions(
            activeUsername = "brealorg",
            author = "brealorg",
            contentType = OwnContentType.OTHER_POST,
        )

        assertFalse(actions.canEdit)
        assertTrue(actions.canDelete)
    }

    @Test
    fun otherUsersContentHasNoOwnContentActions() {
        assertEquals(
            OwnContentActions.None,
            ownContentActions(
                activeUsername = "brealorg",
                author = "someone_else",
                contentType = OwnContentType.COMMENT,
            ),
        )
    }

    @Test
    fun deletedOrMissingIdentityNeverOwnsContent() {
        assertEquals(
            OwnContentActions.None,
            ownContentActions(
                activeUsername = "brealorg",
                author = "[deleted]",
                contentType = OwnContentType.SELF_POST,
            ),
        )
        assertEquals(
            OwnContentActions.None,
            ownContentActions(
                activeUsername = null,
                author = "brealorg",
                contentType = OwnContentType.COMMENT,
            ),
        )
    }

    @Test
    fun usernameNormalizationAcceptsCanonicalRedditPrefixes() {
        assertEquals("brealorg", normalizeOwnContentUsername(" /u/brealorg "))
        assertEquals("BRealOrg", normalizeOwnContentUsername("u/BRealOrg"))
        assertNull(normalizeOwnContentUsername("[deleted]"))
        assertNull(normalizeOwnContentUsername("bad user"))
    }
}
