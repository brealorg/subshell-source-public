package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityInfo01A1NetworkPolicyTest {
    @Test
    fun requestsUseCanonicalReadOnlyCommunityPaths() {
        assertEquals(
            listOf("r", "norge", "about"),
            redditCommunityInfoJsonRequest(
                communityName = "r/Norge",
                resource = RedditCommunityInfoResource.SIDEBAR,
            ).pathSegments,
        )
        assertEquals(
            listOf("r", "norge", "about", "rules"),
            redditCommunityInfoJsonRequest(
                communityName = "/r/Norge",
                resource = RedditCommunityInfoResource.RULES,
            ).pathSegments,
        )
        assertEquals(
            listOf("r", "norge", "about", "moderators"),
            redditCommunityInfoJsonRequest(
                communityName = "Norge",
                resource = RedditCommunityInfoResource.MODERATORS,
            ).pathSegments,
        )
        RedditCommunityInfoResource.entries.forEach { resource ->
            assertEquals(
                listOf("raw_json" to "1"),
                redditCommunityInfoJsonRequest("norge", resource).queryParameters,
            )
        }
    }

    @Test
    fun rulesProjectionIsBoundedSortedAndTolerant() {
        val dto = SubshellNetworkJson.providerJson.decodeFromString<RedditCommunityRulesDto>(
            """
            {
              "rules": [
                {"kind":"all","short_name":"Second","description":" two ","priority":2},
                null,
                {"kind":"link","short_name":" First ","description":" one ","priority":1},
                {"kind":"all","short_name":"first","description":"duplicate","priority":8},
                {"kind":"all","short_name":"   ","description":"ignored","priority":0}
              ],
              "site_rules":["Spam"]
            }
            """.trimIndent(),
        )
        val rules = dto.toCommunityRules()
        assertEquals(listOf("First", "Second"), rules.map { it.shortName })
        assertEquals(listOf(1, 2), rules.map { it.priority })
        assertEquals("one", rules.first().description)
        assertEquals("link", rules.first().kind)
    }

    @Test
    fun sidebarProjectionPreservesMarkdownButTrimsOuterWhitespace() {
        val dto = SubshellNetworkJson.providerJson.decodeFromString<RedditCommunitySidebarDto>(
            """
            {"kind":"t5","data":{"description":"  **Hello**\n\n[Rules](/r/test/about/rules)  "}}
            """.trimIndent(),
        )
        assertEquals(
            "**Hello**\n\n[Rules](/r/test/about/rules)",
            dto.toSidebarMarkdown(),
        )
        assertNull(RedditCommunitySidebarDto().toSidebarMarkdown())
    }

    @Test
    fun moderatorProjectionSupportsRelationshipListShape() {
        val dto = SubshellNetworkJson.providerJson.decodeFromString<RedditCommunityModeratorsDto>(
            """
            {
              "kind":"UserList",
              "data":{
                "children":[
                  {"name":"ModTwo","mod_permissions":["posts","all","posts"]},
                  null,
                  {"name":"modone","mod_permissions":[]},
                  {"name":"MODONE","mod_permissions":["mail"]},
                  {"name":"  ","mod_permissions":["all"]}
                ]
              }
            }
            """.trimIndent(),
        )
        val moderators = dto.toCommunityModerators()
        assertEquals(listOf("ModTwo", "modone"), moderators.map { it.username })
        assertEquals(listOf("all", "posts"), moderators.first().permissions)
        assertTrue(moderators.last().permissions.isEmpty())
    }

    // STEP3 (communityinfo01a1): what Reddit sends is bounded before the app
    // keeps it — 100 000 sidebar characters, 100 rules, 500 moderators.
    @Test
    fun projectionsAreBounded() {
        val json = SubshellNetworkJson.providerJson
        val sidebar = json.decodeFromString<RedditCommunitySidebarDto>(
            """{"kind":"t5","data":{"description":"${"a".repeat(100_001)}"}}""",
        )
        assertEquals(100_000, sidebar.toSidebarMarkdown()!!.length)

        val rules = json.decodeFromString<RedditCommunityRulesDto>(
            (0 until 150).joinToString(prefix = """{"rules":[""", postfix = "]}") { i ->
                """{"kind":"all","short_name":"Rule $i","priority":$i}"""
            },
        ).toCommunityRules()
        assertEquals(100, rules.size)
        assertEquals("Rule 0", rules.first().shortName)
        assertEquals("Rule 99", rules.last().shortName)

        val moderators = json.decodeFromString<RedditCommunityModeratorsDto>(
            (0 until 600).joinToString(prefix = """{"kind":"UserList","data":{"children":[""", postfix = "]}}") { i ->
                """{"name":"mod$i","mod_permissions":[]}"""
            },
        ).toCommunityModerators()
        assertEquals(500, moderators.size)
        assertEquals("mod0", moderators.first().username)
        assertEquals("mod499", moderators.last().username)
    }
}
