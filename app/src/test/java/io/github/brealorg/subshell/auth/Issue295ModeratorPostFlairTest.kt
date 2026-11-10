package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.AccountSessionDirectory
import io.github.brealorg.subshell.data.account.AccountSessionDirectorySnapshot
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.PostManageFailureKind
import io.github.brealorg.subshell.data.account.PostManageMutationRequest
import io.github.brealorg.subshell.data.account.PostManageOperation
import io.github.brealorg.subshell.data.account.PostManagePhase
import io.github.brealorg.subshell.data.account.PostManageSnapshot
import io.github.brealorg.subshell.data.account.PostManageTarget
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostManageLoadResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostManageMutationResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostManager
import io.github.brealorg.subshell.ui.state.UiRequestState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #295 part 2: a moderator changes another reader's post flair (and NSFW and
 * spoiler) through the manage page opened as a moderator's; the author's own
 * reply notifications stay refused. ISSUE295_MODERATOR_SETS_POST_FLAIR_V1.
 */
class Issue295ModeratorPostFlairTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:mod",
        username = "mod",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private class RecordingManager : AuthenticatedPostManager {
        val loads = mutableListOf<PostManageTarget>()
        val mutations = mutableListOf<PostManageMutationRequest>()
        override suspend fun load(target: PostManageTarget): AuthenticatedPostManageLoadResult {
            loads += target
            return AuthenticatedPostManageLoadResult.Loaded(
                PostManageSnapshot(postFullname = target.postFullname, author = "someone", community = target.community),
            )
        }
        override suspend fun mutate(request: PostManageMutationRequest): AuthenticatedPostManageMutationResult {
            mutations += request
            return AuthenticatedPostManageMutationResult.Updated
        }
    }

    private fun coordinator(manager: AuthenticatedPostManager) = PostManageCoordinator(
        manager = manager,
        accountDirectory = object : AccountSessionDirectory {
            override fun snapshot() = AccountSessionDirectorySnapshot(listOf(account), account.stableId)
            override fun selectAccount(stableId: String) = snapshot()
        },
        editCapability = null,
        editClientMode = null,
        flairCapability = null,
        flairClientMode = null,
        moderationCapability = null,
        moderationClientMode = null,
    )

    private fun target(asModerator: Boolean) =
        PostManageTarget(account.stableId, "t3_abc", "someone", "kotlin", asModerator = asModerator)

    @Test
    fun asTheAuthorSomeoneElsesPostIsStillRefused() = runBlocking {
        val manager = RecordingManager()
        val coordinator = coordinator(manager)

        coordinator.load(target(asModerator = false))

        assertEquals(PostManageFailureKind.NOT_OWNER, coordinator.uiState.value.failureKind)
        assertTrue(manager.loads.isEmpty())
    }

    @Test
    fun asAModeratorItLoadsAndChangesFlairNsfwAndSpoiler() = runBlocking {
        val manager = RecordingManager()
        val coordinator = coordinator(manager)

        coordinator.load(target(asModerator = true))
        assertEquals(PostManagePhase.IDLE, coordinator.uiState.value.phase)
        assertEquals(1, manager.loads.size)

        coordinator.mutate(
            PostManageMutationRequest(
                target = target(asModerator = true),
                operation = PostManageOperation.SET_FLAIR,
                flairTemplateId = "tmpl",
                flairText = "Solved",
            ),
        )
        assertEquals(PostManagePhase.SUCCEEDED, coordinator.uiState.value.phase)
        coordinator.mutate(
            PostManageMutationRequest(
                target = target(asModerator = true),
                operation = PostManageOperation.SET_NSFW,
                enabled = true,
            ),
        )
        assertEquals(
            listOf(PostManageOperation.SET_FLAIR, PostManageOperation.SET_NSFW),
            manager.mutations.map(PostManageMutationRequest::operation),
        )
    }

    @Test
    fun aModeratorNeverChangesTheAuthorsReplyNotifications() = runBlocking {
        val manager = RecordingManager()
        val coordinator = coordinator(manager)

        coordinator.mutate(
            PostManageMutationRequest(
                target = target(asModerator = true),
                operation = PostManageOperation.SET_SEND_REPLIES,
                enabled = false,
            ),
        )

        assertEquals(PostManageFailureKind.NOT_OWNER, coordinator.uiState.value.failureKind)
        assertTrue(manager.mutations.isEmpty())
    }

    @Test
    fun theRequestSaysWhetherTheManagePageOpensAsAModerators() {
        val requests = UiRequestState()
        requests.requestPostManage("t3_abc", asModerator = true)
        assertTrue(requests.pendingPostManageAsModerator)
        assertTrue(requests.consumePostManageRequest("t3_abc"))
        requests.requestPostManage("t3_abc")
        assertFalse(requests.pendingPostManageAsModerator)
    }
}
