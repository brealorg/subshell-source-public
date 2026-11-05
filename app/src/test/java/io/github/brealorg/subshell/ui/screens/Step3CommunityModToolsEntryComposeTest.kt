package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.ui.components.ModeratorToolsWorkspaceDestination
import io.github.brealorg.subshell.ui.components.LocalModeratorToolsNavigation
import io.github.brealorg.subshell.ui.components.ModeratorToolsNavigationEntryPoints
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditModerationUiState
import io.github.brealorg.subshell.data.account.RedditModeratorAction
import io.github.brealorg.subshell.data.account.RedditModeratorActionPhase
import io.github.brealorg.subshell.data.account.RedditModeratorActionRequest
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (mod01a1, mod01a2): a community the signed-in account moderates offers
 * "Mod tools" in its top bar; opening it reads that community's queue once and
 * shows the screen's moderation action state. No entry while the moderated
 * list is not loaded, for another account's list, for a community the account
 * does not moderate, or for a local account.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CommunityModToolsEntryComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val local = AccountSessionSummary(
        stableId = "local:me",
        username = "local",
        source = AccountSessionSource.LOCAL_FIXTURE,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Local",
    )
    private val loads = mutableListOf<Triple<String, String, Boolean>>()

    private fun state(activeId: String = account.stableId): SubshellState {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        return SubshellState(
            initialAccountSessions = listOf(account, local),
            initialActiveAccountId = activeId,
            initialInteractionSideConfigured = true,
        )
    }

    private fun moderating(
        accountId: String = account.stableId,
        communities: Set<String> = setOf("android"),
        loaded: Boolean = true,
    ) = RedditModerationUiState(
        accountStableId = accountId,
        moderatedCommunities = communities,
        directoryLoaded = loaded,
    )

    private fun show(
        state: SubshellState,
        moderation: () -> RedditModerationUiState,
        action: RedditModeratorActionUiState = RedditModeratorActionUiState(),
    ) {
        composeRule.setContent {
            val coordinator = testVideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
            // The app hosts the sheet's navigation (ModeratorToolsRootEntryPoints); a
            // minimal stand-in here: one open community at the root.
            var openCommunity by remember { mutableStateOf<String?>(null) }
            val navigation = ModeratorToolsNavigationEntryPoints(
                isRootVisible = { it == openCommunity },
                isVisible = { it == openCommunity },
                destinationFor = { community ->
                    ModeratorToolsWorkspaceDestination.ROOT.takeIf { community == openCommunity }
                },
                openRoot = { openCommunity = it },
                dismiss = { openCommunity = null },
            )
            CompositionLocalProvider(
                LocalVideoPlaybackCoordinator provides coordinator,
                LocalModeratorToolsNavigation provides navigation,
            ) {
                CommunityScreen(
                    communityName = "android",
                    state = state,
                    onBack = {},
                    redditModerationState = moderation(),
                    onLoadRedditModQueue = { accountId, community, force -> loads += Triple(accountId, community, force) },
                    moderationActionState = action,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun entries() =
        composeRule.onAllNodes(hasContentDescription("Mod tools for r/android")).fetchSemanticsNodes().size

    @Test
    fun aModeratedCommunityOpensItsQueueOnceFromTheTopBar() {
        show(
            state(),
            { moderating() },
            action = RedditModeratorActionUiState(
                phase = RedditModeratorActionPhase.AUTHORIZATION_REQUIRED,
                request = RedditModeratorActionRequest("reddit:me", "android", "t3_abc123", RedditModeratorAction.APPROVE),
            ),
        )
        assertEquals(1, entries())
        assertEquals(emptyList<Triple<String, String, Boolean>>(), loads)

        composeRule.onAllNodes(hasContentDescription("Mod tools for r/android")).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf(Triple("reddit:me", "android", false)), loads)
        assertTrue(composeRule.onAllNodesWithText("Mod queue").fetchSemanticsNodes().isNotEmpty())
        // The screen's action state reaches the sheet.
        assertEquals(1, composeRule.onAllNodesWithText("Moderator authorization required").fetchSemanticsNodes().size)
    }

    @Test
    fun noEntryWithoutALoadedListOfTheActiveAccountThatHasThisCommunity() {
        var moderation by mutableStateOf(moderating(communities = setOf("kotlin")))
        show(state(), { moderation })
        assertEquals(0, entries())

        for (next in listOf(moderating(loaded = false), moderating(accountId = "reddit:other"))) {
            composeRule.runOnIdle { moderation = next }
            composeRule.waitForIdle()
            assertEquals(next.toString(), 0, entries())
        }

        // Control within the test: the matching list shows the entry.
        composeRule.runOnIdle { moderation = moderating() }
        composeRule.waitForIdle()
        assertEquals(1, entries())
        assertEquals(emptyList<Triple<String, String, Boolean>>(), loads)
    }

    @Test
    fun aLocalAccountGetsNoEntry() {
        show(state(activeId = local.stableId), { moderating(accountId = local.stableId) })

        assertEquals(0, entries())
    }
}
