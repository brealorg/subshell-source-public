package app.subshell.prototype.ui.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE83_CROSSPOST_MEDIA_KEEPS_ITS_OWN_SENSITIVITY_V1: a crosspost Reddit
 * marked NSFW or spoiler on its own showed its source's media uncovered.
 * ISSUE84_OPAQUE_SENSITIVE_COVER_WITHOUT_BLUR_V1: below Android 12 blur does
 * nothing, so the veil over a preloaded sensitive image must be opaque.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue84And83SensitiveMediaComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private fun crosspost(nsfw: Boolean = false, spoiler: Boolean = false) = Post(
        id = "t3_crosspost83",
        community = "nsfwcommunity",
        author = "crossposter",
        age = "1h",
        title = "A crosspost",
        score = 1,
        comments = 0,
        kind = PostKind.CROSSPOST,
        nsfw = nsfw,
        spoiler = spoiler,
        crosspost = CrosspostPreview(
            community = "pics",
            author = "photographer",
            title = "A safe-for-work photo",
            score = 10,
            id = "t3_source83",
            kind = PostKind.IMAGE,
            remoteImageUri = "https://i.redd.it/source83.jpg",
        ),
    )

    private fun coverCount(post: Post, text: String): Int {
        val state = SubshellState(initialShowNsfwPosts = true)
        composeRule.setContent { SubshellTheme { PostCard(post = post, state = state, onOpen = {}) } }
        composeRule.waitForIdle()
        return composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size
    }

    @Test
    fun anNsfwCrosspostCoversItsSourcesMedia() {
        assertEquals(1, coverCount(crosspost(nsfw = true), "Sensitive media · tap to reveal"))
    }

    @Test
    fun aSpoilerCrosspostCoversItsSourcesMedia() {
        assertEquals(1, coverCount(crosspost(spoiler = true), "Spoiler · tap to reveal"))
    }

    @Test
    fun aPlainCrosspostOfAPlainSourceIsNotCovered() {
        assertEquals(0, coverCount(crosspost(), "Sensitive media · tap to reveal"))
    }

    @Test
    fun withoutBlurThePreloadedCoverIsOpaque() {
        assertEquals(1f, preloadedSensitiveCoverAlpha(sdkInt = 26))
        assertEquals(1f, preloadedSensitiveCoverAlpha(sdkInt = 30))
        assertEquals(0.52f, preloadedSensitiveCoverAlpha(sdkInt = 31))
        assertEquals(0.52f, preloadedSensitiveCoverAlpha(sdkInt = 36))
    }
}
