package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class Crosspost01A1ESourceProjectionPolicyTest {
    private fun data() = CrosspostSourceDataDto("t3_source123", "source123", "source", "author", "Original title", false, false, true)
    private fun listing(data: CrosspostSourceDataDto = data()) = CrosspostSourceListingDto(
        CrosspostSourceListingDataDto(listOf(CrosspostSourceThingDto("t3", data))),
    )
    @Test fun infoReadUsesExplicitCanonicalFullnameNotSourceUrlOrNestedParent() {
        val request = redditCrosspostSourceRequest(" t3_SOURCE123 ")
        // ISSUE281_READS_AVOID_API_INFO_V1: read from the post's thread.
        assertEquals(listOf("comments", "source123"), request.pathSegments)
        assertEquals(listOf("raw_json" to "1", "limit" to "1", "depth" to "1"), request.queryParameters)
        for (bad in listOf("source123", "t1_source123", "https://reddit.com/comments/source123")) {
            assertTrue(runCatching { redditCrosspostSourceRequest(bad) }.isFailure)
        }
    }
    @Test fun literalJsonMapsSourceAvailabilityAndSensitivity() {
        val value = SubshellNetworkJson.providerJson.decodeFromString<CrosspostSourceListingDto>("""
            {"data":{"children":[{"kind":"t3","data":{
              "name":"t3_source123","id":"source123","subreddit":"source","author":"author",
              "title":"Original title","over_18":true,"spoiler":false,"is_crosspostable":true,
              "removed_by_category":null,"crosspost_parent":"t3_not_selected"
            }}]}}
        """.trimIndent())
        val evidence = requireNotNull(value.toCrosspostSourceEvidence("t3_source123"))
        assertEquals("t3_source123", evidence.fullname)
        assertTrue(evidence.nsfw); assertFalse(evidence.spoiler)
    }
    @Test fun missingFalseOrRemovedEligibilityCannotApproveSource() {
        for (d in listOf(data().copy(crosspostable = null), data().copy(crosspostable = false),
            data().copy(removedByCategory = "moderator"), data().copy(author = "[deleted]"))) {
            assertNull(listing(d).toCrosspostSourceEvidence("t3_source123"))
        }
    }
    @Test fun wrongIdentityOrIncompleteFlagsFailClosed() {
        for (d in listOf(data().copy(name = "t3_other"), data().copy(id = "other"), data().copy(nsfw = null),
            data().copy(spoiler = null), data().copy(subreddit = "bad/path"), data().copy(title = null))) {
            assertNull(listing(d).toCrosspostSourceEvidence("t3_source123"))
        }
    }
    @Test fun emptyDuplicateAndWrongKindListingsCannotSupplyProof() {
        val thing = CrosspostSourceThingDto("t3", data())
        for (items in listOf(emptyList(), listOf(thing, thing), listOf(thing.copy(kind = "t1")))) {
            assertNull(CrosspostSourceListingDto(CrosspostSourceListingDataDto(items)).toCrosspostSourceEvidence("t3_source123"))
        }
    }
    @Test fun authorAndTitleArePreservedForComparisonNotSilentlyReplaced() {
        val d = data().copy(author = "OriginalAuthor", title = "Original & exact title")
        val evidence = requireNotNull(listing(d).toCrosspostSourceEvidence("t3_source123"))
        assertEquals(d.author, evidence.author); assertEquals(d.title, evidence.title)
    }
}
