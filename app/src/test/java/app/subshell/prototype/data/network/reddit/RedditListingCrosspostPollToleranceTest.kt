package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditListingCrosspostPollToleranceTest {
    private val json = SubshellNetworkJson.providerJson

    @Test
    fun crosspostBooleanFalsePollDataDecodesAsNoPoll() {
        val listing = decode(
            listingWithCrosspostPollData("false"),
        )

        val parent =
            listing.data.children
                .single()
                .data
                .crosspostParentList
                .single()

        assertNull(parent.pollData)

        val page =
            listing.toNetworkPage(
                nowEpochSeconds = 1_700_000_000L,
            )

        assertEquals(1, page.items.size)
    }

    @Test
    fun crosspostExplicitNullPollDataRemainsNoPoll() {
        val listing = decode(
            listingWithCrosspostPollData("null"),
        )

        assertNull(
            listing.data.children
                .single()
                .data
                .crosspostParentList
                .single()
                .pollData,
        )
    }

    @Test
    fun crosspostBooleanTruePollDataRemainsRejected() {
        assertMalformed(
            listingWithCrosspostPollData("true"),
        )
    }

    @Test
    fun crosspostNonPollShapesRemainRejected() {
        listOf(
            "\"false\"",
            "17",
            "[]",
        ).forEach { value ->
            assertMalformed(
                listingWithCrosspostPollData(value),
            )
        }
    }

    /**
     * POLLDATA02_TOP_LEVEL_FALSE_IS_NO_POLL_V1 (2026-10-18): Reddit sent false at
     * the top level too; an upvoted-history page failed whole on one such post
     * (was topLevelBooleanFalsePollDataRemainsRejected, written before a
     * top-level case had been seen).
     */
    @Test
    fun topLevelBooleanFalsePollDataDecodesAsNoPoll() {
        val listing = decode(topLevelPollData("false"))

        assertNull(listing.data.children.single().data.pollData)
        assertEquals(1, listing.toNetworkPage(nowEpochSeconds = 1_700_000_000L).items.size)
    }

    @Test
    fun topLevelNonPollShapesRemainRejected() {
        listOf("true", "1", "\"poll\"", "[]").forEach { shape ->
            assertMalformed(topLevelPollData(shape))
        }
    }

    private fun topLevelPollData(value: String) =
        """
        {
          "kind": "Listing",
          "data": {
            "children": [
              {
                "kind": "t3",
                "data": {
                  "id": "wrapper",
                  "title": "Wrapper",
                  "poll_data": $value
                }
              }
            ]
          }
        }
        """.trimIndent()

    private fun decode(
        payload: String,
    ): RedditListingDto =
        json.decodeFromString(
            RedditListingDto.serializer(),
            payload,
        )

    private fun assertMalformed(
        payload: String,
    ) {
        val failure =
            runCatching {
                decode(payload)
            }.exceptionOrNull()

        assertTrue(
            "Expected typed Reddit listing decode to fail",
            failure is SerializationException,
        )
    }

    private fun listingWithCrosspostPollData(
        pollData: String,
    ): String =
        """
        {
          "kind": "Listing",
          "data": {
            "children": [
              {
                "kind": "t3",
                "data": {
                  "id": "wrapper",
                  "title": "Wrapper",
                  "crosspost_parent_list": [
                    {
                      "id": "source",
                      "title": "Source",
                      "poll_data": $pollData
                    }
                  ]
                }
              }
            ]
          }
        }
        """.trimIndent()
}
