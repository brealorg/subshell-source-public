package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.SubmittedTextPost
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.CreatePostScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMPOSER03 (ui review round 3 P2): a text/link/media post success removes
 * the sent draft wherever it is and only opens the post from an open composer
 * showing that draft under the same account; the composer is locked while the
 * post is in flight, so what was sent always matches the draft.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Composer03PostSuccessComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val writer = account("writer")
    private val other = account("other")

    private fun account(name: String) = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:t2_$name",
        username = name,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun state() = SubshellState(
        initialCommentActionPlacement = CommentActionPlacement.RIGHT,
        initialAccountSessions = listOf(writer, other),
        initialActiveAccountId = writer.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun sentDraft(state: SubshellState, title: String = "Sent post"): PostComposerDraft {
        state.openPostComposer()
        state.updatePostComposerDraft(
            state.postComposerDraft.copy(community = "subshell", title = title, body = "Body"),
        )
        return state.postComposerDraft
    }

    private fun requestKey(draft: PostComposerDraft) = TextPostWriteRequest(
        accountStableId = writer.stableId,
        community = draft.community,
        title = draft.title,
        text = draft.body,
    ).requestKey

    private fun complete(state: SubshellState, draft: PostComposerDraft) =
        state.completeRemoteTextPostComposer(
            writer.stableId,
            requestKey(draft),
            SubmittedTextPost(
                "t3_proof1", "proof1", "subshell", draft.title, draft.body,
                "/r/subshell/comments/proof1/post/",
            ),
        )

    @Test
    fun aSuccessAfterCloseRemovesTheSavedDraftAndDoesNotNavigate() {
        val state = state()
        val draft = sentDraft(state)
        state.closePostComposer()
        val routeBefore = state.currentNavigationRoute
        assertTrue(state.postComposerDrafts.any { it.id == draft.id })

        complete(state, draft)

        assertFalse(state.postComposerDrafts.any { it.id == draft.id })
        assertEquals(routeBefore, state.currentNavigationRoute)
        assertNull(state.selectedPost)
    }

    @Test
    fun aSuccessWhileAnotherDraftIsOpenKeepsThatDraftAndStays() {
        val state = state()
        val draft = sentDraft(state)
        state.closePostComposer()
        val otherDraft = sentDraft(state, title = "Another post")

        complete(state, draft)

        assertFalse(state.postComposerDrafts.any { it.id == draft.id })
        assertEquals(otherDraft.id, state.postComposerDraft.id)
        assertEquals("Another post", state.postComposerDraft.title)
        assertTrue(state.currentNavigationRoute is SubshellRoute.PostComposer)
    }

    @Test
    fun aSuccessAfterAnAccountSwitchRemovesTheSendersDraftAndDoesNotNavigate() {
        val state = state()
        val draft = sentDraft(state)
        state.closePostComposer()
        state.switchAccount(other.stableId)
        val routeBefore = state.currentNavigationRoute

        complete(state, draft)

        assertFalse(state.postComposerDrafts.any { it.id == draft.id })
        assertEquals(routeBefore, state.currentNavigationRoute)
        assertNull(state.selectedPost)
    }

    @Test
    fun aSuccessInTheOpenComposerStillOpensThePost() {
        val state = state()
        val draft = sentDraft(state)

        complete(state, draft)

        assertFalse(state.postComposerDrafts.any { it.id == draft.id })
        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)
        assertEquals("proof1", state.selectedPost?.id)
    }

    @Test
    fun theComposerIsLockedWhileThePostIsInFlight() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = state()
        val draft = sentDraft(state)
        val sending = TextPostWriteUiState(
            phase = TextPostWritePhase.SENDING,
            accountStableId = writer.stableId,
            requestKey = requestKey(draft),
        )
        composeRule.setContent {
            SubshellTheme { CreatePostScreen(state = state, textPostWriteState = sending) }
        }
        composeRule.waitForIdle()

        // LOWUX1_COMPOSER_INPUT_LOCKED_WHILE_SENDING_V1 (OCB-205): the title field
        // is disabled while the post is sent (it took typing that vanished).
        composeRule.onNode(hasText("Sent post") and hasText("Title")).assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction() and hasText("Body")).performTextInput(" more")
        composeRule.waitForIdle()

        assertEquals("Sent post", state.postComposerDraft.title)
        assertEquals("Body", state.postComposerDraft.body)
    }
}
