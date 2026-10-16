package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditAccountHistoryUiState
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.redditAccountHistoryQueryKey
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.ProfileActivityEvidence
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.theme.SubshellTheme
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (profileuser01, useraction01a1): another user's profile while signed
 * in. An empty Posts or Comments list says "No public profile …" until there is
 * evidence of public activity of that kind — then it says profile privacy may be
 * enabled, for that kind only. A profile Reddit answers 404 for is unavailable:
 * no stats, no Block/Follow, no banner label. Block asks first and sends one
 * request; a local account follows locally. A post's author row opens the
 * profile and records post evidence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3PublicProfileComposeTest {
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
    private val askedUsers = CopyOnWriteArrayList<String>()

    private fun identities(userStatus: Int? = null) = object : IdentityMetadataRepository {
        override fun cachedCommunityIdentity(name: String): Community? = null
        override suspend fun refreshCommunityIdentity(name: String, fallback: Community) = RepositoryResult.Content(fallback)
        override suspend fun refreshCommunityIdentities(fallbacks: List<Community>): RepositoryResult<List<Community>> =
            RepositoryResult.Content(fallbacks)
        override fun cachedUserIdentity(username: String): UserFixture? = null
        override suspend fun refreshUserIdentity(username: String, fallback: UserFixture): RepositoryResult<UserFixture> {
            askedUsers += username
            return if (userStatus != null) {
                RepositoryResult.Error(message = "HTTP $userStatus", failureDetails = FailureDetails(httpStatusCode = userStatus))
            } else {
                RepositoryResult.Content(fallback)
            }
        }
    }

    private fun signedIn(userStatus: Int? = null): SubshellState {
        ensureWorkManagerInitialized(context)
        return SubshellState(
            repository = NoSubmittedPostsRepository(),
            identityMetadataRepository = identities(userStatus),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
    }

    private fun aliceComments(loaded: Boolean = true) = RedditAccountHistoryUiState(
        accountStableId = account.stableId,
        username = "alice",
        kind = RedditAccountHistoryKind.COMMENTS,
        queryKey = redditAccountHistoryQueryKey(account.stableId, "alice", RedditAccountHistoryKind.COMMENTS),
        comments = emptyList(),
        loaded = loaded,
    )

    private fun showProfile(state: SubshellState, username: () -> String = { "alice" }) {
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    UserProfileScreen(
                        username = username(),
                        state = state,
                        onBack = {},
                        redditAccountHistoryState = aliceComments(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun shown(text: String, substring: Boolean = false) =
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onNode(matcher).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun anEmptyListSaysPrivacyMayBeEnabledOnlyForTheKindWithEvidence() {
        val state = signedIn().also { it.selectUserProfileTab("alice", 0) }
        showProfile(state)

        assertEquals(1, shown("No public profile posts"))
        assertEquals(0, shown("Profile privacy may be enabled"))
        // The available profile keeps its banner placeholder label.
        assertEquals(1, shown("No profile banner"))

        composeRule.runOnIdle { state.openUser("alice", ProfileActivityEvidence.POST) }
        composeRule.waitForIdle()
        assertEquals(1, shown("Profile privacy may be enabled"))
        assertEquals(0, shown("No public profile posts"))

        // Post evidence says nothing about comments.
        composeRule.runOnIdle { state.selectUserProfileTab("alice", 1) }
        composeRule.waitForIdle()
        assertEquals(1, shown("No public profile comments"))
        assertEquals(0, shown("Profile privacy may be enabled"))

        composeRule.runOnIdle { state.openUser("alice", ProfileActivityEvidence.COMMENT) }
        composeRule.waitForIdle()
        assertEquals(1, shown("Profile privacy may be enabled"))
        assertEquals(0, shown("No public profile comments"))
    }

    @Test
    fun aProfileRedditCannotFindIsUnavailableWithoutStatsOrActions() {
        val state = signedIn(userStatus = 404).also { it.selectUserProfileTab("alice", 0) }
        showProfile(state)
        composeRule.waitUntil(5_000) { state.publicUserProfileUnavailable("alice") }
        composeRule.waitForIdle()

        assertTrue(askedUsers.toString(), "alice" in askedUsers)
        assertEquals(1, shown("Profile unavailable"))
        assertEquals(1, shown("Unavailable on Reddit"))
        assertEquals(0, shown("Redditor for", substring = true))
        // karma, account age and followers all read "—"; the placeholder's "2y" is gone.
        assertEquals(3, shown("—"))
        assertEquals(0, shown("2y"))
        assertEquals(0, shown("Block"))
        assertEquals(0, shown("Follow"))
        assertEquals(0, shown("No profile banner"))
        assertEquals(0, shown("No public profile posts"))
    }

    @Test
    fun blockAsksFirstAndSendsOneRequestAndYourOwnProfileHasNoBlock() {
        val state = signedIn().also { it.selectUserProfileTab("alice", 0) }
        val blocks = CopyOnWriteArrayList<RedditUserBlockRequest>()
        state.setAuthenticatedContentSafetyRequesters(
            postVisibilityRequester = {},
            reportRequester = {},
            userBlockRequester = { request -> blocks += request },
        )
        val username = mutableStateOf("alice")
        showProfile(state) { username.value }
        assertEquals(0, shown("Follow"))

        click(hasText("Block") and hasClickAction())
        assertEquals(1, shown("Block u/alice?"))
        click(hasText("Cancel") and hasAnyAncestor(isDialog()))
        assertEquals(0, shown("Block u/alice?"))
        assertTrue(blocks.isEmpty())

        click(hasText("Block") and hasClickAction() and !hasAnyAncestor(isDialog()))
        click(hasText("Block") and hasAnyAncestor(isDialog()))
        assertEquals(listOf(RedditUserBlockRequest(accountStableId = "reddit:me", username = "alice", blocked = true)), blocks.toList())

        composeRule.runOnIdle { username.value = "me_user" }
        composeRule.waitForIdle()
        assertEquals(0, shown("Block"))
        assertEquals(0, shown("Unblock"))
    }

    @Test
    fun aLocalAccountFollowsInsteadOfBlocking() {
        ensureWorkManagerInitialized(context)
        val local = localFixtureAccountSummaries(listOf("root"))
        val state = SubshellState(
            repository = NoSubmittedPostsRepository(),
            initialAccountSessions = local + account,
            initialActiveAccountId = local.first().stableId,
            initialInteractionSideConfigured = true,
        ).also { it.selectUserProfileTab("alice", 0) }
        showProfile(state)

        assertEquals(0, shown("Block"))
        assertFalse(state.isFollowingUser("alice"))
        click(hasText("Follow") and hasClickAction())

        assertTrue(state.isFollowingUser("alice"))
        assertEquals(1, shown("Following"))
    }

    @Test
    fun aPostsAuthorRowOpensTheProfileAndRecordsPostEvidence() {
        val state = signedIn()
        val post: Post = SampleData.posts.first { it.kind == PostKind.TEXT }.copy(author = "alice")
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    PostCard(post = post, state = state, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()
        assertFalse(state.hasPublicProfileActivityEvidence("alice", ProfileActivityEvidence.POST))

        click(
            SemanticsMatcher("click label Open profile") { node ->
                node.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Open profile"
            },
        )

        val route = state.currentNavigationRoute
        assertTrue(route.toString(), route is SubshellRoute.User && route.username == "alice")
        assertTrue(state.hasPublicProfileActivityEvidence("alice", ProfileActivityEvidence.POST))
        assertFalse(state.hasPublicProfileActivityEvidence("alice", ProfileActivityEvidence.COMMENT))
    }
}

/** FakeSubshellRepository whose post pages are empty: a profile with no public posts. */
private class NoSubmittedPostsRepository(
    private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by delegate {
    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> =
        RepositoryResult.Content(emptyList())
}
