package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditAccountHistoryKind
import io.github.brealorg.subshell.data.RepositoryResult
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.IdentityMetadataRepository
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditAccountHistoryUiState
import io.github.brealorg.subshell.data.redditAccountHistoryQueryKey
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.model.Community
import io.github.brealorg.subshell.model.ProfileCommentFixture
import io.github.brealorg.subshell.model.ThreadPlacement
import io.github.brealorg.subshell.model.UserFixture
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (profile01a5, profile01a6a6): in your own signed-in profile the
 * library strip names the open destination and shows the others as labelled
 * icons — Hidden and Downvotes read their own history; a comment row names
 * its community (whose identity is asked for), renders its Markdown, and
 * after opening it Back returns to that row; and the local sample comments
 * never show while signed in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3OwnProfileComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val askedCommunities = CopyOnWriteArrayList<String>()
    private val identities = object : IdentityMetadataRepository {
        override fun cachedCommunityIdentity(name: String): Community? = null
        override suspend fun refreshCommunityIdentity(name: String, fallback: Community) = RepositoryResult.Content(fallback)
        override suspend fun refreshCommunityIdentities(fallbacks: List<Community>): RepositoryResult<List<Community>> {
            askedCommunities += fallbacks.map(Community::name)
            return RepositoryResult.Content(fallbacks)
        }
        override fun cachedUserIdentity(username: String): UserFixture? = null
        override suspend fun refreshUserIdentity(username: String, fallback: UserFixture) = RepositoryResult.Content(fallback)
    }

    private fun signedIn(tab: Int): SubshellState {
        ensureWorkManagerInitialized(context)
        return SubshellState(
            identityMetadataRepository = identities,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        ).also { it.selectUserProfileTab("me_user", tab) }
    }

    private fun comment(index: Int, body: String = "Comment number $index") = ProfileCommentFixture(
        id = "t1_c$index",
        author = "me_user",
        postId = "t3_p$index",
        age = "1h",
        body = body,
        score = 1,
        placement = ThreadPlacement.TOP,
        community = "kotlin",
        postTitle = "Post $index",
    )

    private fun history(comments: List<ProfileCommentFixture>) = RedditAccountHistoryUiState(
        accountStableId = account.stableId,
        username = "me_user",
        kind = RedditAccountHistoryKind.COMMENTS,
        queryKey = redditAccountHistoryQueryKey(account.stableId, "me_user", RedditAccountHistoryKind.COMMENTS),
        comments = comments,
        loaded = true,
    )

    @Test
    fun theStripNamesTheOpenDestinationAndHiddenAndDownvotesReadTheirOwnHistory() {
        val state = signedIn(tab = 2)
        val refreshes = mutableListOf<RedditAccountHistoryKind>()
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                androidx.compose.runtime.CompositionLocalProvider(io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator provides coordinator) {
                    UserProfileScreen(
                        username = "me_user",
                        state = state,
                        onRefreshRedditAccountHistory = { _, _, kind -> refreshes += kind },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("Saved").fetchSemanticsNodes().size)
        val hidden = composeRule.onNode(hasContentDescription("Hidden"), useUnmergedTree = true).fetchSemanticsNode()
        val hiddenTab = generateSequence(hidden) { it.parent }.first { SemanticsProperties.Selected in it.config }
        assertEquals(false, hiddenTab.config[SemanticsProperties.Selected])

        refreshes.clear()
        composeRule.onNode(hasContentDescription("Hidden")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Downvotes")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue(refreshes.toString(), RedditAccountHistoryKind.HIDDEN in refreshes)
        assertTrue(refreshes.toString(), RedditAccountHistoryKind.DOWNVOTED in refreshes)
        assertEquals(1, composeRule.onAllNodesWithText("Downvotes").fetchSemanticsNodes().size)
    }

    @Test
    fun aCommentRowNamesItsCommunityAndRendersItsMarkdown() {
        val state = signedIn(tab = 1)
        composeRule.setContent {
            SubshellTheme {
                UserProfileScreen(
                    username = "me_user",
                    state = state,
                    redditAccountHistoryState = history(listOf(comment(1, body = "Read [the docs](https://kotlinlang.org) first"))),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { "kotlin" in askedCommunities }

        assertTrue(composeRule.onAllNodesWithText("r/kotlin", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(1, composeRule.onAllNodesWithText("Read the docs first", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("[the docs]", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun backReturnsToTheOpenedComment() {
        val state = signedIn(tab = 1)
        val comments = (1..40).map { comment(it) }
        val shown = mutableStateOf(true)
        composeRule.setContent {
            SubshellTheme {
                if (shown.value) {
                    UserProfileScreen(
                        username = "me_user",
                        state = state,
                        redditAccountHistoryState = history(comments),
                        onResolveRedditProfileCommentTarget = { _, _, postId, onResolved ->
                            onResolved(SampleData.posts.first().copy(id = postId))
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val target = hasText("Comment number 30")
        val lists = composeRule.onAllNodes(hasScrollToNodeAction())
        for (index in 0 until lists.fetchSemanticsNodes().size) {
            if (runCatching { lists[index].performScrollToNode(target) }.isSuccess) break
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Comment number 30").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.runOnIdle { shown.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            state.navigateBack()
            shown.value = true
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Comment number 30").onFirst().assertIsDisplayed()
    }

    // STEP3 (profile01a2): the sample comments that come with the local
    // account never appear in a profile read while signed in.
    @Test
    fun aSignedInProfileNeverShowsTheLocalSampleComments() {
        val fixture = SampleData.profileComments.first { it.author == "shelluser" }
        val fixtureText = fixture.body.take(30)
        ensureWorkManagerInitialized(context)
        val local = io.github.brealorg.subshell.data.account.localFixtureAccountSummaries(listOf("shelluser"))
        val signedInState = SubshellState(
            repository = io.github.brealorg.subshell.data.FakeSubshellRepository(),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        ).also { it.selectUserProfileTab("shelluser", 1) }
        val emptyHistory = history(emptyList()).copy(
            username = "shelluser",
            queryKey = redditAccountHistoryQueryKey(account.stableId, "shelluser", RedditAccountHistoryKind.COMMENTS),
        )
        val showSignedIn = mutableStateOf(true)
        val localState = SubshellState(
            repository = io.github.brealorg.subshell.data.FakeSubshellRepository(),
            initialAccountSessions = local,
            initialActiveAccountId = local.first().stableId,
            initialInteractionSideConfigured = true,
        ).also { it.selectUserProfileTab("shelluser", 1) }
        composeRule.setContent {
            SubshellTheme {
                if (showSignedIn.value) {
                    UserProfileScreen(username = "shelluser", state = signedInState, redditAccountHistoryState = emptyHistory)
                } else {
                    UserProfileScreen(username = "shelluser", state = localState)
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText(fixtureText, substring = true).fetchSemanticsNodes().size)

        composeRule.runOnIdle { showSignedIn.value = false }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText(fixtureText, substring = true).fetchSemanticsNodes().isNotEmpty())
    }
}
