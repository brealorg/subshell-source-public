package app.subshell.prototype.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.app.AppTopBar
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #283 (reader on 0.88): "Hide read posts" was greyed out with nothing saying
 * why, and once turned on it was off again after the next start.
 * ISSUE283_HIDE_READ_POSTS_IS_REMEMBERED_V1,
 * ISSUE283_HIDE_READ_POSTS_SAYS_WHY_IT_IS_OFF_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue283HideReadPostsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hideReadPostsIsRememberedPerAccountUntilTheMarkersAreReset() {
        val stored = mutableMapOf<String, AccountPostActions>()
        val first = stateWithActions(stored)
        first.markPostRead("read")
        first.updateHideReadPosts(true)

        val restarted = stateWithActions(stored)
        assertTrue(restarted.hideReadPosts)

        restarted.switchAccount("packetmoss")
        assertFalse(restarted.hideReadPosts)
        restarted.switchAccount("root")
        assertTrue(restarted.hideReadPosts)

        restarted.resetReadPostHistory()
        assertFalse(stateWithActions(stored).hideReadPosts)
    }

    @Test
    fun theSettingIsStoredInTheAppsPreferencesAndReadAtTheNextStart() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(application)
        val preferences = application.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)
        // As the previous run left it.
        preferences.edit()
            .putBoolean(accountPostActionsPreferenceKey(STARTUP_ACCOUNT, ACCOUNT_ACTION_FIELD_HIDE_READ), true)
            .putStringSet(accountPostActionsPreferenceKey(STARTUP_ACCOUNT, ACCOUNT_ACTION_FIELD_READ), setOf("issue283"))
            .commit()
        var state: SubshellState? = null
        composeRule.setContent { state = rememberSubshellState() }
        composeRule.waitForIdle()
        val launched = requireNotNull(state)
        assertEquals(STARTUP_ACCOUNT, launched.activeAccountUsername)
        assertTrue(launched.hideReadPosts)

        launched.updateHideReadPosts(false)

        assertFalse(
            preferences.getBoolean(accountPostActionsPreferenceKey(STARTUP_ACCOUNT, ACCOUNT_ACTION_FIELD_HIDE_READ), true),
        )
    }

    @Test
    fun theGreyedOutItemSaysThatOpenedPostsCountAsRead() {
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadAccountPostActions = { AccountPostActions() },
        )
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                AppTopBar(
                    state = state,
                    inContext = false,
                    modmailThreadOpen = false,
                    topBarHiddenFractionState = mutableFloatStateOf(0f),
                    activeInboxContextualSelection = null,
                    activeCustomFeed = null,
                    subsCommunityActions = false,
                    pendingAuthenticatedLandingAccountIdState = mutableStateOf(null),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Choose feed sorting").performClick()
        composeRule.onNodeWithText("Posts you open count as read").assertExists()
        hideItem().assertIsNotEnabled()
        // ISSUE283_RESET_READ_MARKERS_HAS_NO_REFRESH_ICON_V1: no leading icon, so its text
        // starts where "Hide read posts" starts (a leading icon pushes it 36 dp in).
        assertEquals(
            composeRule.onNodeWithText("Hide read posts", useUnmergedTree = true).getBoundsInRoot().left,
            composeRule.onNodeWithText("Reset read markers", useUnmergedTree = true).getBoundsInRoot().left,
        )

        state.markPostRead("opened")
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithText("Posts you open count as read").fetchSemanticsNodes().size)
        hideItem().assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertTrue(state.hideReadPosts)
    }

    private fun hideItem() =
        composeRule.onNode(hasClickAction() and hasText("Hide read posts", substring = true), useUnmergedTree = false)

    private fun stateWithActions(stored: MutableMap<String, AccountPostActions>) = SubshellState(
        initialActiveAccountUsername = "root",
        loadAccountPostActions = { username -> stored[username] ?: AccountPostActions() },
        persistAccountPostActions = { username, actions -> stored[username] = actions },
    )
}

/** The account a fresh install starts in (no Reddit sign-in). */
private const val STARTUP_ACCOUNT = "local"
