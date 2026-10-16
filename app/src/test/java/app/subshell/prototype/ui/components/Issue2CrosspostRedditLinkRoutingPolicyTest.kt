package app.subshell.prototype.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue2CrosspostRedditLinkRoutingPolicyTest {
    @Test
    fun directAndWrappedRedditStaticImagesResolveToTheInternalImageTarget() {
        val image = "https://i.redd.it/x4rwazxdswjh1.jpeg"

        assertEquals(image, redditStaticImageTargetOrNull(image))
        assertEquals(
            image,
            redditStaticImageTargetOrNull(
                "https://www.reddit.com/media?url=" +
                    "https%3A%2F%2Fi.redd.it%2Fx4rwazxdswjh1.jpeg",
            ),
        )
    }

    @Test
    fun staticImageTargetRejectsExternalInsecureAndNonStaticSources() {
        assertNull(redditStaticImageTargetOrNull("https://example.com/image.jpg"))
        assertNull(redditStaticImageTargetOrNull("http://i.redd.it/image.jpg"))
        assertNull(redditStaticImageTargetOrNull("https://i.redd.it/animated.gif"))
        assertNull(
            redditStaticImageTargetOrNull(
                "https://www.reddit.com/media?url=https%3A%2F%2Fevil.example%2Fimage.jpg",
            ),
        )
    }

    @Test
    fun shRedditLinksAreNormalizedBeforeCanonicalRedditRouting() {
        assertEquals(
            "https://www.reddit.com/r/AlignmentChartFills/comments/1vqo1it/title/",
            normalizedRedditShareUrlOrNull(
                "https://sh.reddit.com/r/AlignmentChartFills/comments/1vqo1it/title/",
            ),
        )
        assertNull(normalizedRedditShareUrlOrNull("https://example.com/r/test"))
    }

    @Test
    fun crosspostMarkdownUsesExplicitSubshellRoutingWithExternalFallback() {
        val markdown = File(
            "src/main/java/app/subshell/prototype/ui/components/MarkdownText.kt",
        ).readText()
        val media = File(
            "src/main/java/app/subshell/prototype/ui/components/MediaComponents.kt",
        ).readText()

        assertTrue(markdown.contains("onLinkClick: ((String) -> Unit)? = null"))
        assertTrue(markdown.contains("LinkInteractionListener"))
        assertTrue(markdown.contains("linkInteractionListener = onLinkClick?.let"))

        assertTrue(media.contains("fun openCrosspostBodyLink(rawUrl: String)"))
        assertFalse(media.contains("import okhttp3."))
        assertTrue(media.contains("redditStaticImageTargetOrNull(canonicalUrl)"))
        assertTrue(media.contains("normalizedRedditShareUrlOrNull(rawUrl)"))
        assertTrue(media.contains("state.openRedditTarget("))
        assertTrue(media.contains("state.openMedia("))
        assertTrue(media.contains("onLinkClick = ::openCrosspostBodyLink"))
        assertTrue(media.contains("runCatching { uriHandler.openUri(rawUrl) }"))
    }
}
