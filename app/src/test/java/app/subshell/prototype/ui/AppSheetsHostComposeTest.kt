package app.subshell.prototype.ui

import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithContentDescription
import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.rememberSettingsBackupRepository
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.app.AccountConnectEntryPoints
import app.subshell.prototype.ui.app.AppSheetsHost
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.theme.SubshellTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sheets SubshellApp shows above the scaffold, in their own host: the
 * account switcher (also opened for an active session that needs
 * re-authentication), the muted-communities list and the community mute
 * editor. The host owns none of the visibility state — it is passed in,
 * because the drawer and the scaffold write it too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppSheetsHostComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun theSwitcherShowsWhenAskedAndManageAccountsOpensTheAccountSettings() {
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
            initialActiveAccountId = alice.stableId,
        )
        val switcherVisible = mutableStateOf(true)
        val requestedSettings = mutableStateOf(SettingsDestination.ROOT.name)
        host(state, switcherVisible = switcherVisible, requestedSettings = requestedSettings)

        composeRule.onNodeWithText("u/alice").assertIsDisplayed()
        composeRule.onNodeWithText("Manage accounts").performClick()
        composeRule.waitForIdle()

        assertEquals(SettingsDestination.ACCOUNTS.name, requestedSettings.value)
        assertEquals(false, switcherVisible.value)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Secondary)
    }

    @Test
    fun anActiveSessionThatNeedsReauthenticationOpensTheSwitcherByItself() {
        val stale = alice.copy(state = AccountSessionState.REAUTHENTICATION_REQUIRED)
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + stale,
            initialActiveAccountId = stale.stableId,
        )
        val switcherVisible = mutableStateOf(false)
        host(state, switcherVisible = switcherVisible)

        composeRule.waitForIdle()
        assertEquals(true, switcherVisible.value)
    }

    // STEP3 (auth08a2): when Reddit ends the active session, the account
    // directory moves its revision; the app reads it again and the switcher
    // opens so the reader can sign in again.
    @Test
    fun aDirectoryRevisionThatMarksTheActiveSessionStaleOpensTheSwitcher() {
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
            initialActiveAccountId = alice.stableId,
        )
        val sessions = localFixtureAccountSummaries(listOf("root"))
        var current = AccountSessionDirectorySnapshot(sessions + alice, alice.stableId)
        val revisions = MutableStateFlow(0L)
        val directory = object : AccountSessionDirectory {
            override val revision = revisions
            override fun snapshot() = current
            override fun selectAccount(stableId: String) = current
        }
        val switcherVisible = mutableStateOf(false)
        host(state, switcherVisible = switcherVisible, accountConnect = AccountConnectEntryPoints(accountSessionDirectory = directory))
        assertEquals(false, switcherVisible.value)

        current = AccountSessionDirectorySnapshot(
            sessions + alice.copy(state = AccountSessionState.REAUTHENTICATION_REQUIRED),
            alice.stableId,
        )
        composeRule.runOnIdle { revisions.value = 1L }
        composeRule.waitForIdle()

        assertEquals(true, switcherVisible.value)
    }

    @Test
    fun theMutedCommunitiesListOpensTheMuteEditorForOneCommunity() {
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
            initialActiveAccountId = alice.stableId,
        )
        state.muteCommunity("kotlin", System.currentTimeMillis() + 3_600_000L)
        val mutedList = mutableStateOf(true)
        val editorName = mutableStateOf<String?>(null)
        host(state, mutedList = mutedList, editorName = editorName)

        composeRule.onNodeWithText("r/kotlin", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Change", substring = true).performClick()
        composeRule.waitForIdle()

        assertEquals(false, mutedList.value)
        assertEquals("kotlin", editorName.value)
    }

    @Test
    fun theMuteEditorRendersForTheRequestedCommunity() {
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
            initialActiveAccountId = alice.stableId,
        )
        host(state, editorName = mutableStateOf("kotlin"))

        composeRule.onNodeWithText("r/kotlin", substring = true).assertIsDisplayed()
    }

    // STEP3 (mute01a2): unmute from the list, the empty list, and the editor
    // of a muted community.
    @Test
    fun theMutedListUnmutesAndSaysWhenItIsEmpty() {
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
            initialActiveAccountId = alice.stableId,
        )
        state.muteCommunity("kotlin", null)
        host(state, mutedList = mutableStateOf(true))

        composeRule.onNodeWithContentDescription("Unmute r/kotlin").performClick()
        composeRule.waitForIdle()

        assertEquals(false, state.isCommunityMuted("kotlin"))
        composeRule.onNodeWithText("No muted communities").assertIsDisplayed()
    }

    @Test
    fun theEditorOfAMutedCommunityChangesTheDurationOrUnmutes() {
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
            initialActiveAccountId = alice.stableId,
        )
        state.muteCommunity("kotlin", null)
        host(state, editorName = mutableStateOf("kotlin"))

        composeRule.onNodeWithText("Change duration").assertExists()
        composeRule.onNodeWithText("Unmute community").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(false, state.isCommunityMuted("kotlin"))
    }

    private fun host(
        state: SubshellState,
        switcherVisible: MutableState<Boolean> = mutableStateOf(false),
        requestedSettings: MutableState<String> =
            mutableStateOf(SettingsDestination.ROOT.name),
        mutedList: MutableState<Boolean> = mutableStateOf(false),
        editorName: MutableState<String?> = mutableStateOf(null),
        accountConnect: AccountConnectEntryPoints = AccountConnectEntryPoints(),
    ) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellTheme {
                AppSheetsHost(
                    state = state,
                    accountConnect = accountConnect,
                    backupRepository = rememberSettingsBackupRepository(),
                    accountSwitcherVisibleState = switcherVisible,
                    requestedSettingsDestinationNameState = requestedSettings,
                    mutedCommunitiesSheetVisibleState = mutedList,
                    communityMuteEditorNameState = editorName,
                )
            }
        }
        composeRule.waitForIdle()
    }
}
