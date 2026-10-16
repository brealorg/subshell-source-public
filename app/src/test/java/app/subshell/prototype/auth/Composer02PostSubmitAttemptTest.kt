package app.subshell.prototype.auth

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.TextPostWriteFailureKind
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitter
import app.subshell.prototype.data.auth.reddit.redditSubmitReconciliationShowsNoAttempt
import app.subshell.prototype.data.drafts.Crosspost01A1AMemoryPreferences
import app.subshell.prototype.data.drafts.TextPostSubmitAttemptStore
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationCandidate
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationKind
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationListing
import app.subshell.prototype.data.network.reddit.RedditSubmittedTextPost
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// COMPOSER02: one unconfirmed post no longer blocks every later post of every
// account. A read-back that shows no such post frees the guard (NOT_POSTED);
// attempts are kept per account.
class Composer02PostSubmitAttemptTest {
    private val writer = account("writer")
    private val other = account("otherwriter")

    private fun request(
        account: AccountSessionSummary = writer,
        title: String = "Lost post",
        text: String = "Body",
    ) = TextPostWriteRequest(
        accountStableId = account.stableId,
        community = "subshell",
        title = title,
        text = text,
    )

    private inner class Fixture {
        val prefs = Crosspost01A1AMemoryPreferences()
        val store = TextPostSubmitAttemptStore(prefs.preferences) { 1_000_000L }
        val directory = Directory(writer, other)
        val submitter = Submitter()

        fun dispatched(request: TextPostWriteRequest) {
            store.stage(request)
            store.markDispatched(request.accountStableId, request.requestKey)
        }

        fun coordinator() = TextPostWriteCoordinator(
            null, submitter, directory, OAuthClientMode.REDREADER_COMPATIBILITY,
            TextPostSubmitAttemptStore(prefs.preferences),
        )
    }

    @Test
    fun aReadBackThatShowsNoSuchPostFreesTheGuardAndOnlyThenPostsAgain() = runBlocking {
        val f = Fixture()
        f.dispatched(request())
        f.submitter.readResult = AuthenticatedTextPostSubmitResult.Failed(
            AuthenticatedTextPostSubmitFailureKind.NOT_POSTED,
        )
        val coordinator = f.coordinator()

        coordinator.submit(request())
        assertEquals(TextPostWritePhase.OUTCOME_UNKNOWN, coordinator.uiState.value.phase)
        assertEquals(0, f.submitter.writes)

        coordinator.reconcilePendingSubmit()
        assertEquals(TextPostWritePhase.FAILED, coordinator.uiState.value.phase)
        assertEquals(TextPostWriteFailureKind.NOT_POSTED, coordinator.uiState.value.failureKind)
        assertTrue(coordinator.uiState.value.retryable)
        assertNull(f.store.load(writer.stableId))

        coordinator.submit(request())
        assertEquals(1, f.submitter.writes)
        assertEquals(TextPostWritePhase.SUCCEEDED, coordinator.uiState.value.phase)
    }

    @Test
    fun anUnresolvedReadBackKeepsTheGuard() = runBlocking {
        val f = Fixture()
        f.dispatched(request())
        val coordinator = f.coordinator()

        coordinator.reconcilePendingSubmit()
        assertEquals(TextPostWritePhase.OUTCOME_UNKNOWN, coordinator.uiState.value.phase)
        assertNotNull(f.store.load(writer.stableId))
        coordinator.submit(request())
        assertEquals(0, f.submitter.writes)
    }

    @Test
    fun anotherDraftOfTheSameAccountIsToldAboutTheEarlierPostAndNotSent() = runBlocking {
        val f = Fixture()
        f.dispatched(request())
        val coordinator = f.coordinator()

        coordinator.submit(request(title = "A different post"))
        val state = coordinator.uiState.value
        assertEquals(TextPostWritePhase.FAILED, state.phase)
        assertEquals(TextPostWriteFailureKind.EARLIER_POST_UNCONFIRMED, state.failureKind)
        assertEquals(request(title = "A different post").requestKey, state.requestKey)
        assertEquals(0, f.submitter.writes)

        // Check again reads the earlier post back; a found post clears the guard.
        f.submitter.readResult = submitted(request())
        coordinator.reconcilePendingSubmit()
        assertEquals(1, f.submitter.reads)
        assertEquals(request().requestKey, f.submitter.readRequest?.requestKey)
        assertNull(f.store.load(writer.stableId))

        coordinator.submit(request(title = "A different post"))
        assertEquals(1, f.submitter.writes)
    }

    @Test
    fun anUnresolvedAttemptOfOneAccountDoesNotBlockAnother() = runBlocking {
        val f = Fixture()
        f.dispatched(request())
        f.directory.selectAccount(other.stableId)
        val coordinator = f.coordinator()

        coordinator.submit(request(account = other))
        assertEquals(1, f.submitter.writes)
        assertEquals(TextPostWritePhase.SUCCEEDED, coordinator.uiState.value.phase)
        assertNotNull(f.store.load(writer.stableId))
        assertNull(f.store.load(other.stableId))
    }

    @Test
    fun checkAgainWithNothingLeftForTheActiveAccountReturnsToIdle() = runBlocking {
        val f = Fixture()
        f.dispatched(request())
        val coordinator = f.coordinator()
        coordinator.submit(request(title = "A different post"))
        f.store.clear(writer.stableId)

        coordinator.reconcilePendingSubmit()
        assertEquals(TextPostWritePhase.IDLE, coordinator.uiState.value.phase)
        assertEquals(0, f.submitter.reads)
    }

    @Test
    fun aCrosspostShownAbsentIsFreedOnlyWhileItsAccountIsActive() {
        val crosspost = request(title = "Shared original", text = "")
            .copy(crosspostFullname = "t3_source123")
        val notPosted = AuthenticatedTextPostSubmitResult.Failed(
            AuthenticatedTextPostSubmitFailureKind.NOT_POSTED,
        )
        assertEquals(
            notPosted,
            validateCrosspostReconciliationResult(crosspost, notPosted, accountStillActive = true),
        )
        assertEquals(
            AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
            (validateCrosspostReconciliationResult(crosspost, notPosted, accountStillActive = false)
                as AuthenticatedTextPostSubmitResult.Failed).kind,
        )
    }

    @Test
    fun storeKeepsOneAttemptPerAccountAndReadsTheSingleSlotFormat() {
        val prefs = Crosspost01A1AMemoryPreferences()
        val store = TextPostSubmitAttemptStore(prefs.preferences) { 7_000L }
        store.stage(request())
        store.markDispatched(request().accountStableId, request().requestKey)
        store.stage(request(account = other))
        assertEquals(7_000L, store.load(writer.stableId)?.dispatchedAtEpochMs)
        assertNull(store.load(other.stableId)?.dispatchedAtEpochMs)

        store.stage(request(title = "Replaced"))
        assertEquals("Replaced", store.load(writer.stableId)?.request?.title)
        assertNotNull(store.load(other.stableId))
        store.clear(other.stableId)
        assertNull(store.load(other.stableId))
        assertNotNull(store.load(writer.stableId))

        val legacy = Crosspost01A1AMemoryPreferences()
        legacy.values["composer09_text_post_submit_attempt_v1"] = """
            {"accountStableId":"${writer.stableId}","community":"subshell","title":"Legacy",
             "text":"","sendReplies":true,"nsfw":false,"spoiler":false,"linkUrl":null,
             "linkPreviewImageUrl":null,"mediaUri":null,"mediaMimeType":null,"galleryItems":[],
             "flairId":null,"flairText":null,"mediaAsGif":false,"dispatchedAtEpochMs":42000}
        """.trimIndent()
        val legacyStore = TextPostSubmitAttemptStore(legacy.preferences)
        assertEquals("Legacy", legacyStore.load(writer.stableId)?.request?.title)
        assertNull(legacyStore.load(other.stableId))
        legacyStore.stage(request(account = other))
        assertEquals(42_000L, legacyStore.load(writer.stableId)?.dispatchedAtEpochMs)
        assertNotNull(legacyStore.load(other.stableId))
    }

    @Test
    fun absenceNeedsAListingThatReachesBeforeTheDispatch() {
        val empty = RedditSubmitReconciliationListing(emptyList(), reachesEnd = true)
        assertTrue(absent(empty))
        // SUBMITREADBACK02: "older" means before the dispatch minus the clock-skew margin (600 s).
        val olderOnly = RedditSubmitReconciliationListing(
            listOf(candidate(title = "Older post", createdUtc = 300L)),
            reachesEnd = false,
        )
        assertTrue(absent(olderOnly))
        val onlyNewerPage = RedditSubmitReconciliationListing(
            listOf(candidate(title = "Another post", createdUtc = 1_100L)),
            reachesEnd = false,
        )
        assertFalse(absent(onlyNewerPage))
    }

    @Test
    fun aPostWithTheSameTitleAfterTheDispatchIsNeverAbsenceWhateverElseDiffers() {
        val lookalikes = listOf(
            candidate(text = "Reddit changed the body"),
            candidate().copy(flairText = "Auto flair", nsfw = true, spoiler = true),
            candidate().copy(kind = RedditSubmitReconciliationKind.LINK),
            candidate(title = "  lost   POST "),
            candidate().copy(community = "SUBSHELL"),
            candidate(createdUtc = 5_000L),
        )
        for (lookalike in lookalikes) {
            val listing = RedditSubmitReconciliationListing(listOf(lookalike), reachesEnd = true)
            assertFalse(lookalike.toString(), absent(listing))
        }
        val elsewhere = RedditSubmitReconciliationListing(
            listOf(
                candidate().copy(community = "othercommunity"),
                candidate(createdUtc = 300L),
            ),
            reachesEnd = true,
        )
        assertTrue(absent(elsewhere))
    }

    private fun absent(listing: RedditSubmitReconciliationListing): Boolean =
        redditSubmitReconciliationShowsNoAttempt(
            request = AuthenticatedTextPostSubmitRequest(
                accountStableId = writer.stableId,
                community = "subshell",
                title = "Lost post",
                text = "Body",
                requestKey = request().requestKey,
            ),
            username = writer.username,
            dispatchedAtEpochMs = 1_000_000L,
            listing = listing,
        )

    private fun candidate(
        title: String = "Lost post",
        text: String = "Body",
        createdUtc: Long = 1_002L,
    ) = RedditSubmitReconciliationCandidate(
        fullname = "t3_abc123",
        id = "abc123",
        community = "subshell",
        author = writer.username,
        title = title,
        text = text,
        createdUtcEpochSeconds = createdUtc,
        permalink = "/r/subshell/comments/abc123/",
        destinationUrl = null,
        kind = RedditSubmitReconciliationKind.TEXT,
        flairText = null,
        nsfw = false,
        spoiler = false,
    )

    private fun submitted(request: TextPostWriteRequest) =
        AuthenticatedTextPostSubmitResult.Submitted(
            accountStableId = request.accountStableId,
            post = RedditSubmittedTextPost(
                fullname = "t3_created1", id = "created1", community = request.community,
                title = request.title, text = request.text,
                permalink = "/r/${request.community}/comments/created1/",
            ),
            metadata = NetworkResponseMetadata(
                statusCode = 200, receivedAtEpochMs = 1_005_000L,
                rateLimit = NetworkRateLimit(remaining = null, used = null, resetAfterMs = null),
                retryAfterMs = null,
            ),
        )

    private inner class Submitter : AuthenticatedTextPostSubmitter {
        var writes = 0
        var reads = 0
        var readRequest: AuthenticatedTextPostSubmitRequest? = null
        var readResult: AuthenticatedTextPostSubmitResult = AuthenticatedTextPostSubmitResult.Failed(
            AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
        )

        override suspend fun submit(
            request: AuthenticatedTextPostSubmitRequest,
        ): AuthenticatedTextPostSubmitResult {
            writes++
            return AuthenticatedTextPostSubmitResult.Submitted(
                accountStableId = request.accountStableId,
                post = RedditSubmittedTextPost(
                    fullname = "t3_new$writes", id = "new$writes", community = request.community,
                    title = request.title, text = request.text,
                    permalink = "/r/${request.community}/comments/new$writes/",
                ),
                metadata = submitted(request()).metadata,
            )
        }

        override suspend fun reconcile(
            request: AuthenticatedTextPostSubmitRequest,
            username: String,
            dispatchedAtEpochMs: Long,
        ): AuthenticatedTextPostSubmitResult {
            reads++
            readRequest = request
            return readResult
        }
    }

    private class Directory(vararg accounts: AccountSessionSummary) : AccountSessionDirectory {
        private var current = AccountSessionDirectorySnapshot(accounts.toList(), accounts.first().stableId)
        override fun snapshot() = current
        override fun selectAccount(stableId: String): AccountSessionDirectorySnapshot {
            current = current.copy(activeAccountId = stableId)
            return current
        }
    }

    private fun account(name: String) = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:t2_$name",
        username = name,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
}
