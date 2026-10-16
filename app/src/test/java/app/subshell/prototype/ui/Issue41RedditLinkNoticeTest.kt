package app.subshell.prototype.ui

import app.subshell.prototype.ui.app.RedditLinkUnavailableReason
import app.subshell.prototype.ui.app.redditLinkUnavailablePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #41: a Reddit link Subshell cannot route becomes a launch, so the
 * app can say so; links to other sites are still left alone.
 */
class Issue41RedditLinkNoticeTest {
    @Test
    fun anUnsupportedRedditLinkStillBecomesALaunch() {
        val raw = "https://www.reddit.com/settings/account"
        assertNull(resolveRedditTarget(raw))

        val launch = redditViewIntentLaunchOrNull("android.intent.action.VIEW", raw, requestId = 7L)

        assertNotNull(launch)
        assertEquals(raw, launch?.rawUri)
    }

    @Test
    fun onlyRedditWebHostsCount() {
        assertTrue(isRedditWebLink("https://reddit.com/anything"))
        assertTrue(isRedditWebLink("http://old.reddit.com/r/x/about"))
        assertTrue(isRedditWebLink("https://redd.it/a/b"))
        assertFalse(isRedditWebLink("https://github.com/brealorg/subshell"))
        assertFalse(isRedditWebLink("https://evilreddit.com/r/x"))
        assertFalse(isRedditWebLink("javascript:alert(1)"))
        assertFalse(isRedditWebLink("/r/android/wiki"))
        assertNull(
            redditViewIntentLaunchOrNull(
                "android.intent.action.VIEW",
                "https://github.com/brealorg/subshell",
                requestId = 8L,
            ),
        )
    }

    @Test
    fun noticesInformAndNeverSendTheLinkOutOfTheApp() {
        RedditLinkUnavailableReason.entries.forEach { reason ->
            val presentation = redditLinkUnavailablePresentation(reason)
            assertNull(presentation.primaryAction)
            assertNull(presentation.secondaryAction)
        }
        assertEquals(
            "Can't open this link here",
            redditLinkUnavailablePresentation(RedditLinkUnavailableReason.UNSUPPORTED_LINK).title,
        )
        assertEquals(
            "Couldn't open this post",
            redditLinkUnavailablePresentation(RedditLinkUnavailableReason.POST_UNAVAILABLE).title,
        )
    }

    @Test
    fun linksTappedInsideTheAppStayInTheAppWhenTheyAreRedditLinks() {
        val inApp = mutableListOf<String>()
        val platform = mutableListOf<String>()
        val handler = InAppRedditUriHandler(
            platform = object : androidx.compose.ui.platform.UriHandler {
                override fun openUri(uri: String) {
                    platform += uri
                }
            },
            onRedditLink = { uri -> inApp += uri },
        )

        handler.openUri("https://reddit.com/link/pbpmii5/video/8n56vczekerh1/player")
        handler.openUri("https://www.reddit.com/settings/account")
        handler.openUri("/r/android")
        handler.openUri("https://example.com/article")

        assertEquals(
            listOf(
                "https://reddit.com/link/pbpmii5/video/8n56vczekerh1/player",
                "https://www.reddit.com/settings/account",
                "/r/android",
            ),
            inApp,
        )
        assertEquals(listOf("https://example.com/article"), platform)
    }
}
