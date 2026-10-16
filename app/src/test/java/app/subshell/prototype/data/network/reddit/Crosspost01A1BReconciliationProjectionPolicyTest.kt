package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Crosspost01A1BReconciliationProjectionPolicyTest {
    private fun post() = RedditPostDataDto(
        id = "created123", name = "t3_created123", subreddit = "target",
        author = "crossposter", title = "Shared original", createdUtc = 1_005.0,
        crosspostParent = "t3_source123", linkFlairTemplateId = "targetflair",
    )
    private fun project(post: RedditPostDataDto) = RedditListingDto(
        data = RedditListingDataDto(children = listOf(RedditThingDto(kind = "t3", data = post))),
    ).toSubmitReconciliationCandidates()

    @Test
    fun directOuterParentAndDestinationFlairIdSurviveActualJsonDecode() {
        val json = """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
            "id":"created123","name":"t3_created123","subreddit":"target",
            "author":"crossposter","title":"Shared original","created_utc":1005,
            "crosspost_parent":"t3_source123","link_flair_template_id":"targetflair",
            "crosspost_parent_list":[{"id":"other123","name":"t3_other123"}],
            "is_video":true,"url_overridden_by_dest":"https://example.org/source.mp4"
        }}]}}"""
        val candidate = SubshellNetworkJson.providerJson.decodeFromString<RedditListingDto>(json)
            .toSubmitReconciliationCandidates().single()
        assertEquals("t3_created123", candidate.fullname)
        assertEquals("t3_source123", candidate.crosspostFullname)
        assertEquals("targetflair", candidate.flairId)
        assertEquals(RedditSubmitReconciliationKind.CROSSPOST, candidate.kind)
    }

    @Test
    fun crosspostKindWinsOverAllSourceMediaHints() {
        for (variant in listOf(post(), post().copy(isVideo = true), post().copy(isGallery = true),
            post().copy(postHint = "image"), post().copy(postHint = "rich:video"),
            post().copy(postHint = "link", destinationUrl = "https://example.org/source"))) {
            val candidate = project(variant).single()
            assertEquals(RedditSubmitReconciliationKind.CROSSPOST, candidate.kind)
            assertEquals("t3_source123", candidate.crosspostFullname)
        }
    }

    @Test
    fun nestedPreviewAndSourceUrlCannotSupplyMissingDirectParent() {
        val candidate = project(post().copy(
            crosspostParent = null,
            crosspostParentList = listOf(RedditCrosspostParentDto(id = "source123", name = "t3_source123")),
            destinationUrl = "https://www.reddit.com/r/source/comments/source123/",
        )).single()
        assertEquals(RedditSubmitReconciliationKind.CROSSPOST, candidate.kind)
        assertNull(candidate.crosspostFullname)
    }

    @Test
    fun invalidParentMarkersCannotBecomeOrdinaryTextCandidates() {
        for (parent in listOf("", " ", "source123", "t1_source123", "t3_bad-id", "https://reddit.com/")) {
            val candidate = project(post().copy(crosspostParent = parent)).single()
            assertEquals(RedditSubmitReconciliationKind.CROSSPOST, candidate.kind)
            assertNull(candidate.crosspostFullname)
        }
    }

    @Test
    fun canonicalParentNormalizationDoesNotInventAnIdentity() {
        val candidate = project(post().copy(crosspostParent = " T3_Source123 ")).single()
        assertEquals("t3_source123", candidate.crosspostFullname)
    }

    @Test
    fun invalidCrosspostWrapperIdentityIsDiscarded() {
        assertTrue(project(post().copy(name = "t3_source123")).isEmpty())
        assertTrue(project(post().copy(id = "é", name = "t3_é")).isEmpty())
        assertTrue(project(post().copy(name = "")).isEmpty())
    }

    @Test
    fun wrapperPermalinkCannotPointToTheOriginalPost() {
        val candidate = project(post().copy(permalink = "/r/source/comments/source123/")).single()
        assertEquals("/r/target/comments/created123/", candidate.permalink)
    }

    @Test
    fun ordinaryPostsKeepLegacyKindAndHaveNoVerifiedParent() {
        val plain = project(post().copy(crosspostParent = null)).single()
        assertEquals(RedditSubmitReconciliationKind.TEXT, plain.kind)
        assertNull(plain.crosspostFullname)
        val linked = project(post().copy(crosspostParent = null,
            destinationUrl = "https://example.org/article")).single()
        assertEquals(RedditSubmitReconciliationKind.LINK, linked.kind)
    }
}
