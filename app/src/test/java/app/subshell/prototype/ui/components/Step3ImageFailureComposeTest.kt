package app.subshell.prototype.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (error01a3): an image that cannot be loaded says so with a way to
 * retry; where space is tight (composer previews, flair) it shows only a
 * quiet "image unavailable" mark; an author avatar that cannot be loaded
 * falls back to the author's letter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ImageFailureComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    // A link with credentials is refused before any network use, so the load
    // fails at once (plain http is tried over https since #228).
    private val refused = "http://user:secret@insecure.example/picture.png"

    private fun shownText(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun aFailedImageSaysSoAndABlockedOneOffersTheBrowserNotRetry() {
        composeRule.setContent { SubshellTheme { RemoteImage(uri = refused, label = "Picture") } }
        composeRule.waitForIdle()

        assertEquals(1, shownText("Couldn't load image"))
        // #228: a policy refusal can never pass, so it offers the browser instead
        // of Retry (Retry for a failure that can pass: Issue228MediaPolicyTest).
        assertEquals(0, shownText("Retry"))
        assertEquals(1, shownText("Open in browser"))
    }

    @Test
    fun aCompactFailedImageShowsOnlyAQuietMark() {
        composeRule.setContent { SubshellTheme { RemoteImage(uri = refused, label = "Picture", showFailureDetails = false) } }
        composeRule.waitForIdle()

        assertEquals(0, shownText("Couldn't load image"))
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Image unavailable").fetchSemanticsNodes().size)
    }

    @Test
    fun anAvatarThatCannotLoadFallsBackToTheLetter() {
        composeRule.setContent {
            SubshellTheme { CommentAuthorAvatar(author = "alice", accent = Color.Blue, avatarUri = refused) }
        }
        composeRule.waitForIdle()

        assertEquals(1, shownText("A"))
    }
}
