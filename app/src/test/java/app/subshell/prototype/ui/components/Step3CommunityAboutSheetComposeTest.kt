package app.subshell.prototype.ui.components

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.RedditCommunityInfoSection
import app.subshell.prototype.data.account.RedditCommunityInfoUiFailureKind
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditCommunityWikiUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.model.CommunityRule
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.theme.SubshellTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (communityinfo01a3, a32, a33, a34, a35): the community sheet's tabs
 * carry no counts; About shows the public summary at once and folds the
 * community details behind a Show/Hide row (details equal to the summary are
 * not repeated); one rule is open at a time and a rule without a description
 * does not open; moderator rows ask for their identity at most two at a time,
 * show the profile description or "Moderator", leave out the "all"
 * permission and show at most three; a missing section and a failed read
 * offer Retry; another account's or community's answer is never shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h2400dp")
class Step3CommunityAboutSheetComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = "reddit:me"
    private val retries = mutableListOf<Unit>()
    private val opened = mutableListOf<String>()
    private val asked = mutableListOf<String>()
    private var inFlight = 0
    private var maxInFlight = 0
    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    private var info by mutableStateOf(RedditCommunityInfoUiState())

    private fun loaded(snapshot: CommunityInfoSnapshot, account: String = this.account, community: String = "kotlin") =
        RedditCommunityInfoUiState(accountStableId = account, communityName = community, snapshot = snapshot, loaded = true)

    private fun show(state: RedditCommunityInfoUiState, summary: String = "", identity: (String) -> String = { "" }) {
        info = state
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                CommunityAboutSheet(
                    communityLabel = "r/kotlin",
                    communityName = "kotlin",
                    accountStableId = account,
                    authenticated = true,
                    fallbackDescription = summary,
                    uiState = info,
                    wikiUiState = RedditCommunityWikiUiState(),
                    onLoadWikiIndex = { _, _, _ -> },
                    onLoadWikiPage = { _, _, _, _ -> },
                    onAuthorizeWiki = { _, _ -> },
                    moderatorIdentity = { username -> UserFixture(username = username, description = identity(username), karma = "") },
                    onEnsureModeratorIdentity = { username ->
                        asked += username
                        inFlight++
                        maxInFlight = maxOf(maxInFlight, inFlight)
                        gates.getOrPut(username) { CompletableDeferred() }.await()
                        inFlight--
                    },
                    onDismiss = {},
                    onRetry = { retries += Unit },
                    onOpenModerator = { opened += it },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tab(title: String) = click(hasText(title) and hasClickAction())

    private fun count(text: String, substring: Boolean = false) =
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun described(text: String) = composeRule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().size

    @Test
    fun theTabsCarryNoCounts() {
        show(
            loaded(
                CommunityInfoSnapshot(
                    communityName = "kotlin",
                    rules = listOf(CommunityRule("One"), CommunityRule("Two"), CommunityRule("Three")),
                    moderators = listOf(CommunityModerator("alice"), CommunityModerator("bob")),
                ),
            ),
        )

        for (title in listOf("About", "Rules", "Moderators")) assertEquals(title, 1, count(title))
        assertEquals(0, count("Rules 3", substring = true))
        assertEquals(0, count("Moderators 2", substring = true))
        assertEquals(0, count("Community information from Reddit", substring = true))
    }

    @Test
    fun theSummaryShowsAtOnceAndTheDetailsFoldBehindShowAndHide() {
        show(
            loaded(CommunityInfoSnapshot(communityName = "kotlin", sidebarMarkdown = "Links and resources for Kotlin")),
            summary = "Kotlin news",
        )

        assertEquals(1, count("Kotlin news"))
        assertEquals(1, count("Community details"))
        assertEquals(1, count("Show links, resources and additional information."))
        assertEquals(1, count("Show"))
        assertEquals(0, count("Links and resources for Kotlin"))

        click(hasText("Community details") and hasClickAction() or hasContentDescription("Expand community details"))
        assertEquals(1, count("Links and resources for Kotlin"))
        assertEquals(1, count("Hide links, resources and additional information."))
        assertEquals(1, count("Hide"))
        assertEquals(1, described("Collapse community details"))

        click(hasText("Community details") and hasClickAction() or hasContentDescription("Collapse community details"))
        assertEquals(0, count("Links and resources for Kotlin"))
    }

    @Test
    fun detailsThatRepeatTheSummaryAreNotShownTwice() {
        show(loaded(CommunityInfoSnapshot(communityName = "kotlin", sidebarMarkdown = "Kotlin news")), summary = "Kotlin news")

        assertEquals(1, count("Kotlin news"))
        assertEquals(0, count("Community details"))
    }

    @Test
    fun oneRuleIsOpenAtATimeAndARuleWithoutADescriptionDoesNotOpen() {
        show(
            loaded(
                CommunityInfoSnapshot(
                    communityName = "kotlin",
                    rules = listOf(
                        CommunityRule("No spam", description = "Spam is removed"),
                        CommunityRule("Be kind", description = "Kindness is required"),
                        CommunityRule("Titles"),
                    ),
                ),
            ),
        )
        tab("Rules")
        assertEquals(0, count("Spam is removed"))
        assertEquals(2, described("Expand rule"))

        click(hasText("No spam") and hasClickAction())
        assertEquals(1, count("Spam is removed"))

        click(hasText("Be kind") and hasClickAction())
        assertEquals(1, count("Kindness is required"))
        assertEquals(0, count("Spam is removed"))

        assertEquals(0, composeRule.onAllNodes(hasText("Titles") and hasClickAction() and isEnabled()).fetchSemanticsNodes().size)
    }

    @Test
    fun moderatorRowsAskForTheirIdentityTwoAtATime() {
        show(
            loaded(
                CommunityInfoSnapshot(
                    communityName = "kotlin",
                    moderators = listOf("alice", "bob", "carol", "dave").map { CommunityModerator(it) },
                ),
            ),
            identity = { if (it == "alice") "Kotlin team" else "" },
        )
        tab("Moderators")

        assertEquals(2, asked.size)
        assertEquals(2, maxInFlight)
        repeat(4) {
            composeRule.runOnIdle { gates.values.firstOrNull { !it.isCompleted }?.complete(Unit) }
            composeRule.waitForIdle()
        }
        assertEquals(listOf("alice", "bob", "carol", "dave"), asked.sorted())
        assertEquals(2, maxInFlight)

        assertEquals(1, count("Kotlin team"))
        assertEquals(3, count("Moderator"))
        click(hasText("u/alice", substring = true) and hasClickAction() or (hasText("Kotlin team") and hasClickAction()))
        assertEquals(listOf("alice"), opened)
    }

    @Test
    fun theAllPermissionIsLeftOutAndAtMostThreeShow() {
        gates["alice"] = CompletableDeferred(Unit)
        gates["bob"] = CompletableDeferred(Unit)
        show(
            loaded(
                CommunityInfoSnapshot(
                    communityName = "kotlin",
                    moderators = listOf(
                        CommunityModerator("alice", listOf("all", "mail", "posts")),
                        CommunityModerator("bob", listOf("access", "config", "flair", "mail", "wiki")),
                    ),
                ),
            ),
        )
        tab("Moderators")

        assertEquals(1, count("mail · posts"))
        assertEquals(1, count("access · config · flair"))
        assertEquals(0, count("all", substring = false))
        assertEquals(0, count("wiki", substring = true))
    }

    @Test
    fun aMissingSectionAndAFailedReadOfferRetry() {
        show(
            loaded(CommunityInfoSnapshot(communityName = "kotlin", rules = emptyList())).copy(
                unavailableSections = setOf(RedditCommunityInfoSection.RULES),
            ),
        )
        tab("Rules")
        assertEquals(1, count("Community rules unavailable."))
        click(hasText("Retry") and hasClickAction())
        assertEquals(1, retries.size)

        composeRule.runOnIdle {
            info = info.copy(
                unavailableSections = emptySet(),
                failureKind = RedditCommunityInfoUiFailureKind.TRANSPORT,
                failureRetryable = true,
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, count("Couldn't refresh community info"))
        click(hasText("Retry") and hasClickAction())
        assertEquals(2, retries.size)

        composeRule.runOnIdle {
            info = RedditCommunityInfoUiState(
                accountStableId = account,
                communityName = "kotlin",
                failureKind = RedditCommunityInfoUiFailureKind.TRANSPORT,
                failureRetryable = true,
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, count("Couldn't load community info"))
    }

    @Test
    fun anotherAccountsOrCommunitysAnswerIsNotShown() {
        val snapshot = CommunityInfoSnapshot(communityName = "kotlin", sidebarMarkdown = "Secret details", rules = listOf(CommunityRule("Hidden rule")))
        show(loaded(snapshot, account = "reddit:other"), summary = "")

        assertEquals(1, count("Loading community info…"))
        tab("Rules")
        assertEquals(0, count("Hidden rule"))

        composeRule.runOnIdle { info = loaded(snapshot.copy(communityName = "java"), community = "java") }
        composeRule.waitForIdle()
        assertEquals(0, count("Hidden rule"))
        assertTrue(count("Loading community info…") >= 1)
    }
}
