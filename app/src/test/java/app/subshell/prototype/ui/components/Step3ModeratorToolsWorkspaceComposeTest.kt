package app.subshell.prototype.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (moderation02a5a4, moderation02a4): in the mod tools sheet the
 * Moderators button opens the moderators view for the canonical community,
 * whose back step returns to the mod tools root; the Mod log button opens the
 * log for a community the moderator may read, and is absent for another.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ModeratorToolsWorkspaceComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val navigation = mutableListOf<String>()

    private fun sheet(destination: ModeratorToolsWorkspaceDestination) {
        val nav = ModeratorToolsNavigationEntryPoints(
            isRootVisible = { true },
            isVisible = { true },
            destinationFor = { destination },
            openChild = { community, child -> navigation += "open:$community:$child" },
            returnToRoot = { community, from -> navigation += "root:$community:$from" },
        )
        val content = ModeratorToolsWorkspaceContentEntryPoints(
            moderators = { community, onBack ->
                Text("Moderators of $community")
                TextButton(onClick = onBack) { Text("Back from moderators") }
            },
        )
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(
                    LocalModeratorToolsNavigation provides nav,
                    LocalModeratorToolsWorkspaceContent provides content,
                ) {
                    ModeratorToolsSheet(
                        communityLabel = "r/AskReddit",
                        communityName = "AskReddit",
                        accountStableId = "reddit:mod",
                        uiState = RedditModerationUiState(accountStableId = "reddit:mod", queueCommunityName = "askreddit", queueLoaded = true),
                        onDismiss = {},
                        onRefresh = {},
                        onAppend = {},
                        onModerate = {},
                        savedResponses = SavedResponseActions(emptyList(), onSave = { _, _ -> null }, onDelete = {}),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun click(text: String) {
        composeRule.onAllNodes(hasText(text) and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun theModeratorsButtonOpensTheModeratorsView() {
        sheet(ModeratorToolsWorkspaceDestination.ROOT)

        click("Moderators")

        assertEquals(listOf("open:AskReddit:MODERATORS"), navigation)
    }

    @Test
    fun theModeratorsViewIsForTheCanonicalCommunityAndStepsBackToTheRoot() {
        sheet(ModeratorToolsWorkspaceDestination.MODERATORS)

        assertEquals(1, composeRule.onAllNodesWithText("Moderators of askreddit").fetchSemanticsNodes().size)
        click("Back from moderators")

        assertEquals(listOf("root:AskReddit:MODERATORS"), navigation)
    }

    @Test
    fun theModLogButtonOpensTheLogOnlyWhereTheModeratorMayRead() {
        val log = ModeratorLogEntryPoints(canInspect = { it == "subshell" }, open = { navigation += "log:$it" })
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalModeratorLog provides log) {
                    ModeratorLogButton("subshell", onBeforeOpen = { navigation += "before" })
                    ModeratorLogButton("elsewhere")
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("Mod log").fetchSemanticsNodes().size)
        click("Mod log")

        assertEquals(listOf("before", "log:subshell"), navigation)
    }
}
