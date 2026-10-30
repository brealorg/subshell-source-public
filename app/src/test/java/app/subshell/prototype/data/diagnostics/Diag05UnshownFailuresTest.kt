package app.subshell.prototype.data.diagnostics

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.RedditAccountHistoryRequestCoordinator
import app.subshell.prototype.auth.RedditNavigationTargetCoordinator
import app.subshell.prototype.auth.RedditSavedHistoryRequestCoordinator
import app.subshell.prototype.data.account.OnlineVisibilityResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxTargetResult
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.reddit.toOnlineVisibilityResult
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.error.ReportFailureDiagnostic
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DIAG05 (owner 2026-10-18, the DIAG04 leftovers): failures shown only as
 * plain text or a notice card, and failures that reach no screen (a post
 * looked up behind a link, the sign-in's identity read), are recorded with
 * what Reddit answered, the field a decode failed on included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Diag05UnshownFailuresTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val brokenAnswer = FailureDetails(
        retryable = false,
        protocolKind = NetworkProtocolFailureKind.MALFORMED_JSON,
        jsonPath = app.subshell.prototype.data.error.safeJsonPathText("$.data.children[0].data.title"),
    )

    @After
    fun tearDown() {
        scope.cancel()
        File(application.noBackupFilesDir, "diagnostics").deleteRecursively()
    }

    private fun events() = DiagnosticEventStore(application).snapshot().events

    @Test
    fun aNoticeCardFailureIsRecordedWithItsDetailsOnlyWhileShown() {
        var shown by mutableStateOf(false)
        composeRule.setContent {
            ReportFailureDiagnostic(scopeKey = "moderator_log", active = shown, failureDetails = brokenAnswer)
        }
        composeRule.waitForIdle()
        assertTrue(events().isEmpty())

        shown = true
        composeRule.waitForIdle()
        // The diagnostic is written on Dispatchers.IO, which waitForIdle does not
        // wait for: under full-suite load the store could still be empty here.
        val deadline = System.currentTimeMillis() + 5_000
        while (events().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        val event = events().single()
        assertEquals(DiagnosticOperation.INLINE_ERROR_PRESENTED, event.operation)
        assertEquals("moderator_log", event.action)
        assertEquals("$.data.children[0].data.title", event.failure?.jsonPath)
    }

    @Test
    fun aFailedLinkLookupIsRecordedButAMissingPostIsNot() {
        var answer: AuthenticatedRedditInboxTargetResult = AuthenticatedRedditInboxTargetResult.NotFound
        val recorded = mutableListOf<Pair<String, FailureDetails?>>()
        val coordinator = RedditNavigationTargetCoordinator(
            scope = scope,
            inboxLoader = object : AuthenticatedRedditInboxLoader {
                override suspend fun cached(accountStableId: String) = error("unused")
                override suspend fun synchronize(accountStableId: String) = error("unused")
                override suspend fun append(accountStableId: String, after: String): AuthenticatedRedditInboxResult = error("unused")
                override suspend fun resolveTargetPost(accountStableId: String, postId: String) = answer
            },
            commentLoader = null,
            redditAccountHistoryCoordinator = RedditAccountHistoryRequestCoordinator(scope = scope, updater = null),
            redditSavedHistoryCoordinator = RedditSavedHistoryRequestCoordinator(scope = scope, loader = null),
            recordLookupFailure = { scopeKey, details -> recorded += scopeKey to details },
        )
        var resolved: Post? = app.subshell.prototype.data.SampleData.posts.first()

        coordinator.resolveRedditNavigationTargetPost("reddit:me", "abc", onResolved = { resolved = it })
        assertNull(resolved)
        assertTrue(recorded.isEmpty())

        answer = AuthenticatedRedditInboxTargetResult.Failed(
            kind = AuthenticatedRedditInboxFailureKind.PROTOCOL,
            retryable = false,
            details = brokenAnswer,
        )
        coordinator.resolveRedditNavigationTargetPost("reddit:me", "abc", onResolved = { resolved = it })
        assertEquals(listOf("link_target_post" to brokenAnswer), recorded)
    }

    @Test
    fun anUnreadableOnlineVisibilityAnswerKeepsItsField() {
        val failed = NetworkResult.ProtocolFailure(NetworkProtocolFailureKind.MALFORMED_JSON, "$.show_presence")
            .toOnlineVisibilityResult() as OnlineVisibilityResult.Failed
        assertEquals("$.show_presence", failed.failureDetails?.jsonPath?.value)
    }
}
