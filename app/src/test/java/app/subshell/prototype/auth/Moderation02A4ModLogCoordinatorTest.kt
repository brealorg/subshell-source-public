package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditModeratorLogEntry
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLogLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModerationLogResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A4ModLogCoordinatorTest {
    @Test
    fun loadAndAppendRemainAccountAndCommunityPinnedAndDeduplicateByActionId() = runBlocking {
        val afters = mutableListOf<String?>()
        val loader = object : AuthenticatedRedditModerationLogLoader {
            override suspend fun load(
                accountStableId: String,
                rawCommunityName: String,
                after: String?,
            ): AuthenticatedRedditModerationLogResult {
                assertEquals("acct", accountStableId)
                assertEquals("subshell", rawCommunityName)
                afters += after
                return if (after == null) {
                    AuthenticatedRedditModerationLogResult.Loaded(
                        communityName = "subshell",
                        entries = listOf(entry("ModAction_1")),
                        nextCursor = "ModAction_Next",
                    )
                } else {
                    AuthenticatedRedditModerationLogResult.Loaded(
                        communityName = "subshell",
                        entries = listOf(entry("ModAction_1"), entry("ModAction_2")),
                        nextCursor = null,
                    )
                }
            }
        }
        val coordinator = ModerationLogCoordinator(
            capability = null,
            loader = loader,
            clientMode = null,
            activeAccountStableId = { "acct" },
        )
        coordinator.open("acct", "SubShell")
        coordinator.load("acct")
        coordinator.append("acct")

        assertEquals(listOf(null, "ModAction_Next"), afters)
        assertEquals(
            listOf("ModAction_1", "ModAction_2"),
            coordinator.uiState.value.entries.map { it.id },
        )
        assertTrue(coordinator.uiState.value.loaded)
        assertFalse(coordinator.uiState.value.loading)
    }

    @Test
    fun activeAccountMismatchFailsClosedBeforeAnyRead() = runBlocking {
        var reads = 0
        val loader = object : AuthenticatedRedditModerationLogLoader {
            override suspend fun load(
                accountStableId: String,
                rawCommunityName: String,
                after: String?,
            ): AuthenticatedRedditModerationLogResult {
                reads++
                error("read must not run")
            }
        }
        val coordinator = ModerationLogCoordinator(
            capability = null,
            loader = loader,
            clientMode = null,
            activeAccountStableId = { "other" },
        )
        coordinator.open("acct", "subshell")
        coordinator.load("acct")
        assertEquals(0, reads)
    }

    private fun entry(id: String) = RedditModeratorLogEntry(
        id = id,
        communityName = "subshell",
        moderatorName = "mod",
        action = "lock",
    )
}
