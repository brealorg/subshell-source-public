package app.subshell.prototype.auth

import app.subshell.prototype.data.account.*
import app.subshell.prototype.data.auth.*
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModerationActionResult
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class Moderation02A3ContentControlsCoordinatorTest {
    private val target = ModeratorReportTarget("t3_abc", "subshell")

    private class Loader(
        private val read: suspend (ModeratorReportTarget) -> AuthenticatedRedditReportTargetResult,
    ) : AuthenticatedRedditModerationLoader {
        override suspend fun loadReportTarget(
            accountStableId: String,
            target: ModeratorReportTarget,
        ) = read(target)
        override suspend fun loadModeratorCommunities(accountStableId: String) =
            AuthenticatedRedditModeratedCommunitiesResult.Loaded(setOf("subshell"))
        override suspend fun loadModQueue(accountStableId: String, rawCommunityName: String, after: String?) =
            error("Content control verification must use the exact target lookup")
        override suspend fun resolveTargetPost(accountStableId: String, postId: String) =
            AuthenticatedRedditModerationTargetResult.NotFound
    }

    private fun item(
        author: String = "moderator",
        locked: Boolean? = false,
        distinguished: Boolean? = false,
        allowed: Boolean? = true,
        fullname: String = "t3_abc",
    ) = RedditModeratorQueueItem(
        fullname = fullname,
        kind = RedditModeratorQueueKind.POST,
        communityName = "subshell",
        author = author,
        summary = "Fixture",
        reportDetails = RedditModeratorReportDetails(canModerate = allowed),
        contentState = RedditModeratorContentState(locked, distinguished, allowed),
    )

    private fun coordinator(
        read: suspend (ModeratorReportTarget) -> AuthenticatedRedditReportTargetResult,
        activeAccount: () -> String? = { "owner" },
        activeUsername: () -> String? = { "moderator" },
        write: suspend (RedditModeratorActionRequest, suspend () -> Boolean) -> AuthenticatedRedditModerationActionResult,
    ) = ModeratorContentControlsCoordinator(
        loader = Loader(read),
        activeAccountStableId = activeAccount,
        activeAccountUsername = activeUsername,
        performAction = write,
    )

    private suspend fun opened(
        c: ModeratorContentControlsCoordinator,
        action: RedditModeratorAction,
    ): ModeratorContentControlConfirmation {
        c.open("owner", target)
        c.refresh("owner")
        return requireNotNull(
            contentControlConfirmationOrNull(c.uiState.value, "owner", "moderator", action),
        )
    }

    @Test
    fun lockThenUnlockRequiresFreshPreflightAndReadback() = runBlocking {
        var locked = false
        var reads = 0
        val writes = mutableListOf<RedditModeratorAction>()
        val c = coordinator(
            read = {
                reads++
                AuthenticatedRedditReportTargetResult.Loaded(item(locked = locked))
            },
        ) { request, verify ->
            if (!verify()) return@coordinator AuthenticatedRedditModerationActionResult.Failed(
                RedditModeratorActionFailureKind.VALIDATION,
            )
            writes += request.action
            locked = request.action == RedditModeratorAction.LOCK
            AuthenticatedRedditModerationActionResult.Updated()
        }

        val lock = opened(c, RedditModeratorAction.LOCK)
        c.confirm(lock)
        assertEquals(listOf(RedditModeratorAction.LOCK), writes)
        assertEquals(true, c.uiState.value.item?.contentState?.locked)
        assertEquals(ModeratorContentControlsNotice.VERIFIED, c.uiState.value.notice)
        assertEquals(3, reads)

        val unlock = requireNotNull(
            contentControlConfirmationOrNull(c.uiState.value, "owner", "moderator", RedditModeratorAction.UNLOCK),
        )
        c.confirm(unlock)
        assertEquals(listOf(RedditModeratorAction.LOCK, RedditModeratorAction.UNLOCK), writes)
        assertEquals(false, c.uiState.value.item?.contentState?.locked)
    }

    @Test
    fun distinguishRequiresActiveModeratorToOwnTheTarget() = runBlocking {
        val other = coordinator(
            read = { AuthenticatedRedditReportTargetResult.Loaded(item(author = "someone_else")) },
        ) { _, _ -> error("No write") }
        other.open("owner", target)
        other.refresh("owner")
        assertNull(
            contentControlConfirmationOrNull(other.uiState.value, "owner", "moderator", RedditModeratorAction.DISTINGUISH),
        )

        val own = coordinator(
            read = { AuthenticatedRedditReportTargetResult.Loaded(item(author = "moderator")) },
        ) { request, verify ->
            assertTrue(verify())
            assertEquals(RedditModeratorAction.DISTINGUISH, request.action)
            AuthenticatedRedditModerationActionResult.AuthorizationRequired
        }
        val confirmation = opened(own, RedditModeratorAction.DISTINGUISH)
        own.confirm(confirmation)
        assertEquals(ModeratorContentControlsNotice.AUTHORIZATION_REQUIRED, own.uiState.value.notice)
        assertNull(
            contentControlConfirmationOrNull(own.uiState.value, "owner", "moderator", RedditModeratorAction.DISTINGUISH),
        )
    }

    @Test
    fun permissionOrStateChangeBeforeWriteBlocksDispatch() = runBlocking {
        listOf(
            item(allowed = false),
            item(locked = true),
            item(locked = null),
        ).forEach { changed ->
            var reads = 0
            var writes = 0
            val c = coordinator(
                read = {
                    reads++
                    AuthenticatedRedditReportTargetResult.Loaded(if (reads == 1) item() else changed)
                },
            ) { _, verify ->
                if (verify()) writes++
                AuthenticatedRedditModerationActionResult.Updated()
            }
            c.confirm(opened(c, RedditModeratorAction.LOCK))
            assertEquals(0, writes)
            assertFalse(c.uiState.value.mutating)
        }
    }

    @Test
    fun staleConfirmationAndWrongTargetCannotWrite() = runBlocking {
        var writes = 0
        val c = coordinator(
            read = { requested ->
                AuthenticatedRedditReportTargetResult.Loaded(
                    item(fullname = requested.fullname),
                )
            },
        ) { _, verify ->
            if (verify()) writes++
            AuthenticatedRedditModerationActionResult.Updated()
        }
        val old = opened(c, RedditModeratorAction.LOCK)
        c.refresh("owner")
        c.confirm(old)
        assertEquals(0, writes)
        c.open("owner", ModeratorReportTarget("t3_other", "subshell"))
        c.confirm(old)
        assertEquals(0, writes)
    }

    @Test
    fun unknownOutcomeIsReconciledWithoutSecondWrite() = runBlocking {
        var locked = false
        var writes = 0
        val c = coordinator(
            read = { AuthenticatedRedditReportTargetResult.Loaded(item(locked = locked)) },
        ) { request, verify ->
            if (!verify()) return@coordinator AuthenticatedRedditModerationActionResult.Failed(
                RedditModeratorActionFailureKind.VALIDATION,
            )
            writes++
            locked = request.action == RedditModeratorAction.LOCK
            AuthenticatedRedditModerationActionResult.Failed(
                RedditModeratorActionFailureKind.OUTCOME_UNKNOWN,
                retryable = true,
            )
        }
        val confirmation = opened(c, RedditModeratorAction.LOCK)
        c.confirm(confirmation)
        c.confirm(confirmation)
        assertEquals(1, writes)
        assertEquals(ModeratorContentControlsNotice.VERIFIED_AFTER_UNKNOWN, c.uiState.value.notice)
    }

    @Test
    fun rapidConfirmationCannotQueueASecondWrite() = runBlocking {
        withTimeout(2_000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var writes = 0
            var locked = false
            val c = coordinator(
                read = { AuthenticatedRedditReportTargetResult.Loaded(item(locked = locked)) },
            ) { request, verify ->
                if (!verify()) return@coordinator AuthenticatedRedditModerationActionResult.Failed(
                    RedditModeratorActionFailureKind.VALIDATION,
                )
                writes++
                entered.complete(Unit)
                release.await()
                locked = request.action == RedditModeratorAction.LOCK
                AuthenticatedRedditModerationActionResult.Updated()
            }
            val confirmation = opened(c, RedditModeratorAction.LOCK)
            val first = launch { c.confirm(confirmation) }
            entered.await()
            val second = launch { c.confirm(confirmation) }
            second.join()
            release.complete(Unit)
            first.join()
            assertEquals(1, writes)
        }
    }

    // STEP3 (moderation02a3): an account switch while the target is read
    // drops the late answer and the old confirmation can no longer write.
    @Test
    fun accountChangedDiscardsALateReadAndCannotWrite() = runBlocking {
        var active = "owner"
        var writes = 0
        val c = coordinator(
            read = { AuthenticatedRedditReportTargetResult.Loaded(item()) },
            activeAccount = { active },
        ) { _, _ -> writes++; AuthenticatedRedditModerationActionResult.Updated() }
        val lock = opened(c, RedditModeratorAction.LOCK)

        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val late = coordinator(
            read = { started.complete(Unit); gate.await(); AuthenticatedRedditReportTargetResult.Loaded(item()) },
            activeAccount = { active },
        ) { _, _ -> writes++; AuthenticatedRedditModerationActionResult.Updated() }
        late.open("owner", target)
        val job = launch { late.refresh("owner") }
        started.await()
        active = "other"
        late.accountChanged(active)
        c.accountChanged(active)
        gate.complete(Unit)
        job.join()

        assertEquals("other", late.uiState.value.accountStableId)
        assertNull(late.uiState.value.item)
        assertFalse(late.uiState.value.visible)
        c.confirm(lock)
        assertEquals(0, writes)
    }
}
