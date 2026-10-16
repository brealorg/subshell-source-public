package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class Crosspost01A1AWriteIdentityPolicyTest {
    private fun request(source: String? = "t3_source123") = TextPostWriteRequest(
        accountStableId = "reddit:redreader_compatibility:t2_crossposter",
        community = "subshell",
        title = "Shared original",
        text = "",
        crosspostFullname = source,
    )

    @Test
    fun canonicalSourceRequiresAnExplicitRedditPostFullname() {
        assertEquals("t3_abc123", normalizeCrosspostSourceFullname(" T3_AbC123 "))
        listOf(
            "", "abc123", "t1_abc123", "t3_", "local-post-1",
            "https://www.reddit.com/r/subshell/comments/abc123/",
            "t3_abc/def", "t3_abc\ndef", "t3_é", "t3_" + "a".repeat(17),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                normalizeCrosspostSourceFullname(invalid)
            }
        }
    }

    @Test
    fun requestIdentityIncludesSourceAndDestinationWithoutReplacingAccountIdentity() {
        val first = request()
        assertEquals(first.requestKey, request(" T3_SOURCE123 ").requestKey)
        assertEquals(first.requestKey, first.copy(community = " /r/SUBSHELL ").requestKey)
        assertNotEquals(first.requestKey, request("t3_other123").requestKey)
        assertNotEquals(first.requestKey, request(null).requestKey)
        assertNotEquals(first.requestKey, first.copy(community = "othercommunity").requestKey)
        assertNotEquals(first.requestKey, first.copy(title = "Changed title").requestKey)
        assertNotEquals(first.requestKey, first.copy(nsfw = true).requestKey)
        assertNotEquals(first.requestKey, first.copy(spoiler = true).requestKey)
        assertNotEquals(first.requestKey, first.copy(sendReplies = false).requestKey)
        assertNotEquals(first.requestKey, first.copy(flairId = "destination-flair").requestKey)
        assertEquals("reddit:redreader_compatibility:t2_crossposter", first.accountStableId)
        assertEquals(64, first.requestKey.length)
    }

    @Test
    fun crosspostCannotBecomeALinkBodyOrMediaUpload() {
        val original = request()
        val invalidChanges: List<() -> TextPostWriteRequest> = listOf(
            { original.copy(text = "Replacement body") },
            { original.copy(linkUrl = "https://www.reddit.com/r/subshell/comments/source123/") },
            { original.copy(mediaUri = "content://image", mediaMimeType = "image/png") },
            { original.copy(mediaAsGif = true) },
            { original.copy(linkPreviewImageUrl = "https://example.org/preview.png") },
            { original.copy(galleryItems = listOf(
                TextPostWriteGalleryItem("content://first", "image/png"),
                TextPostWriteGalleryItem("content://second", "image/png"),
            )) },
            { original.copy(flairText = "Unbound flair text") },
            { original.copy(crosspostFullname = "") },
            { original.copy(accountStableId = "") },
        )
        invalidChanges.forEach { change ->
            assertThrows(IllegalArgumentException::class.java) { change() }
        }
    }

    @Test
    fun acknowledgedWrapperAndRequestedSourceStaySeparate() {
        val submitted = SubmittedTextPost(
            fullname = "t3_created123",
            id = "created123",
            community = "subshell",
            title = "Shared original",
            text = "",
            permalink = "/r/subshell/comments/created123/",
            crosspostFullname = "t3_source123",
        )
        assertEquals("t3_created123", submitted.fullname)
        assertEquals("t3_source123", submitted.crosspostFullname)
        assertNull(submitted.linkUrl)
        assertThrows(IllegalArgumentException::class.java) {
            submitted.copy(text = "Copied source body")
        }
    }

    @Test
    fun legacyTextAndLinkKeysKeepTheirPreCrosspostGoldenValues() {
        // Fixed vectors computed with the unmodified 370ca263 implementation, not with a new encoder.
        assertEquals("5a9b4f7815ded74602292a278f105c137a4bdb542eb0400ff2f2a1327702065e", textPostWriteRequestKey(
            community = "subshell", title = "Legacy title", text = "Legacy body",
        ))
        assertEquals("cc4ecc2354931d33a87be6b62b4e4958aaf340995c331597eebd2650bcc0455d", textPostWriteRequestKey(
            community = "subshell", title = "Legacy link", text = "Context",
            linkUrl = "https://example.org/story", flairId = "flair1", flairText = "News",
        ))
    }
}
