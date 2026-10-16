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

    @Test
    fun topLevelBooleanFalsePollDataRemainsRejected() {
        assertMalformed(
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
                      "poll_data": false
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )
    }

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
