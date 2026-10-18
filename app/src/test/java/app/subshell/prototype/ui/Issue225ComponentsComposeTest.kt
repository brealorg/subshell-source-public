package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.components.AdaptiveFullScreenDialog
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.ui.components.SettingsSegmentedChoice
import app.subshell.prototype.ui.components.SettingsSwitchItem
import app.subshell.prototype.ui.components.SubshellSearchField
import app.subshell.prototype.ui.screens.CommunityScreen
import app.subshell.prototype.ui.screens.composerTitleSupportingText
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #225 (M3 audit group 8, hand-built UI → M3 components), owner decisions:
 * M3225_SHORT_SINGLE_CHOICE_IS_A_SEGMENTED_ROW_V1, M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1,
 * M3225_ONE_SEARCH_FIELD_STYLE_V1, M3225_TITLE_COUNTER_AND_ERRORS_ON_THE_FIELD_V1,
 * M3225_FORM_DIALOGS_ARE_FULL_SCREEN_ON_PHONES_V1, M3225_COMMUNITY_SORT_IS_A_MENU_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue225ComponentsComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val role = { expected: Role -> SemanticsMatcher.expectValue(SemanticsProperties.Role, expected) }

    @Test
    fun aShortSingleChoiceIsARadioGroupNotCheckboxes() {
        var chosen by mutableStateOf("Left")
        composeRule.setContent {
            SubshellTheme {
                SettingsSegmentedChoice(
                    title = "Interaction side",
                    description = "Preferred side",
                    options = listOf("Left", "Right"),
                    selected = chosen,
                    label = { it },
                    onSelect = { chosen = it },
                )
            }
        }

        composeRule.onNode(hasText("Left") and role(Role.RadioButton)).assertIsSelected()
        composeRule.onNode(hasText("Right") and role(Role.RadioButton)).assertIsNotSelected().performClick()
        composeRule.waitForIdle()

        assertEquals("Right", chosen)
        composeRule.onNode(hasText("Right") and role(Role.RadioButton)).assertIsSelected()
        assertTrue(
            nodesWithRole(Role.Checkbox).isEmpty(),
        )
    }

    @Test
    fun aSwitchRowTogglesFromAnywhereAndADisabledOneDoesNot() {
        var checked by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            SubshellTheme {
                Column {
                    SettingsSwitchItem(
                        title = "Loop videos",
                        description = "Restart videos automatically",
                        checked = checked,
                        onCheckedChange = { checked = it },
                        enabled = enabled,
                    )
                }
            }
        }

        composeRule.onNode(role(Role.Switch)).performClick()
        composeRule.waitForIdle()
        assertTrue(checked)

        enabled = false
        composeRule.waitForIdle()
        composeRule.onNode(role(Role.Switch)).performClick()
        composeRule.waitForIdle()
        assertTrue(checked)
    }

    @Test
    fun theSearchFieldClearsAndSearchesFromTheKeyboard() {
        var query by mutableStateOf("")
        val searched = mutableListOf<String>()
        composeRule.setContent {
            SubshellTheme {
                SubshellSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search settings",
                    onSearch = { searched += it },
                )
            }
        }

        assertTrue(composeRule.onAllNodes(hasContentDescription("Clear search")).fetchSemanticsNodes().isEmpty())
        composeRule.onNode(hasSetTextAction()).performTextInput("video")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        composeRule.waitForIdle()
        assertEquals(listOf("video"), searched)

        composeRule.onNodeWithContentDescription("Clear search").performClick()
        composeRule.waitForIdle()
        assertEquals("", query)
    }

    @Test
    fun theTitleCounterAndErrorSitOnTheFieldFrom80Percent() {
        assertNull(composerTitleSupportingText(message = null, length = 239, maxLength = 300))
        composeRule.setContent {
            SubshellTheme {
                Column {
                    composerTitleSupportingText(message = null, length = 240, maxLength = 300)?.invoke()
                    composerTitleSupportingText(message = "Title is required", length = 0, maxLength = 300)?.invoke()
                }
            }
        }

        composeRule.onNodeWithText("240 / 300").assertExists()
        composeRule.onNodeWithText("Title is required").assertExists()
    }

    @Test
    fun aFormDialogIsFullScreenOnAPhone() {
        var closed = 0
        composeRule.setContent {
            SubshellTheme {
                AdaptiveFullScreenDialog(
                    title = "Remove this post?",
                    onDismissRequest = { closed += 1 },
                    closeLabel = "Cancel",
                ) {
                    Box(Modifier.fillMaxWidth().testTag("body")) { Text("Form body") }
                }
            }
        }

        val dialogWidth = composeRule.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot.width
        assertEquals(px(411f), dialogWidth, px(1f))
        composeRule.onNodeWithContentDescription("Cancel").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun aFormDialogIsABasicDialogOnAWideWindow() {
        composeRule.setContent {
            SubshellTheme {
                AdaptiveFullScreenDialog(title = "Remove this post?", onDismissRequest = {}) {
                    Box(Modifier.fillMaxWidth().testTag("body")) { Text("Form body") }
                }
            }
        }

        val width = composeRule.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot.width
        assertTrue("dialog is $width px wide", width <= px(560f) + 1f)
    }

    @Test
    fun theCommunitySortIsAButtonWithACheckedMenu() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val account = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    CommunityScreen(
                        communityName = "pics",
                        state = state,
                        onRefreshRedditFeed = { _, _, _, _ -> },
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val current = state.communitySort
        val other = CommunitySort.entries.first { it != current }
        assertTrue(nodesWithRole(Role.Checkbox).isEmpty())
        composeRule.onNodeWithContentDescription("Sort posts: ${current.label}").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText(other.label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            .assertIsNotSelected()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(other, state.communitySort)
        assertFalse(nodesDescribed("Sort posts: ${other.label}").isEmpty())
    }

    // M3225_INBOX_DESTINATIONS_ARE_M3_TABS_V1 (owner 2026-10-13): Activity and
    // Messages are M3 tabs (Role.Tab, selected state), not segmented radio buttons.
    @Test
    fun theInboxDestinationsAreTabs() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        app.subshell.prototype.backup.SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        val account = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = testVideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Inbox").performClick()
        composeRule.waitForIdle()

        val activity = hasText("Activity") and role(Role.Tab)
        val messages = hasText("Messages") and role(Role.Tab)
        composeRule.onNode(activity).assertIsSelected()
        composeRule.onNode(messages).assertIsNotSelected().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(messages).assertIsSelected()
        assertTrue(composeRule.onAllNodes(hasText("Messages") and role(Role.RadioButton)).fetchSemanticsNodes().isEmpty())
    }

    private fun px(dp: Float): Float = with(composeRule.density) { dp.dp.toPx() }

    private fun nodesWithRole(target: Role) =
        composeRule.onAllNodes(role(target)).fetchSemanticsNodes()

    private fun nodesDescribed(label: String) =
        composeRule.onAllNodes(hasContentDescription(label)).fetchSemanticsNodes()
}
