package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.normalizePostSaveFullname
import app.subshell.prototype.data.network.reddit.redditPostSaveFormRequest
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.canonicalRedditCommentUrl
import app.subshell.prototype.ui.canonicalRedditPostUrl
import app.subshell.prototype.ui.remoteVideoInputMimeType
import app.subshell.prototype.ui.screens.clampZoomTranslation
import app.subshell.prototype.ui.screens.commentsEntryTargetReady
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.media3.common.MimeTypes
import app.subshell.prototype.ui.externalPostUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAction01PostActionsPolicyTest {
    @Test
    fun postSaveScopeIsExplicitAndPartOfDailyDriverGrant() {
        assertEquals(
            RedditOAuth.coreReadScopes + "save",
            RedditOAuth.livePostSaveScopes,
        )
        assertEquals(
            RedditOAuth.livePostSaveScopes,
            RedditOAuth.scopesFor(RedditOAuthCapability.POST_SAVE),
        )
        assertTrue(
            RedditOAuth.dailyDriverScopes.containsAll(
                RedditOAuth.livePostSaveScopes,
            ),
        )
    }

    @Test
    fun saveAndUnsaveUseCanonicalAbsoluteRedditMutations() {
        assertEquals("t3_abc123", normalizePostSaveFullname("AbC123"))
        assertEquals(
            listOf("api", "save"),
            redditPostSaveFormRequest("abc123", true).pathSegments,
        )
        assertEquals(
            listOf("id" to "t3_abc123"),
            redditPostSaveFormRequest("abc123", true).fields,
        )
        assertEquals(
            listOf("api", "unsave"),
            redditPostSaveFormRequest("t3_ABC123", false).pathSegments,
        )
        assertThrows(IllegalArgumentException::class.java) {
            PostSaveRequest(
                accountStableId = "reddit:fixture",
                postFullname = "abc123",
                saved = true,
            )
        }
    }

    @Test
    fun postAndExternalLinksHaveSeparateCanonicalTargets() {
        val post = Post(
            id = "t3_abc123",
            community = "subshell",
            author = "user",
            age = "now",
            title = "Link",
            body = "",
            score = 1,
            comments = 2,
            kind = PostKind.LINK,
            mediaLabel = "example.com/article",
            permalink = "/r/subshell/comments/abc123/link/",
            externalUrl = "https://example.com/article",
        )
        assertEquals(
            "https://www.reddit.com/r/subshell/comments/abc123/link/",
            canonicalRedditPostUrl(post),
        )
        assertEquals("https://example.com/article", externalPostUrl(post))
        assertNull(externalPostUrl(post.copy(externalUrl = "javascript:alert(1)")))
        assertEquals(
            "https://www.reddit.com/r/subshell/comments/abc123/link/def456/",
            canonicalRedditCommentUrl(post, "t1_def456"),
        )
    }

    @Test
    fun adaptiveVideoMediaIsExportedUsingItsActualInputType() {
        assertEquals(
            MimeTypes.APPLICATION_MPD,
            remoteVideoInputMimeType("https://v.redd.it/example/DASHPlaylist.mpd?source=fallback"),
        )
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            remoteVideoInputMimeType("https://v.redd.it/example/HLSPlaylist.m3u8"),
        )
        assertEquals(
            MimeTypes.VIDEO_MP4,
            remoteVideoInputMimeType("https://cdn.example/video.mp4"),
        )
    }
    @Test
    fun commentsEntryWaitsForLiveCommentMaterialization() {
        assertEquals(
            false,
            commentsEntryTargetReady(
                authenticated = true,
                liveStateMatches = true,
                loading = true,
                loadedCommentCount = 0,
            ),
        )
        assertEquals(
            true,
            commentsEntryTargetReady(
                authenticated = true,
                liveStateMatches = true,
                loading = true,
                loadedCommentCount = 1,
            ),
        )
        assertEquals(
            true,
            commentsEntryTargetReady(
                authenticated = true,
                liveStateMatches = true,
                loading = false,
                loadedCommentCount = 0,
            ),
        )
    }

    @Test
    fun zoomClampUsesFittedImageBoundsVertically() {
        val viewport = Size(width = 1080f, height = 2400f)
        val clamped = clampZoomTranslation(
            translation = Offset(0f, 1800f),
            scale = 2.5f,
            viewportSize = viewport,
            contentAspectRatio = 0.8f,
        )
        assertEquals(0f, clamped.x, 0.01f)
        assertEquals(487.5f, clamped.y, 0.01f)
    }

}
