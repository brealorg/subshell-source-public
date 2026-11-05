package io.github.brealorg.subshell.ui.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.brealorg.subshell.data.account.ContentSafetyFailureKind
import io.github.brealorg.subshell.data.account.ContentSafetyOperation
import io.github.brealorg.subshell.data.account.ContentSafetyPhase
import io.github.brealorg.subshell.data.account.ContentSafetyUiState
import io.github.brealorg.subshell.data.account.PostFollowFailureKind
import io.github.brealorg.subshell.data.account.PostFollowPhase
import io.github.brealorg.subshell.data.account.PostFollowUiState
import io.github.brealorg.subshell.data.account.PostSaveFailureKind
import io.github.brealorg.subshell.data.account.PostSavePhase
import io.github.brealorg.subshell.data.account.PostSaveUiState
import io.github.brealorg.subshell.data.account.PostVoteFailureKind
import io.github.brealorg.subshell.data.account.PostVotePhase
import io.github.brealorg.subshell.data.account.PostVoteUiState
import io.github.brealorg.subshell.ui.PostFollowRuntimeEntryPoints
import io.github.brealorg.subshell.ui.error.LocalViewportErrorHostState
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
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
