package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LISTINGSKIP02_ONE_BAD_COMMENT_DOES_NOT_FAIL_THE_PAGE_V1 (owner 2026-10-18):
 * the profile's comment page leaves out a comment it cannot read and keeps
 * the rest and the cursor; children that are not a list still fail.
 */
class ListingSkip02Test {
    private val json = SubshellNetworkJson.providerJson

    @Test
    fun aCommentThatDoesNotDecodeIsLeftOut() {
        val listing = json.decodeFromString(
            RedditProfileCommentsListingDto.serializer(),
            """
            {"data": {"after": "t1_next", "children": [
              {"kind": "t1", "data": {"id": "a", "body": "First"}},
              {"kind": "t1", "data": {"id": "bad", "body": null}},
              {"kind": "t1", "data": {"id": "c", "body": "Third"}}
            ]}}
            """.trimIndent(),
        )

        assertEquals(listOf("a", "c"), listing.data.children.map { it.data.id })
        assertEquals("t1_next", listing.data.after)
    }

    @Test
    fun childrenThatAreNotAListStillFail() {
        val failure = runCatching {
            json.decodeFromString(RedditProfileCommentsListingDto.serializer(), """{"data": {"children": {"kind": "t1"}}}""")
        }.exceptionOrNull()

        assertTrue(failure is SerializationException)
    }
}
