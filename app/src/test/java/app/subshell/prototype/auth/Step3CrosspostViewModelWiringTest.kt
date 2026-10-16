package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCrosspostSourceLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitter
import app.subshell.prototype.data.auth.reddit.CrosspostSourceLoadResult
import app.subshell.prototype.ui.CrosspostReviewPhase
import app.subshell.prototype.ui.CrosspostSourceEvidence
import app.subshell.prototype.ui.crosspost01A1CDraft
import app.subshell.prototype.ui.crosspost01A1CPolicy
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * STEP3 (crosspost01a1e): the ViewModel's crosspost review reads the source
 * and the destination rules for the active account and only asks; Publish of
 * the reviewed revision hands exactly one crosspost to the post submitter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3CrosspostViewModelWiringTest {
    private val sourceReads = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
    private val policyReads = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
    private val submits = Collections.synchronizedList(mutableListOf<AuthenticatedTextPostSubmitRequest>())

    private val directory = object : AccountSessionDirectory {
        override fun snapshot() = AccountSessionDirectorySnapshot(
            accounts = listOf(
                AccountSessionSummary(
                    stableId = "owner",
                    username = "crossposter",
                    source = AccountSessionSource.AUTHENTICATED,
                    state = AccountSessionState.CONNECTED,
                    providerLabel = "Reddit",
                ),
            ),
            activeAccountId = "owner",
        )

        override fun selectAccount(stableId: String) = snapshot()
    }

    private val viewModel = AuthConnectViewModel(
        ApplicationProvider.getApplicationContext<Application>(),
        unavailableAuthApplicationRuntime().copy(
            accountSessionDirectory = directory,
            crosspostSourceLoader = AuthenticatedCrosspostSourceLoader { account, fullname ->
                sourceReads += account to fullname
                CrosspostSourceLoadResult.Available(
                    CrosspostSourceEvidence("t3_source123", "source", "author", "Original title", false, false),
                )
            },
            composerPolicyLoader = AuthenticatedComposerPolicyLoader { account, community ->
                policyReads += account to community
                AuthenticatedComposerPolicyResult.Loaded(crosspost01A1CPolicy())
            },
            textPostSubmitter = AuthenticatedTextPostSubmitter { request ->
                submits += request
                AuthenticatedTextPostSubmitResult.Failed(kind = AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN)
            },
        ),
    )

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun aReviewOnlyReadsAndPublishSendsTheReviewedCrosspostOnce() {
        val draft = crosspost01A1CDraft()

        viewModel.reviewCrosspostDraft(draft)
        waitFor { viewModel.crosspostReviewUiState.value.phase == CrosspostReviewPhase.READY }

        assertEquals(CrosspostReviewPhase.READY, viewModel.crosspostReviewUiState.value.phase)
        assertEquals(listOf("owner" to "t3_source123"), sourceReads.toList())
        assertEquals(listOf("owner" to "target"), policyReads.toList())
        assertEquals(emptyList<AuthenticatedTextPostSubmitRequest>(), submits.toList())

        viewModel.publishReviewedCrosspost(draft, viewModel.crosspostReviewUiState.value.revision)
        waitFor { submits.isNotEmpty() }

        assertEquals(listOf("t3_source123"), submits.map { it.crosspostFullname })
        assertEquals(listOf("target"), submits.map { it.community })
    }
}
