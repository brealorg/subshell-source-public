package io.github.brealorg.subshell.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.brealorg.subshell.data.FakeSubshellRepository
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.ui.PostLayout
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #295 (owner: the ⋮ menu was too long): your own post's Edit, Manage and
 * Delete are one "Your post…" entry opening a sheet
 * (ISSUE295_YOUR_POST_ACTIONS_IN_ONE_SHEET_V1), and "Go to r/…" / "About u/…"
 * are offered only where the card cannot open them itself
 * (ISSUE295_MENU_OFFERS_ONLY_WHAT_THE_CARD_DOES_NOT_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue295PostMenuComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun post(author: String) = Post(
        id = "t3_abc",
        community = "android",
        author = author,
        age = "1h",
        title = "A post",
        body = "Some text",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    private fun state() = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
    )

    private fun exists(text: String) =
        composeRule.onAllNodesWithTextCount(text) > 0

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    @Test
    fun theCardSaysWhereItOpensTheCommunityAndTheAuthor() {
        assertTrue(postLayoutOpensCommunity(PostLayout.LARGE))
        assertTrue(postLayoutOpensAuthor(PostLayout.LARGE))
        assertTrue(postLayoutOpensCommunity(PostLayout.COMPACT))
        assertFalse(postLayoutOpensAuthor(PostLayout.COMPACT))
        assertFalse(postLayoutOpensCommunity(PostLayout.LIST))
        assertFalse(postLayoutOpensAuthor(PostLayout.GRID))
    }

    @Test
    fun yourOwnPostHasOneEntryAndTheSheetHoldsEditManageAndDelete() {
        var deletes = 0
        var manages = 0
        composeRule.setContent {
            SubshellTheme {
                PostCard(
                    post = post("me_user"),
                    state = state(),
                    onOpen = {},
                    onManagePost = { manages++ },
                    onDeletePost = { deletes++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Post actions").performClick()
        composeRule.onNodeWithText("Your post…").assertIsDisplayed()
        assertFalse(exists("Manage post"))
        assertFalse(exists("Delete post"))
        // The Large card opens the community and the author from its header.
        assertFalse(exists("Go to r/android"))
        assertFalse(exists("About u/me_user"))

        composeRule.onNodeWithText("Your post…").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit post").assertIsDisplayed()
        composeRule.onNodeWithText("Manage post").assertIsDisplayed()
        composeRule.onNodeWithText("Delete post").performClick()
        composeRule.waitForIdle()
        assertEquals(1, deletes)
        assertEquals(0, manages)
        assertFalse(exists("Manage post"))
    }

    @Test
    fun someoneElsesPostHasNoYourPostEntryAndListKeepsTheWays() {
        composeRule.setContent {
            SubshellTheme {
                PostCard(post = post("alice"), state = state(), onOpen = {}, layout = PostLayout.LIST)
            }
        }
        composeRule.onNodeWithContentDescription("Post actions").performClick()
        composeRule.waitForIdle()
        assertFalse(exists("Your post…"))
        composeRule.onNodeWithText("Go to r/android").assertIsDisplayed()
        composeRule.onNodeWithText("About u/alice").assertIsDisplayed()
    }

    @Test
    fun compactKeepsOnlyTheAuthor() {
        composeRule.setContent {
            SubshellTheme {
                PostCard(post = post("alice"), state = state(), onOpen = {}, layout = PostLayout.COMPACT)
            }
        }
        composeRule.onNodeWithContentDescription("Post actions").performClick()
        composeRule.waitForIdle()
        assertFalse(exists("Go to r/android"))
        composeRule.onNodeWithText("About u/alice").assertIsDisplayed()
    }
}
