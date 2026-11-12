package io.github.brealorg.subshell.ui.app

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.ui.InteractionSide
import io.github.brealorg.subshell.ui.PostLayout
import io.github.brealorg.subshell.ui.RedditLinkHandlingState
import io.github.brealorg.subshell.ui.RedditLinkHandlingStatus
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import io.github.brealorg.subshell.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE271_ONE_SETUP_FLOW_AFTER_SIGN_IN_V1 (owner 2026-10-28): after the first
 * sign-in one flow asks hand, look, content and video, Reddit links and
 * backups; Skip or Done confirms the side and answers the link and backup
 * questions, so nothing else pops up over Home afterwards. There is no
 * notification page (DEFAULTS102_NO_NOTIFICATION_PAGE_V1): Android's question
 * comes once when the flow ends, from InboxNotificationPermissionEffect.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue271FirstRunSetupComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var repository: SettingsBackupRepository
    private var linkPromptDismissed = 0
    private var permissionAsks = 0

    @Before
    fun setUp() {
        ensureWorkManagerInitialized(application)
        application.getSharedPreferences("subshell_backup_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        repository = SettingsBackupRepository(application, "test")
    }

    private fun show(
        state: SubshellState,
        links: RedditLinkHandlingState = RedditLinkHandlingState(),
    ) {
        composeRule.setContent {
            SubshellTheme {
                FirstRunSetupScreen(
                    state = state,
                    backupRepository = repository,
                    linkHandlingState = links,
                    onOpenRedditLinkSettings = {},
                    onDismissRedditLinkPrompt = { linkPromptDismissed += 1 },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun tap(text: String) {
        val node = composeRule.onNode(hasText(text) and hasClickAction())
        // The page scrolls; the top and bottom bars do not.
        runCatching { node.performScrollTo() }
        node.performClick()
        composeRule.waitForIdle()
    }

    private fun shows(text: String) =
        composeRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theChoicesOnEachPageReachTheSettingsAndDoneEndsTheSetup() {
        val state = SubshellState()
        assertTrue(state.commentActionOnboardingVisible)
        show(state)

        tap("Left hand")
        assertEquals(InteractionSide.LEFT, state.interactionSide)
        tap("Next")
        tap("Dark")
        tap("Grid")
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(PostLayout.GRID, state.postLayout)
        tap("Next")
        tap("Show NSFW posts")
        assertTrue(state.showNsfwPosts)
        tap("Next")
        // No notification page; links are skipped here.
        assertFalse(shows("Get notified?"))
        assertTrue(shows("Keep your setup safe"))
        tap("Hidden communities")
        assertTrue(repository.hiddenCommunitiesBackupEnabled())
        tap("Next")
        assertTrue(shows("You're all set"))
        tap("Start using subshell")

        assertFalse("the setup does not come back", state.commentActionOnboardingVisible)
        assertEquals(InteractionSide.LEFT, state.interactionSide)
        assertFalse("no backup dialog over Home", repository.shouldShowFolderOnboarding())
        assertEquals("no link screen after it", 1, linkPromptDismissed)
    }

    @Test
    fun skipKeepsTheDefaultsAndEndsTheSetup() {
        val state = SubshellState()
        show(state)

        tap("Skip")

        assertFalse(state.commentActionOnboardingVisible)
        assertEquals(InteractionSide.RIGHT, state.interactionSide)
        assertFalse(repository.shouldShowFolderOnboarding())
        assertEquals(1, linkPromptDismissed)
    }

    @Test
    fun theLinkPageShowsOnlyWhereSubshellCanOpenRedditLinks() {
        val state = SubshellState()
        show(state, links = RedditLinkHandlingState(supported = true, status = RedditLinkHandlingStatus.DISABLED))
        repeat(3) { tap("Next") }
        assertTrue(shows("Open Reddit links here?"))
        tap("Not now")
        assertTrue(shows("Keep your setup safe"))

        assertEquals(
            listOf(
                FirstRunSetupPage.HAND,
                FirstRunSetupPage.LOOK,
                FirstRunSetupPage.CONTENT,
                FirstRunSetupPage.BACKUPS,
                FirstRunSetupPage.DONE,
            ),
            firstRunSetupPages(RedditLinkHandlingState(supported = true, status = RedditLinkHandlingStatus.ENABLED)),
        )
    }

    @Test
    fun androidIsNotAskedForNotificationsWhileTheSetupRuns() {
        val account = AccountSessionSummary(
            stableId = "reddit:me",
            username = "me_user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInboxNotificationsEnabled = true,
        )
        composeRule.setContent {
            InboxNotificationPermissionEffect(state = state, onRequestPermission = { permissionAsks += 1 })
        }
        composeRule.waitForIdle()
        assertEquals(0, permissionAsks)

        composeRule.runOnIdle { state.confirmInteractionSide() }
        composeRule.waitForIdle()
        assertEquals("after Skip it asks once, as before", 1, permissionAsks)
    }
}
