package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import org.junit.Test
import org.junit.Assert.assertEquals

class UserAction01A2BlockedAccountsDtoPolicyTest {
    @Test
    fun redditUserListPayloadDecodesBlockedRelationshipMetadata() {
        val dto = SubshellNetworkJson.providerJson.decodeFromString(
            RedditBlockedAccountsListingDto.serializer(),
            """{"kind":"UserList","data":{"children":[{"date":1700846613.0,"rel_id":"r9_5r4zdy","name":"oiz92","id":"t2_1w72"}]}}""",
        )
        assertEquals("UserList", dto.kind)
        assertEquals("oiz92", dto.data.children.single().name)
        assertEquals("t2_1w72", dto.data.children.single().id)
        assertEquals("r9_5r4zdy", dto.data.children.single().relationshipId)
    }
}
