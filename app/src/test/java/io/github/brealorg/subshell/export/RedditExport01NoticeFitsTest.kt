package io.github.brealorg.subshell.export

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.ui.components.ViewportErrorTrayHost
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import io.github.brealorg.subshell.ui.noticeInfo
import io.github.brealorg.subshell.ui.screens.REDDIT_EXPORT_NOTICE_MESSAGE
import io.github.brealorg.subshell.ui.screens.REDDIT_EXPORT_NOTICE_TITLE
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * REDDITEXPORT01_EXPORT_YOUR_REDDIT_DATA_V1 (owner, after the phone check: the
 * export notice needed More before it said the ZIP holds private messages):
 * the notice fits the tray's two lines on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
// Native graphics measure text, so a message that needs More needs it here too.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RedditExport01NoticeFitsTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Test
    fun theExportNoticeFitsWithoutMore() {
        val host = ViewportErrorHostState()
        host.noticeInfo(REDDIT_EXPORT_NOTICE_TITLE, REDDIT_EXPORT_NOTICE_MESSAGE)
        composeRule.setContent {
            SubshellTheme { ViewportErrorTrayHost(state = host) }
        }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText(REDDIT_EXPORT_NOTICE_TITLE).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("More").fetchSemanticsNodes().size)
    }
}
