package app.subshell.prototype.auth

import kotlinx.coroutines.runBlocking
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.CustomFeedCreateFailureKind
import app.subshell.prototype.data.account.CustomFeedCreatePhase
import app.subshell.prototype.data.account.CustomFeedCreateRequest
import app.subshell.prototype.data.account.CustomFeedDeleteRequest
import app.subshell.prototype.data.account.CustomFeedFailureKind
import app.subshell.prototype.data.account.CustomFeedManageFailureKind
import app.subshell.prototype.data.account.CustomFeedManagePhase
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedUpdateRequest
import app.subshell.prototype.data.account.CustomFeedVisibility
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityFailureKind
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.CustomFeedCreateResult
import app.subshell.prototype.data.auth.CustomFeedCreator
import app.subshell.prototype.data.auth.CustomFeedManageResult
import app.subshell.prototype.data.auth.CustomFeedPostReadResult
import app.subshell.prototype.data.auth.CustomFeedReadResult
import app.subshell.prototype.data.auth.CustomFeedReader
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Characterisation of the custom-feed reads and writes on
 * AuthConnectViewModel, written before they move to a coordinator (HARNESS03
 * made the reader/creator injectable). Current behaviour, not a
 * specification. The write capability is faked as already granted, so the
 * browser authorization leg is not exercised here; the OAuth callback path
 * stays with AuthConnectViewModelCallbackRoutingTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelCustomFeedsTest {
    private val account = "reddit:test"

    @Test
    fun listLoadIsKeyedOnAccountAndReloadsOnlyWhenForcedOrAfterFailure() {
        val reader = FakeReader()
        val viewModel = viewModel(reader)

        viewModel.communities().loadCustomFeeds(account, forceRefresh = false)
        idle()
        assertTrue(viewModel.communities().customFeedsUiState.value.loading)
        viewModel.communities().loadCustomFeeds(account, forceRefresh = true)
        idle()
        assertEquals(1, reader.loadCalls)

        reader.pendingGate!!.complete(loaded(feed("user/tester/m/alpha", "Alpha")))
        idle()
        var state = viewModel.communities().customFeedsUiState.value
        assertFalse(state.loading)
        assertEquals(listOf("Alpha"), state.feeds.map { it.displayName })
        assertEquals(1L, state.contentRevision)

        // Loaded and not forced: refused. Forced: reloads.
        viewModel.communities().loadCustomFeeds(account, forceRefresh = false)
        idle()
        assertEquals(1, reader.loadCalls)
        viewModel.communities().loadCustomFeeds(account, forceRefresh = true)
        idle()
        assertEquals(2, reader.loadCalls)
        reader.pendingGate!!.complete(CustomFeedReadResult.Failed(CustomFeedFailureKind.HTTP, retryable = true, statusCode = 502))
        idle()
        state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedFailureKind.HTTP, state.failureKind)
        assertEquals(502, state.failureStatusCode)
        assertEquals(listOf("Alpha"), state.feeds.map { it.displayName })

        // After a failure an unforced load is allowed again.
        viewModel.communities().loadCustomFeeds(account, forceRefresh = false)
        idle()
        assertEquals(3, reader.loadCalls)

        // Another account starts from an empty state.
        viewModel.communities().loadCustomFeeds("reddit:other", forceRefresh = false)
        idle()
        state = viewModel.communities().customFeedsUiState.value
        assertEquals("reddit:other", state.accountStableId)
        assertTrue(state.feeds.isEmpty())
    }

    @Test
    fun createValidatesThenCreatesWithTheGrantedSessionAndReloads() = runBlocking<Unit> {
        val reader = FakeReader()
        val creator = FakeCreator()
        val viewModel = viewModel(reader, creator)

        val launch = viewModel.communities().beginCustomFeedCreation(
            CustomFeedCreateRequest(account, displayName = "  ", subredditNames = listOf("android")),
        )
        idle()
        assertEquals(AuthBrowserLaunchResult.NotStarted, launch)
        var state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedCreatePhase.FAILED, state.createPhase)
        assertEquals(CustomFeedCreateFailureKind.VALIDATION, state.createFailureKind)
        assertEquals(0, creator.createCalls)

        val ok = viewModel.communities().beginCustomFeedCreation(
            CustomFeedCreateRequest(account, displayName = " Alpha ", subredditNames = listOf(" android ", "Android", "kotlin")),
        )
        idle()
        assertEquals(AuthBrowserLaunchResult.NotStarted, ok)
        state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedCreatePhase.CREATING, state.createPhase)
        assertEquals("Alpha", creator.lastCreate?.displayName)
        assertEquals(listOf("android", "kotlin"), creator.lastCreate?.subredditNames)
        assertEquals(session, creator.lastCreateSession)

        creator.createGate.complete(CustomFeedCreateResult.Created)
        idle()
        state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedCreatePhase.SUCCEEDED, state.createPhase)
        assertEquals(1L, state.createCompletionRevision)
        // Success forces a list reload.
        assertEquals(1, reader.loadCalls)
        assertTrue(state.loading)
    }

    @Test
    fun createOutcomeUnknownReconcilesByRereadingTheListAfterTheFirstDelay() = runBlocking<Unit> {
        val reader = FakeReader()
        val creator = FakeCreator()
        val viewModel = viewModel(reader, creator)
        viewModel.communities().beginCustomFeedCreation(CustomFeedCreateRequest(account, "Alpha", listOf("android")))
        idle()
        creator.createGate.complete(CustomFeedCreateResult.Failed(CustomFeedCreateFailureKind.OUTCOME_UNKNOWN, retryable = true))
        idle()
        var state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedCreatePhase.FAILED, state.createPhase)
        assertEquals(CustomFeedCreateFailureKind.OUTCOME_UNKNOWN, state.createFailureKind)
        assertEquals(0, reader.loadCalls)

        // First reconciliation read after 500 ms; the feed exists -> SUCCEEDED.
        idleFor(500)
        assertEquals(1, reader.loadCalls)
        reader.pendingGate!!.complete(loaded(feed("user/tester/m/alpha", "alpha")))
        idle()
        state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedCreatePhase.SUCCEEDED, state.createPhase)
        assertNull(state.createFailureKind)
        assertEquals(1L, state.createCompletionRevision)
        // No further reconciliation reads once confirmed.
        idleFor(30_000)
        assertEquals(1, reader.loadCalls)
    }

    @Test
    fun createOutcomeUnknownWithoutTheFeedStaysUnknownUntilTheLastRead() = runBlocking<Unit> {
        val reader = FakeReader()
        val creator = FakeCreator()
        val viewModel = viewModel(reader, creator)
        viewModel.communities().beginCustomFeedCreation(CustomFeedCreateRequest(account, "Alpha", listOf("android")))
        idle()
        creator.createGate.complete(CustomFeedCreateResult.Failed(CustomFeedCreateFailureKind.OUTCOME_UNKNOWN, retryable = true))
        idle()
        // MED3 (OCB-585): a read without the feed keeps the outcome unknown
        // until the last read of the backoff (0.5, 2, 5, 15 s).
        for ((calls, delayMs) in listOf(1 to 500L, 2 to 2_000L, 3 to 5_000L)) {
            idleFor(delayMs)
            assertEquals(calls, reader.loadCalls)
            reader.pendingGate!!.complete(loaded(feed("user/tester/m/other", "Other")))
            idle()
            assertEquals(
                CustomFeedCreateFailureKind.OUTCOME_UNKNOWN,
                viewModel.communities().customFeedsUiState.value.createFailureKind,
            )
        }
        idleFor(15_000)
        assertEquals(4, reader.loadCalls)
        reader.pendingGate!!.complete(loaded(feed("user/tester/m/other", "Other")))
        idle()
        val state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedCreatePhase.IDLE, state.createPhase)
        assertNull(state.createFailureKind)
        assertEquals(CUSTOM_FEED_CREATE_NOT_CONFIRMED_MESSAGE, state.createMessage)
        assertEquals(0L, state.createCompletionRevision)
        idleFor(60_000)
        assertEquals(4, reader.loadCalls)
    }

    @Test
    fun updateAndDeleteRunWithTheGrantedSessionAndReloadOnSuccess() = runBlocking<Unit> {
        val reader = FakeReader()
        val creator = FakeCreator()
        val viewModel = viewModel(reader, creator)

        // Invalid path: validation failure, no call.
        viewModel.communities().beginCustomFeedUpdate(CustomFeedUpdateRequest(account, "not/a/path", "Alpha", listOf("android"), CustomFeedVisibility.PRIVATE))
        idle()
        var state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedManagePhase.FAILED, state.managePhase)
        assertEquals(CustomFeedManageFailureKind.VALIDATION, state.manageFailureKind)
        assertEquals(0, creator.updateCalls)

        viewModel.communities().beginCustomFeedUpdate(CustomFeedUpdateRequest(account, "user/tester/m/alpha", "Alpha 2", listOf("android"), CustomFeedVisibility.PUBLIC))
        idle()
        state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedManagePhase.SAVING, state.managePhase)
        assertEquals(1, creator.updateCalls)
        creator.manageGate.complete(CustomFeedManageResult.Updated)
        idle()
        state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedManagePhase.SUCCEEDED, state.managePhase)
        assertEquals(1L, state.manageCompletionRevision)
        assertEquals(1, reader.loadCalls)
        reader.pendingGate!!.complete(loaded(feed("user/tester/m/alpha", "Alpha 2")))
        idle()

        creator.manageGate = CompletableDeferred()
        viewModel.communities().beginCustomFeedDeletion(CustomFeedDeleteRequest(account, "user/tester/m/alpha", "Alpha 2"))
        idle()
        assertEquals(CustomFeedManagePhase.DELETING, viewModel.communities().customFeedsUiState.value.managePhase)
        assertEquals(1, creator.deleteCalls)
        creator.manageGate.complete(CustomFeedManageResult.Deleted)
        idle()
        state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedManagePhase.SUCCEEDED, state.managePhase)
        assertEquals(2L, state.manageCompletionRevision)
        assertEquals(2, reader.loadCalls)
    }

    @Test
    fun deleteOutcomeUnknownIsConfirmedWhenTheRereadNoLongerListsTheFeed() = runBlocking<Unit> {
        val reader = FakeReader()
        val creator = FakeCreator()
        val viewModel = viewModel(reader, creator)
        viewModel.communities().beginCustomFeedDeletion(CustomFeedDeleteRequest(account, "user/tester/m/alpha", "Alpha"))
        idle()
        creator.manageGate.complete(CustomFeedManageResult.Failed(CustomFeedManageFailureKind.OUTCOME_UNKNOWN, retryable = true))
        idle()
        assertEquals(CustomFeedManageFailureKind.OUTCOME_UNKNOWN, viewModel.communities().customFeedsUiState.value.manageFailureKind)

        // MED3 (OCB-585): still listed on every read of the backoff: the
        // outcome stays unknown until the last read, then a non-blocking
        // notice; the pending delete is cleared so a later read changes nothing.
        for ((calls, delayMs) in listOf(1 to 500L, 2 to 2_000L, 3 to 5_000L, 4 to 15_000L)) {
            idleFor(delayMs)
            assertEquals(calls, reader.loadCalls)
            reader.pendingGate!!.complete(loaded(feed("user/tester/m/alpha", "Alpha")))
            idle()
        }
        var state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedManagePhase.IDLE, state.managePhase)
        assertEquals(CUSTOM_FEED_DELETE_NOT_CONFIRMED_MESSAGE, state.manageMessage)
        idleFor(30_000)
        assertEquals(4, reader.loadCalls)

        // The same failure with the feed gone: SUCCEEDED.
        creator.manageGate = CompletableDeferred()
        viewModel.communities().beginCustomFeedDeletion(CustomFeedDeleteRequest(account, "user/tester/m/alpha", "Alpha"))
        idle()
        creator.manageGate.complete(CustomFeedManageResult.Failed(CustomFeedManageFailureKind.OUTCOME_UNKNOWN, retryable = true))
        idle()
        idleFor(500)
        assertEquals(5, reader.loadCalls)
        reader.pendingGate?.complete(loaded())
        idle()
        state = viewModel.communities().customFeedsUiState.value
        assertEquals(CustomFeedManagePhase.SUCCEEDED, state.managePhase)
        assertEquals(1L, state.manageCompletionRevision)
    }

    // REDDITURL04_MULTIREDDIT_LINK_V1
    @Test
    fun aFeedFromALinkIsResolvedByPathAndHandedBack() {
        val reader = FakeReader().apply { feedResult = feed("/user/other/m/rust/", "Rust things") }
        val viewModel = viewModel(reader)
        var resolved: CustomFeedSummary? = null

        viewModel.communities().resolveCustomFeed("reddit:tester", "/user/other/m/rust/") { feed -> resolved = feed }
        idle()

        assertEquals(listOf("reddit:tester" to "/user/other/m/rust/"), reader.feedCalls)
        assertEquals("Rust things", resolved?.displayName)
    }

    // LOW8_CUSTOM_FEED_WRITE_RESULT_STAYS_WITH_ITS_ACCOUNT_V1 (OCB-417)
    @Test
    fun aCreateThatFinishesAfterAnAccountSwitchIsNotWrittenIntoTheNewAccount() = runBlocking<Unit> {
        val reader = FakeReader()
        val creator = FakeCreator()
        val viewModel = viewModel(reader, creator)
        viewModel.communities().beginCustomFeedCreation(CustomFeedCreateRequest(account, displayName = "Alpha", subredditNames = listOf("android")))
        idle()
        assertEquals(1, creator.createCalls)

        viewModel.communities().loadCustomFeeds("reddit:other", forceRefresh = false)
        idle()
        val loadsBefore = reader.loadCalls
        creator.createGate.complete(CustomFeedCreateResult.Created)
        idle()

        val state = viewModel.communities().customFeedsUiState.value
        assertEquals("reddit:other", state.accountStableId)
        assertEquals(0L, state.createCompletionRevision)
        assertEquals(loadsBefore, reader.loadCalls)
    }

    private fun viewModel(reader: CustomFeedReader, creator: CustomFeedCreator = FakeCreator()): AuthConnectViewModel =
        AuthConnectViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            unavailableAuthApplicationRuntime().copy(
                customFeedReader = reader,
                customFeedCreator = creator,
                customFeedWriteCapability = GrantedCapability(),
                customFeedWriteClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            ),
        )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun idleFor(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun loaded(vararg feeds: CustomFeedSummary) = CustomFeedReadResult.Loaded(feeds.toList())
    private fun feed(path: String, name: String) = CustomFeedSummary(
        path = path,
        displayName = name,
        description = "",
        visibility = "private",
        subredditNames = listOf("android"),
    )

    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "bearer",
            expiresAtEpochMs = Long.MAX_VALUE,
            scope = "identity mysubreddits read subscribe",
        ),
        identity = AuthAccountIdentity(providerAccountId = "account-id", username = "tester"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    /** Reader whose list reads suspend until a test completes the current gate. */
    private class FakeReader : CustomFeedReader {
        var loadGate = CompletableDeferred<CustomFeedReadResult>()
        var pendingGate: CompletableDeferred<CustomFeedReadResult>? = null
        var loadCalls = 0

        override suspend fun load(accountStableId: String, preferredSession: StoredOAuthSession?): CustomFeedReadResult {
            loadCalls++
            val gate = loadGate
            pendingGate = gate
            loadGate = CompletableDeferred()
            return gate.await()
        }

        override suspend fun loadPosts(accountStableId: String, feedPath: String, after: String?, preferredSession: StoredOAuthSession?) =
            CustomFeedPostReadResult.Loaded(posts = emptyList(), nextCursor = null)

        var feedResult: CustomFeedSummary? = null
        val feedCalls = mutableListOf<Pair<String, String>>()

        override suspend fun loadFeed(accountStableId: String, feedPath: String): CustomFeedSummary? {
            feedCalls += accountStableId to feedPath
            return feedResult
        }
    }

    private class FakeCreator : CustomFeedCreator {
        var createGate = CompletableDeferred<CustomFeedCreateResult>()
        var manageGate = CompletableDeferred<CustomFeedManageResult>()
        var createCalls = 0
        var updateCalls = 0
        var deleteCalls = 0
        var lastCreate: CustomFeedCreateRequest? = null
        var lastCreateSession: StoredOAuthSession? = null

        override suspend fun create(request: CustomFeedCreateRequest, authorizedSession: StoredOAuthSession?): CustomFeedCreateResult {
            createCalls++; lastCreate = request; lastCreateSession = authorizedSession
            return createGate.await()
        }
        override suspend fun update(request: CustomFeedUpdateRequest, authorizedSession: StoredOAuthSession?): CustomFeedManageResult {
            updateCalls++
            return manageGate.await()
        }
        override suspend fun delete(request: CustomFeedDeleteRequest, authorizedSession: StoredOAuthSession?): CustomFeedManageResult {
            deleteCalls++
            return manageGate.await()
        }
    }

    /** A write capability whose grant is already present for every account. */
    private inner class GrantedCapability : AuthCustomFeedWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = error("not exercised")
        override suspend fun hasScopeGrant(accountStableId: String) = true
        override suspend fun authorizedSession(accountStableId: String): StoredOAuthSession = session
        override fun hasPendingAuthorization() = false
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCustomFeedWriteCapabilityResult =
            AuthCustomFeedWriteCapabilityResult.Failed(AuthCustomFeedWriteCapabilityFailureKind.CALLBACK)
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.Authenticated(session)
    }
}
