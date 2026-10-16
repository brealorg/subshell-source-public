package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Issue22CommentAuthorFullnameProjectionTest {
    @Test
    fun commentMappingCollectsOneFullnamePerAuthorAndSkipsDeletedAndMalformed() {
        val authorFullnames = linkedMapOf<String, String>()
        val comments = listOf(
            RedditCommentListingDto(),
            RedditCommentListingDto(
                data = RedditCommentListingDataDto(
                    children = listOf(
                        comment("a", "alice", "t2_ALICE1"),
                        comment("b", "bob", "t2_bob2"),
                        comment("c", "alice", "t2_other"),
                        comment("d", null, "t2_deleted"),
                        comment("e", "mallory", "not-a-fullname"),
                        comment("f", "trent", null),
                    ),
                ),
            ),
        ).toComments(
            json = SubshellNetworkJson.providerJson,
            nowEpochSeconds = 10_000L,
            limit = 50,
            maxDepth = 5,
            authorFullnames = authorFullnames,
        )

        assertEquals(6, comments.size)
        assertEquals(
            mapOf("alice" to "t2_alice1", "bob" to "t2_bob2"),
            authorFullnames,
        )
    }

    @Test
    fun fullnamesAreCollectedFromNestedRepliesAndMoreChildren() {
        val authorFullnames = linkedMapOf<String, String>()
        val reply = SubshellNetworkJson.providerJson.encodeToJsonElement(
            RedditCommentListingDto.serializer(),
            RedditCommentListingDto(
                data = RedditCommentListingDataDto(
                    children = listOf(comment("child", "carol", "t2_carol3")),
                ),
            ),
        )
        listOf(
            RedditCommentListingDto(),
            RedditCommentListingDto(
                data = RedditCommentListingDataDto(
                    children = listOf(
                        comment("root", "root", "t2_root0").let { root ->
                            root.copy(data = root.data.copy(replies = reply))
                        },
                    ),
                ),
            ),
        ).toComments(
            json = SubshellNetworkJson.providerJson,
            nowEpochSeconds = 10_000L,
            limit = 50,
            maxDepth = 5,
            authorFullnames = authorFullnames,
        )
        assertEquals(setOf("root", "carol"), authorFullnames.keys)

        val more = linkedMapOf<String, String>()
        RedditMoreChildrenResponseDto(
            json = RedditMoreChildrenJsonDto(
                data = RedditMoreChildrenDataDto(
                    things = listOf(comment("m", "dave", "t2_dave4")),
                ),
            ),
        ).toComments(
            jsonCodec = SubshellNetworkJson.providerJson,
            nowEpochSeconds = 10_000L,
            maxDepth = 5,
            authorFullnames = more,
        )
        assertEquals(mapOf("dave" to "t2_dave4"), more)
    }

    @Test
    fun fullnameNormalizationAcceptsOnlyAccountFullnames() {
        assertEquals("t2_abc12", " T2_ABC12 ".redditAccountFullnameOrNull())
        assertNull("t1_abc12".redditAccountFullnameOrNull())
        assertNull("t2_".redditAccountFullnameOrNull())
        assertNull("t2_with-dash".redditAccountFullnameOrNull())
        assertNull(null.redditAccountFullnameOrNull())
    }

    private fun comment(id: String, author: String?, fullname: String?) = RedditCommentThingDto(
        kind = "t1",
        data = RedditCommentDataDto(
            id = id,
            name = "t1_$id",
            author = author,
            authorFullname = fullname,
            body = "body $id",
            replies = JsonNull,
        ),
    )
}
