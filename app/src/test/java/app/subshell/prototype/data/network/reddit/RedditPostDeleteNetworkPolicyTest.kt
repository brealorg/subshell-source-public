package app.subshell.prototype.data.network.reddit

import org.junit.Assert.assertEquals
import org.junit.Test

class RedditPostDeleteNetworkPolicyTest {
    @Test
    fun deleteUsesApiDelWithCanonicalT3Id() {
        val request = redditPostDeleteFormRequest("abc123")
        assertEquals(listOf("api", "del"), request.pathSegments)
        assertEquals(listOf("id" to "t3_abc123"), request.fields)
    }

    @Test
    fun canonicalT3IdIsPreserved() {
        val request = redditPostDeleteFormRequest("t3_abc123")
        assertEquals(listOf("id" to "t3_abc123"), request.fields)
    }
}
