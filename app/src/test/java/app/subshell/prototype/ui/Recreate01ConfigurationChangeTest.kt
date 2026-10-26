package app.subshell.prototype.ui

import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.PostSaveCoordinator
import app.subshell.prototype.auth.PostVoteCoordinator
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.PostFollowPhase
import app.subshell.prototype.data.account.PostFollowUiState
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.PostVotePhase
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostSaveResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostVoteResult
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.app.InboxNotificationPermissionEffect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RECREATE01 (#241): a dark-mode switch, font size, language or an attached
 * keyboard recreate MainActivity (configChanges covers size and orientation
 * only). SubshellState is then rebuilt from its saved process snapshot by
 * rememberSubshellState, as StateRestorationTester does here; the ViewModel
 * coordinators (vote, save) survive and hand the rebuilt state their newest
 * ui state again, as RuntimeStateBridge does. Each test is one thing a
 * reader expects to find unchanged after the switch.
 *
 * Since #245 a recreated activity keeps the state its ViewModelStore holds
 * (Issue245StateSurvivesConfigurationChangesTest), so the snapshot is read
 * only in a new process; [recreate] gives the rebuild a new store, as process
 * death does. The rules stay the same: what the snapshot carries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Recreate01ConfigurationChangeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:reader",
        username = "reader",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val postA = SampleData.posts.first().copy(id = "aaa111", score = 10, vote = 0, saved = false)
    private val postB = SampleData.posts.first().copy(id = "bbb222", score = 20, vote = 0, saved = false)

    private val otherAccount = account.copy(stableId = "reddit:other", username = "other")

    private inner class Accounts : AccountSessionDirectory {
        var activeAccountId = account.stableId
        override fun snapshot() = AccountSessionDirectorySnapshot(listOf(account, otherAccount), activeAccountId)
        override fun selectAccount(stableId: String) = snapshot().also { activeAccountId = stableId }
    }

    private class Process : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private class Harness(
        val restoration: StateRestorationTester,
        val directory: AccountSessionDirectory,
        val newProcess: () -> Unit,
        private val current: () -> SubshellState,
    ) {
        val state: SubshellState get() = current()
    }

    private fun harness(): Harness {
        val repository = FakeSubshellRepository()
        val directory = Accounts()
        var state: SubshellState? = null
        // A plain variable: the running composition must not see the new
        // store; only the rebuild does, as a new process would.
        var process = Process()
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            CompositionLocalProvider(LocalSubshellStateStoreOwner provides process) {
                state = rememberSubshellState(repository = repository, accountSessionDirectory = directory)
            }
        }
        composeRule.waitForIdle()
        return Harness(restoration, directory, newProcess = { process = Process() }) { requireNotNull(state) }
    }

    private fun Harness.recreate(): SubshellState {
        val before = state
        newProcess()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        val after = state
        assertTrue("the state was not rebuilt", before !== after)
        return after
    }

    // A reply is written on an open post; the composer comes back only there.
    @Test
    fun aHalfWrittenCommentReplySurvives() {
        val harness = harness()
        val storedPost = SampleData.posts.first()
        composeRule.runOnIdle {
            val state = harness.state
            state.open(storedPost)
            assertTrue(state.openCommentComposer(storedPost))
            state.updateCommentComposerDraft(
                requireNotNull(state.commentComposerDraft).copy(body = "half a reply"),
            )
        }

        val rebuilt = harness.recreate()

        composeRule.runOnIdle {
            assertTrue(rebuilt.commentComposerVisible)
            assertEquals("half a reply", rebuilt.commentComposerDraft?.body)
        }
    }

    @Test
    fun aHalfWrittenPostSurvives() {
        val harness = harness()
        composeRule.runOnIdle {
            harness.state.updatePostComposerDraft(
                harness.state.postComposerDraft.copy(title = "a title", body = "a body"),
            )
        }

        val rebuilt = harness.recreate()

        composeRule.runOnIdle {
            assertEquals("a title", rebuilt.postComposerDraft.title)
            assertEquals("a body", rebuilt.postComposerDraft.body)
        }
    }

    // Private messages and direct chat share this draft (ConversationRouteHost).
    @Test
    fun anUnsentMessageInAConversationSurvives() {
        val harness = harness()
        composeRule.runOnIdle {
            harness.state.updateMessageConversationDraft(account.stableId, "chat-1", "unsent message")
        }

        val rebuilt = harness.recreate()

        composeRule.runOnIdle {
            assertEquals("unsent message", rebuilt.messageConversationDraft(account.stableId, "chat-1"))
        }
    }

    @Test
    fun everyVoteOfTheSessionSurvivesNotOnlyTheLast() = runBlocking {
        val harness = harness()
        val requests = mutableListOf<PostVoteRequest>()
        val votes = PostVoteCoordinator(
            capability = null,
            writer = { request -> AuthenticatedPostVoteResult.Voted(request) },
            clientMode = null,
            activeAccountStableId = { account.stableId },
        )
        composeRule.runOnIdle {
            harness.state.setAuthenticatedPostVoteRequester { request -> requests += request }
            harness.state.vote(postA, 1)
        }
        votes.vote(requests[0])
        composeRule.runOnIdle {
            harness.state.synchronizePostVoteState(votes.uiState.value)
            harness.state.vote(postB, 1)
        }
        votes.vote(requests[1])
        composeRule.runOnIdle { harness.state.synchronizePostVoteState(votes.uiState.value) }

        val rebuilt = harness.recreate()
        composeRule.runOnIdle { rebuilt.synchronizePostVoteState(votes.uiState.value) }

        composeRule.runOnIdle {
            assertEquals("last vote", 1, rebuilt.effectivePostVote(postB))
            assertEquals("earlier vote", 1, rebuilt.effectivePostVote(postA))
        }
    }

    @Test
    fun everySaveOfTheSessionSurvivesNotOnlyTheLast() = runBlocking {
        val harness = harness()
        val requests = mutableListOf<PostSaveRequest>()
        val saves = PostSaveCoordinator(
            capability = null,
            writer = { request -> AuthenticatedPostSaveResult.Updated(request) },
            clientMode = null,
            activeAccountStableId = { account.stableId },
        )
        composeRule.runOnIdle {
            harness.state.setAuthenticatedPostSaveRequester { request -> requests += request }
            harness.state.toggleSaved(postA)
        }
        saves.setSaved(requests[0])
        composeRule.runOnIdle {
            harness.state.synchronizePostSaveState(saves.uiState.value)
            harness.state.toggleSaved(postB)
        }
        saves.setSaved(requests[1])
        composeRule.runOnIdle { harness.state.synchronizePostSaveState(saves.uiState.value) }

        val rebuilt = harness.recreate()
        composeRule.runOnIdle { rebuilt.synchronizePostSaveState(saves.uiState.value) }

        composeRule.runOnIdle {
            assertTrue("last save", rebuilt.effectivePostSaved(postB))
            assertTrue("earlier save", rebuilt.effectivePostSaved(postA))
        }
    }

    // Without its rollback, a vote that fails after the rebuild must still
    // fall back to what Reddit has, so a vote in flight is not snapshotted.
    @Test
    fun aVoteInFlightThatFailsAfterTheSwitchIsRolledBack() {
        val harness = harness()
        val sending = PostVoteUiState(
            phase = PostVotePhase.SENDING,
            accountStableId = account.stableId,
            postFullname = "t3_aaa111",
            direction = 1,
        )
        composeRule.runOnIdle {
            harness.state.setAuthenticatedPostVoteRequester { }
            harness.state.vote(postA, 1)
            harness.state.synchronizePostVoteState(sending)
        }

        val rebuilt = harness.recreate()
        composeRule.runOnIdle {
            rebuilt.synchronizePostVoteState(sending)
            assertEquals(1, rebuilt.effectivePostVote(postA))
            rebuilt.synchronizePostVoteState(sending.copy(phase = PostVotePhase.FAILED))
            assertEquals(0, rebuilt.effectivePostVote(postA))
        }
    }

    // One account's votes and saves never show up for another account.
    @Test
    fun settledVotesStayWithTheirAccount() = runBlocking {
        val harness = harness()
        val requests = mutableListOf<PostVoteRequest>()
        val votes = PostVoteCoordinator(
            capability = null,
            writer = { request -> AuthenticatedPostVoteResult.Voted(request) },
            clientMode = null,
            activeAccountStableId = { account.stableId },
        )
        composeRule.runOnIdle {
            harness.state.setAuthenticatedPostVoteRequester { request -> requests += request }
            harness.state.vote(postA, 1)
        }
        votes.vote(requests[0])
        composeRule.runOnIdle { harness.state.synchronizePostVoteState(votes.uiState.value) }
        // Another account became active while the activity was gone (as the
        // stored selection and the directory say at the rebuild).
        harness.directory.selectAccount(otherAccount.stableId)
        ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("subshell_preferences", 0)
            .edit()
            .putString(ACTIVE_ACCOUNT_ID, otherAccount.stableId)
            .commit()

        val rebuilt = harness.recreate()

        composeRule.runOnIdle {
            assertEquals(otherAccount.stableId, rebuilt.activeAccountId)
            assertEquals(0, rebuilt.effectivePostVote(postA))
        }
    }

    // Long drafts share a budget: the rest of the snapshot (here the open
    // post's reply) is never lost to them.
    @Test
    fun longMessageDraftsDoNotCostTheRestOfTheSnapshot() {
        val harness = harness()
        val storedPost = SampleData.posts.first()
        composeRule.runOnIdle {
            val state = harness.state
            (1..30).forEach { index ->
                state.updateMessageConversationDraft(account.stableId, "chat-$index", "x".repeat(10_000))
            }
            state.open(storedPost)
            assertTrue(state.openCommentComposer(storedPost))
            state.updateCommentComposerDraft(
                requireNotNull(state.commentComposerDraft).copy(body = "the reply"),
            )
        }

        val rebuilt = harness.recreate()

        composeRule.runOnIdle {
            assertEquals("the reply", rebuilt.commentComposerDraft?.body)
            val restored = (1..30).count { index ->
                rebuilt.messageConversationDraft(account.stableId, "chat-$index").isNotEmpty()
            }
            assertTrue("some drafts restored: $restored", restored in 1..29)
        }
    }

    // RECREATE02: the image attached to an unsent reply comes back with it.
    @Test
    fun theImageAttachedToAnUnsentMessageSurvives() {
        val harness = harness()
        val image = ComposerMediaAttachment(
            uri = "content://media/picker/0/1/media/42",
            mimeType = "image/png",
            kind = ComposerMediaKind.IMAGE,
        )
        composeRule.runOnIdle {
            harness.state.updateMessageConversationDraft(account.stableId, "chat-1", "look")
            harness.state.updateMessageConversationMedia(account.stableId, "chat-1", image)
        }

        val rebuilt = harness.recreate()

        composeRule.runOnIdle {
            assertEquals("look", rebuilt.messageConversationDraft(account.stableId, "chat-1"))
            assertEquals(image, rebuilt.messageConversationMedia(account.stableId, "chat-1"))
        }
    }

    private fun hiddenState(fullname: String, phase: ContentSafetyPhase) = ContentSafetyUiState(
        phase = phase,
        operation = ContentSafetyOperation.SET_POST_HIDDEN,
        accountStableId = account.stableId,
        thingFullname = fullname,
        hidden = true,
    )

    @Test
    fun everyHideOfTheSessionSurvivesNotOnlyTheLast() {
        val harness = harness()
        composeRule.runOnIdle {
            val state = harness.state
            state.setAuthenticatedContentSafetyRequesters(postVisibilityRequester = {}, reportRequester = {})
            state.togglePostHidden(postA)
            state.synchronizeContentSafetyState(hiddenState("t3_aaa111", ContentSafetyPhase.SUCCEEDED))
            state.togglePostHidden(postB)
            state.synchronizeContentSafetyState(hiddenState("t3_bbb222", ContentSafetyPhase.SUCCEEDED))
        }

        val rebuilt = harness.recreate()
        composeRule.runOnIdle {
            rebuilt.synchronizeContentSafetyState(hiddenState("t3_bbb222", ContentSafetyPhase.SUCCEEDED))
            assertTrue("last hide", rebuilt.effectivePostHidden(postB))
            assertTrue("earlier hide", rebuilt.effectivePostHidden(postA))
        }
    }

    private fun followState(fullname: String) = PostFollowUiState(
        phase = PostFollowPhase.SUCCEEDED,
        accountStableId = account.stableId,
        postFullname = fullname,
        followed = true,
    )

    @Test
    fun everyFollowOfTheSessionSurvivesNotOnlyTheLast() {
        val harness = harness()
        composeRule.runOnIdle {
            val state = harness.state
            state.setAuthenticatedPostFollowRequester { }
            state.togglePostFollow(postA)
            state.synchronizePostFollowState(followState("t3_aaa111"))
            state.togglePostFollow(postB)
            state.synchronizePostFollowState(followState("t3_bbb222"))
        }

        val rebuilt = harness.recreate()
        composeRule.runOnIdle {
            rebuilt.synchronizePostFollowState(followState("t3_bbb222"))
            assertEquals("last follow", true, rebuilt.effectivePostFollowed(postB))
            assertEquals("earlier follow", true, rebuilt.effectivePostFollowed(postA))
        }
    }

    // RECREATE02_PERMISSION_ANSWER_SURVIVES_RECREATION_V1: a rebuild while the
    // notification question is open does not ask again, and the answer given
    // afterwards still reaches the rebuilt screen (a refusal turns them off).
    @Test
    fun theNotificationQuestionIsAskedOnceAcrossARebuildAndItsAnswerKept() {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
            initialInboxNotificationsEnabled = true,
        )
        val launches = mutableListOf<Int>()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                launches += requestCode
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                InboxNotificationPermissionEffect(state = state)
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, launches.size)

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        assertEquals("asked again after the rebuild", 1, launches.size)

        composeRule.runOnIdle { registry.dispatchResult(launches.single(), false) }
        composeRule.waitForIdle()
        assertFalse(state.inboxNotificationsEnabled)
    }

    private fun blockedState(username: String) = ContentSafetyUiState(
        phase = ContentSafetyPhase.SUCCEEDED,
        operation = ContentSafetyOperation.SET_USER_BLOCKED,
        accountStableId = account.stableId,
        thingFullname = "u/$username",
        username = username,
        blocked = true,
    )

    // RECREATE03: a block made from a profile (the blocked-accounts list not
    // loaded) lives only in the override map, like a vote.
    @Test
    fun everyBlockOfTheSessionSurvivesNotOnlyTheLast() {
        val harness = harness()
        composeRule.runOnIdle {
            val state = harness.state
            state.setAuthenticatedContentSafetyRequesters(postVisibilityRequester = {}, reportRequester = {})
            state.setUserBlocked("first_user", true)
            state.synchronizeContentSafetyState(blockedState("first_user"))
            state.setUserBlocked("second_user", true)
            state.synchronizeContentSafetyState(blockedState("second_user"))
        }

        val rebuilt = harness.recreate()
        composeRule.runOnIdle {
            rebuilt.synchronizeContentSafetyState(blockedState("second_user"))
            assertTrue("last block", rebuilt.isUserBlocked("second_user"))
            assertTrue("earlier block", rebuilt.isUserBlocked("first_user"))
        }
    }
}
