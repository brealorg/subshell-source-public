package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationCandidate
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Crosspost01A1BReconciliationPolicyTest {
    private fun request() = AuthenticatedTextPostSubmitRequest(
        accountStableId = "reddit:redreader_compatibility:t2_crossposter",
        community = "target", title = "Shared original", text = "",
        crosspostFullname = "t3_source123", flairId = "targetflair", flairText = "News",
    )
    private fun candidate() = RedditSubmitReconciliationCandidate(
        fullname = "t3_created123", id = "created123", community = "target",
        author = "crossposter", title = "Shared original", text = "",
        createdUtcEpochSeconds = 1_005L, permalink = "/r/target/comments/created123/",
        destinationUrl = null, kind = RedditSubmitReconciliationKind.CROSSPOST,
        flairText = "News", nsfw = false, spoiler = false,
        crosspostFullname = "t3_source123", flairId = "targetflair",
    )
    private fun select(vararg values: RedditSubmitReconciliationCandidate,
        request: AuthenticatedTextPostSubmitRequest = request(),
        username: String = "crossposter", at: Long = 1_000_000L,
    ) = selectRedditSubmitReconciliationCandidate(request, username, at, values.toList())

    @Test
    fun aUniqueExactParentMatchSelectsTheCreatedWrapper() {
        val result = select(candidate())
        assertEquals("t3_created123", result?.fullname)
        assertEquals("t3_source123", result?.crosspostFullname)
        assertEquals("t3_created123", select(candidate(), request = request().copy(
            community = "r/target", crosspostFullname = " T3_Source123 ", title = " Shared original ",
        ), username = " CrossPoster ")?.fullname)
    }

    @Test
    fun identicalTitleAndTargetCannotCompensateForMissingOrWrongParent() {
        for (source in listOf(null, "", "t3_other123", "t1_source123", "source123")) {
            assertNull(select(candidate().copy(crosspostFullname = source)))
        }
        assertNull(select(candidate().copy(kind = RedditSubmitReconciliationKind.LINK)))
    }

    @Test
    fun originalPostAndMalformedWrapperCannotBeSelected() {
        assertNull(select(candidate().copy(fullname = "t3_source123", id = "source123")))
        assertNull(select(candidate().copy(fullname = "t3_other123")))
        assertNull(select(candidate().copy(fullname = "t3_é", id = "é")))
    }

    @Test
    fun authorCommunityAndTitleRemainPinned() {
        val variants = listOf(candidate().copy(author = "someoneelse"),
            candidate().copy(community = "elsewhere"), candidate().copy(title = "Other title"))
        variants.forEach { assertNull(select(it)) }
        assertNull(select(candidate(), username = ""))
    }

    // SUBMITREADBACK01 (#141): Reddit or the community may mark or re-flair the
    // crosspost, so these differences no longer hide it.
    @Test
    fun redditMutableMarksAndFlairDoNotHideTheCrosspost() {
        listOf(
            candidate().copy(nsfw = true), candidate().copy(spoiler = true),
            candidate().copy(flairId = null), candidate().copy(flairId = "another-template"),
            candidate().copy(flairText = "Other label"),
        ).forEach { assertEquals("created123", select(it)?.id) }
    }

    @Test
    fun existingDispatchWindowIsEnforcedIncludingItsBoundaries() {
        // SUBMITREADBACK02: the window looks back SUBMIT_READBACK_CLOCK_SKEW_SECONDS (600 s).
        for (time in listOf(400L, 1_300L)) {
            assertEquals("created123", select(candidate().copy(createdUtcEpochSeconds = time))?.id)
        }
        for (time in listOf(399L, 1_301L)) {
            assertNull(select(candidate().copy(createdUtcEpochSeconds = time)))
        }
        assertNull(select(candidate(), at = 0L))
        assertNull(select(candidate(), at = -1L))
    }

    @Test
    fun ambiguityAndEmptyReadbackStayUnknown() {
        assertNull(select())
        assertNull(select(candidate(), candidate().copy(id = "created124", fullname = "t3_created124")))
        assertNull(select(candidate(), candidate()))
        assertEquals("created123", select(candidate().copy(crosspostFullname = "t3_other123"), candidate())?.id)
    }

    @Test
    fun inheritedSourceBodyAndMediaUrlAreNotTreatedAsWrapperContent() {
        val selected = select(candidate().copy(text = "Original source body",
            destinationUrl = "https://example.org/source.jpg"))
        assertEquals("created123", selected?.id)
        val result = requireNotNull(selected).toReconciledSubmittedPost()
        assertEquals("", result.text)
        assertEquals("t3_source123", result.verifiedCrosspostFullname)
        assertNull(result.remoteImageUri)
        assertEquals(emptyList<String>(), result.galleryRemoteImageUris)
        assertEquals("/r/target/comments/created123/", result.permalink)
    }

    @Test
    fun noRequestedFlairDoesNotInventAFlairRequirement() {
        assertEquals("created123", select(candidate().copy(flairId = null, flairText = null),
            request = request().copy(flairId = null, flairText = null))?.id)
    }

    @Test
    fun invalidExclusiveRequestIsRejectedBeforeReadingCandidates() {
        val candidates = object : AbstractList<RedditSubmitReconciliationCandidate>() {
            override val size: Int get() = error("Invalid request must not read candidates")
            override fun get(index: Int): RedditSubmitReconciliationCandidate = error("No read")
        }
        for (bad in listOf(request().copy(text = "replacement"),
            request().copy(linkUrl = "https://example.org/"), request().copy(crosspostFullname = "invalid"))) {
            assertNull(selectSourceVerifiedCrosspostCandidate(bad, "crossposter", 1_000_000L, candidates))
        }
    }

    @Test
    fun malformedCandidateCannotHideALaterValidCandidate() {
        assertEquals("created123", select(candidate().copy(community = "!invalid"), candidate())?.id)
    }

    @Test
    fun plainTextRequestsCannotBeReconciledToCrossposts() {
        val plain = request().copy(crosspostFullname = null, flairId = null, flairText = null)
        assertNull(select(candidate(), request = plain))
        val selected = select(candidate().copy(kind = RedditSubmitReconciliationKind.TEXT,
            crosspostFullname = null), request = plain)
        assertEquals("created123", selected?.id)
        assertNull(requireNotNull(selected).toReconciledSubmittedPost().verifiedCrosspostFullname)
    }
}
