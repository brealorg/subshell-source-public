package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.model.Post
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
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
 * STEP3 (next01a6): on an expanded window in split mode another user's posts
 * are a list-detail workspace: a post opens beside the profile, the next post
 * replaces it instead of stacking, and your own profile is not claimed. Both
 * profile hosts ask for the user's next posts when the list ends on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class Step3PublicUserWorkspaceComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val appends = mutableListOf<String>()

    private fun compose(shortPages: Boolean = false): SubshellState {
        val state = SubshellState(
            repository = AuthoredByTheProfileRepository(shortPages),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.updateTabletLayoutMode(TabletLayoutMode.SPLIT)
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                onAppendRedditUserSubmitted = { _, username -> appends += username },
            )
        }
        composeRule.waitForIdle()
        return state
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun aPostOpensBesideTheProfileAndTheNextReplacesIt() {
        val state = compose()
        val first = SampleData.posts[0]
        val second = SampleData.posts[1]
        state.openUser("bob")
        composeRule.waitForIdle()
        assertEquals(1, shown("Select a post"))

        composeRule.runOnIdle { state.openFromUser(first, "bob") }
        composeRule.waitForIdle()
        val depth = state.navigationRouteDepth
        assertEquals(0, shown("Select a post"))
        assertTrue("the profile stays beside the post", shown("u/bob") >= 1)
        assertTrue(shown(first.title) >= 1)

        composeRule.runOnIdle { assertTrue(state.replaceCurrentUserContentSelection(second, "bob")) }
        composeRule.waitForIdle()
        assertEquals(depth, state.navigationRouteDepth)
        assertEquals(second.id, (state.currentNavigationRoute as SubshellRoute.Post).postRoute.postId)
        assertTrue(shown(second.title) >= 1)
        // A post from another user's list is not this workspace's selection.
        assertFalse(state.replaceCurrentUserContentSelection(first, "carol"))
    }

    @Test
    fun yourOwnProfileIsNotClaimedByThePublicWorkspace() {
        val state = compose()
        state.openUser(account.username)
        composeRule.waitForIdle()

        assertEquals(0, shown("Select a post"))
    }

    @Test
    @Config(qualifiers = "w411dp-h2400dp")
    fun bothProfileHostsAskForTheUsersNextPostsWhenTheListEndsOnScreen() {
        val state = compose(shortPages = true)
        state.navigate(MainDestination.YOU)
        composeRule.waitForIdle()
        assertTrue("own profile: $appends", account.username in appends)

        state.openUser("bob")
        composeRule.waitForIdle()
        assertTrue("public profile: $appends", "bob" in appends)
    }
}

/** Pages SampleData's posts as if the profile being shown had written them. */
private class AuthoredByTheProfileRepository(
    private val shortPages: Boolean = false,
    private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by delegate {
    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
        val result = delegate.posts(request)
        val key = request.queryKey.value
        val author = if ("bob" in key) "bob" else "connected-user"
        return if (result is RepositoryResult.Content) {
            val posts = result.value.map { post -> post.copy(author = author) }
            if (shortPages) result.copy(value = posts.take(1), nextCursor = "more") else result.copy(value = posts)
        } else {
            result
        }
    }
}
