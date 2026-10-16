package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.UserFlairMutationRequest
import app.subshell.prototype.data.auth.reddit.redditUserFlairSelectRequest
import app.subshell.prototype.data.auth.reddit.redditUserFlairSelectorRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserFlair01A1NetworkPolicyTest {
    @Test
    fun selectorUsesOwnUsernameAndCanonicalCommunityEndpoint() {
        val request = redditUserFlairSelectorRequest("r/SubShell", "owner")
        assertEquals(listOf("r", "subshell", "api", "flairselector"), request.pathSegments)
        assertEquals(mapOf("name" to "owner"), request.fields.toMap())
    }

    @Test
    fun selectionUsesNameNotLinkAndKeepsTemplateIdentity() {
        val request = redditUserFlairSelectRequest(
            "subshell",
            "owner",
            UserFlairMutationRequest("acct", "subshell", "template", "Custom"),
        )
        val fields = request.fields.toMap()
        assertEquals(listOf("r", "subshell", "api", "selectflair"), request.pathSegments)
        assertEquals("owner", fields["name"])
        assertEquals("template", fields["flair_template_id"])
        assertEquals("Custom", fields["text"])
        assertFalse("link" in fields)
    }
}
