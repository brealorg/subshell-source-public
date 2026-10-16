package app.subshell.prototype.ui.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.data.account.ContentSafetyFailureKind
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.PostFollowFailureKind
import app.subshell.prototype.data.account.PostFollowPhase
import app.subshell.prototype.data.account.PostFollowUiState
import app.subshell.prototype.data.account.PostSaveFailureKind
import app.subshell.prototype.data.account.PostSavePhase
import app.subshell.prototype.data.account.PostSaveUiState
import app.subshell.prototype.data.account.PostVoteFailureKind
import app.subshell.prototype.data.account.PostVotePhase
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.ui.PostFollowRuntimeEntryPoints
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** #214: the vote, save, follow and content-safety failures reach the error tray. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RuntimeErrorReportsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val host = ViewportErrorHostState()
    private val vote = mutableStateOf(PostVoteUiState())
    private val save = mutableStateOf(PostSaveUiState())
    private val follow = mutableStateOf(PostFollowUiState())
    private val safety = mutableStateOf(ContentSafetyUiState())
    private val retries = mutableListOf<String>()

    private fun show() {
        composeRule.setContent {
            CompositionLocalProvider(LocalViewportErrorHostState provides host) {
                RuntimeErrorReports(
                    postVoteState = vote.value,
                    onRetryPostVote = { retries += "vote" },
                    postSaveState = save.value,
                    onRetryPostSave = { retries += "save" },
                    postFollowRuntime = PostFollowRuntimeEntryPoints(
                        state = follow.value,
                        onRetry = { retries += "follow" },
                    ),
                    contentSafetyState = safety.value,
                    onRetryContentSafety = { retries += "safety" },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun nothingIsReportedWhileNothingFailed() {
        show()

        assertNull(host.currentRequest)
    }

    @Test
    fun aFailedVoteIsReportedWithItsRetry() {
        vote.value = PostVoteUiState(
            phase = PostVotePhase.FAILED,
            postFullname = "t3_a",
            failureKind = PostVoteFailureKind.entries.first(),
            retryable = true,
        )
        show()

        assertEquals("reddit-post-vote:t3_a", host.currentRequest?.scopeKey)
        host.retryCurrent()
        assertEquals(listOf("vote"), retries)
    }

    @Test
    fun aFailedSaveThatCannotBeRetriedHasNoRetry() {
        save.value = PostSaveUiState(
            phase = PostSavePhase.FAILED,
            postFullname = "t3_a",
            failureKind = PostSaveFailureKind.entries.first(),
            retryable = false,
        )
        show()

        assertEquals("reddit-post-save:t3_a", host.currentRequest?.scopeKey)
        assertNull(host.currentRequest?.onRetry)
    }

    @Test
    fun aFailedFollowRetriesThroughTheFollowRuntime() {
        follow.value = PostFollowUiState(
            phase = PostFollowPhase.FAILED,
            postFullname = "t3_a",
            failureKind = PostFollowFailureKind.entries.first(),
            retryable = true,
        )
        show()

        assertEquals("reddit-post-follow:t3_a", host.currentRequest?.scopeKey)
        host.retryCurrent()
        assertEquals(listOf("follow"), retries)
    }

    @Test
    fun aFailedSafetyActionIsScopedByThingAndOperation() {
        val operation = ContentSafetyOperation.entries.first()
        safety.value = ContentSafetyUiState(
            phase = ContentSafetyPhase.FAILED,
            operation = operation,
            thingFullname = "t3_a",
            failureKind = ContentSafetyFailureKind.entries.first(),
            retryable = true,
        )
        show()

        assertEquals("reddit-content-safety:t3_a:${operation.name}", host.currentRequest?.scopeKey)
        host.retryCurrent()
        assertEquals(listOf("safety"), retries)
    }

    @Test
    fun aFailureThatEndsIsClearedFromTheTray() {
        vote.value = PostVoteUiState(
            phase = PostVotePhase.FAILED,
            postFullname = "t3_a",
            failureKind = PostVoteFailureKind.entries.first(),
            retryable = true,
        )
        show()
        assertNotNull(host.currentRequest)

        vote.value = PostVoteUiState()
        composeRule.waitForIdle()

        assertNull(host.currentRequest)
    }
}
