package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostEditUiState
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.RedditPeopleSearchItem
import app.subshell.prototype.ui.components.MENTION_SEARCH_DEBOUNCE_MS
import app.subshell.prototype.ui.screens.CreatePostScreen
import app.subshell.prototype.ui.screens.PostEditScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE229_POST_BODY_MENTIONS_V1 (owner 2026-10-16): a post body suggests people
 * from the active account's Reddit people search while `@` or `u/` is typed,
 * in Create post and in a post edit; a tap writes `u/name `.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue229PostBodyMentionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val searches = mutableListOf<String>()

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun answer(accountStableId: String) = RedditPeopleSearchUiState(
        accountStableId = accountStableId,
        searchQuery = "ca",
        items = listOf(RedditPeopleSearchItem(username = "carol", title = "carol", description = "")),
        loaded = true,
    )

    private fun editPost(body: String, search: RedditPeopleSearchUiState) {
        val post = SampleData.posts.first { it.kind == PostKind.TEXT }.copy(id = "t3_abc123", body = body)
        val state = signedIn()
        composeRule.setContent {
            SubshellTheme {
                PostEditScreen(
                    post = post,
                    state = state,
                    editState = PostEditUiState(),
                    onSubmitEdit = {},
                    onAuthorizeEdit = {},
                    onCancelAuthorization = {},
                    onResetEditState = {},
                    onSaved = {},
                    onClose = {},
                    mentionSearchState = search,
                    onSearchMentions = { searches += it },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun shown(name: String) =
        composeRule.onAllNodes(hasContentDescription("Mention u/$name")).fetchSemanticsNodes().size

    @Test
    fun aPostEditAsksRedditAndWritesTheChosenMention() {
        editPost("Thanks u/ca", answer(account.stableId))

        composeRule.mainClock.advanceTimeBy(MENTION_SEARCH_DEBOUNCE_MS + 100L)
        composeRule.waitForIdle()
        assertEquals(listOf("ca"), searches)
        assertEquals(1, shown("carol"))

        composeRule.onNodeWithContentDescription("Mention u/carol").performClick()
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodes(hasText("Thanks u/carol ")).fetchSemanticsNodes().size)
        assertEquals(0, shown("carol"))
    }

    @Test
    fun anAnswerForAnotherAccountIsNeverShown() {
        editPost("Thanks u/ca", answer("reddit:someone-else"))
        assertEquals(0, shown("carol"))
    }

    @Test
    fun createPostOffersTheMentionAboveTheDock() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = signedIn()
        state.openPostComposer()
        state.updatePostComposerDraft(PostComposerDraft(community = "subshell", title = "A post", body = "Hello @ca"))
        composeRule.setContent {
            SubshellTheme {
                CreatePostScreen(
                    state = state,
                    mentionSearchState = answer(account.stableId),
                    onSearchMentions = { searches += it },
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(1, shown("carol"))
        composeRule.onNodeWithContentDescription("Mention u/carol").performClick()
        composeRule.waitForIdle()
        assertEquals("Hello u/carol ", state.postComposerDraft.body)
    }
}
