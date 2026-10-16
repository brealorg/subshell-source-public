package app.subshell.prototype.ui.components

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditCommunityWikiUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityWikiIndex
import app.subshell.prototype.model.CommunityWikiPage
import app.subshell.prototype.model.CommunityWikiPageRef
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (wiki01a2, wiki01a2r1, wiki01a2r2): the community sheet has three tabs
 * (About, Rules, Moderators) and the wiki opens from About into its own
 * workspace, which reads the page list only once it is open; a page opens
 * and shows its Markdown as text; configuration pages stay folded until
 * Show; the back arrow steps page -> wiki -> community info; a wiki without
 * a grant offers "Enable Wiki access".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CommunityWikiWorkspaceComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = "reddit:me"
    private val indexLoads = mutableListOf<String>()
    private val pageLoads = mutableListOf<String>()
    private val authorizations = mutableListOf<String>()
    private var wiki by mutableStateOf(RedditCommunityWikiUiState())

    private val loadedIndex = CommunityWikiIndex(
        "kotlin",
        listOf(CommunityWikiPageRef("faq"), CommunityWikiPageRef("config/sidebar")),
    )

    private fun show() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                CommunityAboutSheet(
                    communityLabel = "r/kotlin",
                    communityName = "kotlin",
                    accountStableId = account,
                    authenticated = true,
                    fallbackDescription = "",
                    uiState = RedditCommunityInfoUiState(
                        accountStableId = account,
                        communityName = "kotlin",
                        snapshot = CommunityInfoSnapshot(communityName = "kotlin", sidebarMarkdown = "About Kotlin"),
                        loaded = true,
                    ),
                    wikiUiState = wiki,
                    onLoadWikiIndex = { _, community, _ -> indexLoads += community },
                    onLoadWikiPage = { _, _, page, _ -> pageLoads += page },
                    onAuthorizeWiki = { _, community -> authorizations += community },
                    moderatorIdentity = { username -> UserFixture(username = username, description = "", karma = "") },
                    onEnsureModeratorIdentity = {},
                    onDismiss = {},
                    onRetry = {},
                    onOpenModerator = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun click(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun openWiki() = click(hasContentDescription("Open Wiki") or (hasText("Community documentation", substring = true) and hasClickAction()))

    @Test
    fun theSheetHasThreeTabsAndTheWikiIsReadOnlyOnceOpened() {
        show()

        for (tab in listOf("About", "Rules", "Moderators")) assertTrue(tab, count(tab) >= 1)
        assertEquals(emptyList<String>(), indexLoads)

        openWiki()

        assertEquals(listOf("kotlin"), indexLoads)
    }

    @Test
    fun aPageOpensShowsItsMarkdownAndBackStepsOut() {
        show()
        openWiki()
        composeRule.runOnIdle { wiki = RedditCommunityWikiUiState(accountStableId = account, communityName = "kotlin", index = loadedIndex) }
        composeRule.waitForIdle()

        assertEquals(0, count("config/sidebar"))
        click(hasText("Show") and hasClickAction() or (hasText("Configuration pages", substring = true) and hasClickAction()))
        assertTrue(count("config/sidebar") >= 1)

        click(hasText("faq", substring = true) and hasClickAction())
        assertEquals(listOf("faq"), pageLoads)
        composeRule.runOnIdle {
            wiki = wiki.copy(page = CommunityWikiPage("kotlin", "faq", "# Questions\n\nRead the docs."))
        }
        composeRule.waitForIdle()
        assertTrue(count("Read the docs.") >= 1)

        click(hasContentDescription("Back to Wiki"))
        click(hasContentDescription("Back to Community info"))
        assertTrue(composeRule.onAllNodesWithText("Community documentation").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aWikiWithoutAGrantOffersToEnableAccess() {
        wiki = RedditCommunityWikiUiState(accountStableId = account, communityName = "kotlin", authorizationRequired = true)
        show()
        openWiki()

        click(hasText("Enable Wiki access") and hasClickAction())

        assertEquals(listOf("kotlin"), authorizations)
    }
}
