package app.subshell.prototype.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.filteredPostsSummaryText
import app.subshell.prototype.ui.screens.CustomFeedBrowser
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #30 / CONTENTFILTER04: an optional row (off by default) says how
 * many posts the filters hide in a feed, and can show them for the visit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ContentFilter04SummaryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun post(id: String, title: String) = Post(
        id = "t3_$id",
        community = "movies",
        author = "someone",
        age = "1h",
        title = title,
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    @Test
    fun theSettingIsOffByDefaultPortableAndPersisted() {
        assertFalse(SubshellSettings().filteredPostsSummary)
        assertTrue(SubshellSettings.FILTERED_POSTS_SUMMARY in SubshellSettings.storageKeys)
        assertTrue(
            SubshellSettings.fromStorageMap(SubshellSettings(filteredPostsSummary = true).toStorageMap())
                .filteredPostsSummary,
        )
        val persisted = mutableMapOf<String, String>()
        val state = SubshellState(persistPreference = { key, value -> persisted[key] = value })
        state.updateFilteredPostsSummary(true)
        assertTrue(state.filteredPostsSummary)
        assertEquals("true", persisted[SubshellSettings.FILTERED_POSTS_SUMMARY])
    }

    @Test
    fun theRowTextCountsPosts() {
        assertEquals("1 post hidden by your filters", filteredPostsSummaryText(1, revealed = false))
        assertEquals("3 posts hidden by your filters", filteredPostsSummaryText(3, revealed = false))
        assertEquals("Showing 3 posts your filters hide", filteredPostsSummaryText(3, revealed = true))
    }

    @Test
    fun withTheSettingOffThereIsNoRow() {
        render(stateWithSpoilerFilter(summary = false))

        assertEquals(0, composeRule.onAllNodesWithText("hidden by your filters", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("A spoiler inside").fetchSemanticsNodes().size)
    }

    @Test
    fun withTheSettingOnTheRowCountsAndShowRevealsTheFilteredPosts() {
        render(stateWithSpoilerFilter(summary = true))

        composeRule.onNodeWithText("1 post hidden by your filters").assertExists()
        assertEquals(0, composeRule.onAllNodesWithText("A spoiler inside").fetchSemanticsNodes().size)

        composeRule.onNodeWithText("Show").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Showing 1 post your filters hide").assertExists()
        assertEquals(1, composeRule.onAllNodesWithText("A spoiler inside").fetchSemanticsNodes().size)

        composeRule.onNodeWithText("Hide").performClick()
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText("A spoiler inside").fetchSemanticsNodes().size)
    }

    // CONTENTFILTER06: Show is remembered per account, not only per feed.
    @Test
    fun anAccountSwitchHidesTheRevealedPostsAgain() {
        val account = mutableStateOf("reddit:me")
        render(stateWithSpoilerFilter(summary = true)) { account.value }

        composeRule.onNodeWithText("Show").performClick()
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithText("A spoiler inside").fetchSemanticsNodes().size)

        account.value = "reddit:other"
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1 post hidden by your filters").assertExists()
        assertEquals(0, composeRule.onAllNodesWithText("A spoiler inside").fetchSemanticsNodes().size)
    }

    private fun stateWithSpoilerFilter(summary: Boolean): SubshellState =
        SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT).apply {
            addContentFilter(ContentFilterType.WORD, "spoiler")
            updateFilteredPostsSummary(summary)
        }

    private fun render(state: SubshellState, account: () -> String = { "reddit:me" }) {
        val feed = CustomFeedSummary(
            path = "/user/me/m/films/",
            displayName = "films",
            description = "",
            visibility = "private",
            subredditNames = listOf("movies"),
        )
        composeRule.setContent {
            SubshellTheme {
                CustomFeedBrowser(
                    modifier = androidx.compose.ui.Modifier,
                    state = state,
                    accountStableId = account(),
                    feed = feed,
                    postsState = CustomFeedPostsUiState(
                        accountStableId = account(),
                        feedPath = feed.path,
                        posts = listOf(post("keep", "Keep this one"), post("word", "A spoiler inside")),
                    ),
                    contentPadding = PaddingValues(),
                    onBack = {},
                    onRefresh = {},
                    onLoadInitial = {},
                    onAppend = {},
                )
            }
        }
        composeRule.waitForIdle()
    }
}
