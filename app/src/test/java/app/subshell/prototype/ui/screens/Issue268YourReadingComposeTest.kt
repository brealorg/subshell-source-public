package app.subshell.prototype.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.subshell.prototype.data.reading.READING_STATS_SUGGESTION_MIN_DAYS
import app.subshell.prototype.data.reading.ReadingStats
import app.subshell.prototype.data.reading.ReadingStatsStore
import app.subshell.prototype.ui.SubshellState
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE268_YOUR_READING_PAGE_V1 / ISSUE268_SUGGESTIONS_HIDE_OR_LEAVE_V1 (#268):
 * the page offers to turn counting on, shows the time per community, and
 * after 14 days lists subscriptions never opened with Hide and Leave.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h1800dp")
class Issue268YourReadingComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var day = 20_000L
    private val store = ReadingStatsStore(Files.createTempDirectory("reading").toFile(), today = { day })

    @After
    fun tearDown() {
        ReadingStats.installForTest(null)
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theOffPageTurnsCountingOn() {
        store.load()
        ReadingStats.installForTest(store)
        composeRule.setContent { ReadingStatsPanel(SubshellState()) }

        composeRule.onNodeWithText("Turn on").performClick()
        waitForText("Nothing counted yet")

        assertEquals(true, store.enabled.value)
    }

    @Test
    fun timePerCommunityAndSuggestionsWithHideAndLeave() {
        store.setEnabled(true)
        day += READING_STATS_SUGGESTION_MIN_DAYS
        val state = SubshellState()
        store.record(state.activeAccountId, "pics", opens = 2, postOpens = 3, seconds = 125)
        ReadingStats.installForTest(store)
        val left = mutableListOf<String>()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalReadingStatsCommunities provides ReadingStatsCommunities(
                    subscribedNames = listOf("pics", "news", "kotlin"),
                    onLeave = { left += it },
                ),
            ) {
                ReadingStatsPanel(state)
            }
        }
        waitForText("r/pics")

        composeRule.onNodeWithText("2 min · 2 visits · 3 posts").assertExists()
        composeRule.onNodeWithText("r/news").assertExists()
        composeRule.onNodeWithText("r/kotlin").assertExists()
        assertTrue(composeRule.onAllNodesWithText("Hide").fetchSemanticsNodes().size == 2)

        composeRule.onAllNodesWithText("Leave")[1].performClick()
        assertEquals(listOf("news"), left)

        composeRule.onAllNodesWithText("Hide")[0].performClick()
        composeRule.waitForIdle()
        assertTrue(state.isCommunityHidden("kotlin"))
        assertTrue(composeRule.onAllNodesWithText("r/kotlin").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun suggestionsWaitForFourteenDays() {
        store.setEnabled(true)
        day += 4
        ReadingStats.installForTest(store)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalReadingStatsCommunities provides ReadingStatsCommunities(subscribedNames = listOf("news")),
            ) {
                ReadingStatsPanel(SubshellState())
            }
        }

        waitForText("Shown after 14 days of counting (9 to go)")
        assertTrue(composeRule.onAllNodesWithText("r/news").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun durationsReadLikeAClock() {
        assertEquals("under a minute", readingDurationLabel(59))
        assertEquals("12 min", readingDurationLabel(12 * 60 + 30))
        assertEquals("2 h", readingDurationLabel(120 * 60))
        assertEquals("1 h 5 min", readingDurationLabel(65 * 60))
    }
}
