package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditCommunityInfoUiState
import io.github.brealorg.subshell.data.account.UserFlairUiState
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.ui.app.CommunityEntryPoints
import io.github.brealorg.subshell.ui.app.FeedEntryPoints
import io.github.brealorg.subshell.ui.app.InboxEntryPoints
import io.github.brealorg.subshell.ui.app.ModeratorToolsEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterisation of the runtime→state bridge effects at the top of
 * SubshellApp today, written before they move to RuntimeStateBridge.
 * Current behaviour, not a specification, one probe per effect family:
 * entry loads for the signed-in account, per-account moderator resets,
 * requester registration on SubshellState, and runtime UI states mirrored
 * into SubshellState when they change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RuntimeStateBridgeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bridgeLoadsResetsRegistersAndMirrorsForTheSignedInAccount() {
        val alice = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val bob = alice.copy(stableId = "reddit:bob", username = "bob")
        val state = SubshellState(
            initialAccountSessions = listOf(alice, bob),
            initialActiveAccountId = alice.stableId,
            initialInteractionSideConfigured = true,
        )
        val events = mutableListOf<String>()
        var flairState by mutableStateOf(UserFlairUiState())
        var communityInfoState by mutableStateOf(RedditCommunityInfoUiState())
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)

        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(onLoadRedditSubscriptions = { id, force -> events += "subs:$id:$force" }),
                redditInbox = InboxEntryPoints(onLoadRedditInbox = { id, force -> events += "inbox:$id:$force" }),
                moderatorTools = ModeratorToolsEntryPoints(
                    onModeratorReportsAccountChanged = { id -> events += "reports:$id" },
                    onModeratorLogAccountChanged = { id -> events += "modlog:$id" },
                ),
                onLoadRedditUserFlair = { id, community, force -> events += "flair:$id:$community:$force" },
                redditCommunity = CommunityEntryPoints(
                    onLoadRedditCommunityInfo = { id, community, force -> events += "info:$id:$community:$force" },
                    redditCommunityInfoState = communityInfoState,
                ),
                redditUserFlairState = flairState,
            )
        }
        composeRule.waitForIdle()

        // Entry loads and per-account resets, once for the active account.
        assertEquals(
            listOf("subs:reddit:alice:false", "inbox:reddit:alice:false", "reports:reddit:alice", "modlog:reddit:alice"),
            events.filter { it.startsWith("subs") || it.startsWith("inbox") || it.startsWith("reports") || it.startsWith("modlog") },
        )

        // Requesters registered on SubshellState route to the callbacks.
        state.loadUserFlair("homelab")
        state.ensureReportReasonPolicy("homelab")
        assertEquals(listOf("flair:reddit:alice:homelab:false", "info:reddit:alice:homelab:false"), events.takeLast(2))

        // Runtime UI states are mirrored into SubshellState when they change.
        flairState = UserFlairUiState(accountStableId = "reddit:alice", communityName = "homelab", loading = true)
        communityInfoState = RedditCommunityInfoUiState(accountStableId = "reddit:alice", communityName = "homelab", loading = true)
        composeRule.waitForIdle()
        assertEquals(flairState, state.userFlairUiState)
        assertEquals(communityInfoState, state.reportReasonCommunityInfoState)

        // An account switch repeats the loads and resets for the new account.
        events.clear()
        state.switchAccount("reddit:bob")
        composeRule.waitForIdle()
        assertEquals(
            listOf("subs:reddit:bob:false", "inbox:reddit:bob:false", "reports:reddit:bob", "modlog:reddit:bob"),
            events.filter { it.startsWith("subs") || it.startsWith("inbox") || it.startsWith("reports") || it.startsWith("modlog") },
        )
    }
}
