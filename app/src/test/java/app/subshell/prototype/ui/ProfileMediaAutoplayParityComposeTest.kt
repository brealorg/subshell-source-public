package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SUBSHELL_PROFILE01A2_RUNTIME_FEED_PARITY_V1 (profile media autoplay): a
 * profile's posts take part in feed autoplay like Home — its videos are the
 * feed's video context, the GIF on screen autoplays, autoplay off holds it,
 * and leaving the profile clears both. Both profile hosts (your own and
 * another user's) do it. PARITY01_FEED_SURFACE_CLEARS_ONLY_WHAT_IT_OWNS_V1:
 * Home leaving after You has entered does not clear You's state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h2400dp")
class ProfileMediaAutoplayParityComposeTest {
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

    private val gif: Post = SampleData.posts.first { it.kind == PostKind.GIF && !it.nsfw }
    private val video: Post = SampleData.posts.first { it.kind == PostKind.VIDEO && !it.nsfw }

    private fun compose(posts: List<Post>, autoplay: VideoAutoplay = VideoAutoplay.ALWAYS): SubshellState {
        val state = SubshellState(
            repository = ProfilePostsRepository(posts),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.updateVideoAutoplay(autoplay)
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        return state
    }

    // The GIF is picked from the laid-out list, a frame or two after the posts arrive.
    private fun awaitAutoplayed(state: SubshellState) {
        val seen = mutableListOf<String?>()
        repeat(20) {
            composeRule.waitForIdle()
            seen += state.activeAnimatedMediaId
            if (state.activeAnimatedMediaId == gif.id) return
            composeRule.mainClock.advanceTimeBy(100)
        }
        assertEquals("seen: $seen", gif.id, state.activeAnimatedMediaId)
    }

    @Test
    fun anotherUsersProfileAutoplaysTheGifOnScreen() {
        val state = compose(listOf(gif))
        composeRule.runOnIdle { state.openUser("bob") }
        awaitAutoplayed(state)
    }

    @Test
    fun yourOwnProfileAutoplaysTheGifOnScreen() {
        val state = compose(listOf(gif))
        composeRule.runOnIdle { state.navigate(MainDestination.YOU) }
        awaitAutoplayed(state)
    }

    @Test
    fun aSurfaceThatLeftLateDoesNotClearTheOneThatEntered() {
        val state = SubshellState()
        state.updateVideoAutoplay(VideoAutoplay.ALWAYS)
        val home = Any()
        val profile = Any()
        state.claimFeedSurface(home)
        state.claimFeedSurface(profile)
        state.updateVideoFeedContext(listOf(video.id), video.id)
        state.setFeedAutoplayAnimatedMedia(gif.id)

        state.releaseFeedSurface(home)
        assertEquals(0, state.videoFeedRank(video.id))

        state.releaseFeedSurface(profile)
        assertNull(state.videoFeedRank(video.id))
        assertNull(state.activeAnimatedMediaId)
    }

    @Test
    fun autoplayOffHoldsTheProfilesGif() {
        val state = compose(listOf(gif), autoplay = VideoAutoplay.NEVER)
        composeRule.runOnIdle { state.openUser("bob") }
        composeRule.waitForIdle()

        assertNull(state.activeAnimatedMediaId)
    }

    @Test
    fun theProfilesVideosAreTheFeedsVideoContextUntilItIsLeft() {
        val state = compose(listOf(video))
        composeRule.runOnIdle { state.openUser("bob") }
        composeRule.waitForIdle()
        assertEquals(0, state.videoFeedRank(video.id))

        composeRule.runOnIdle { state.navigateBack() }
        composeRule.waitForIdle()
        assertNull(state.videoFeedRank(video.id))
    }

    @Test
    fun leavingTheProfileStopsItsGif() {
        val state = compose(listOf(gif))
        composeRule.runOnIdle { state.openUser("bob") }
        awaitAutoplayed(state)

        composeRule.runOnIdle { state.navigateBack() }
        composeRule.waitForIdle()
        assertNull(state.activeAnimatedMediaId)
    }
}

/** Serves [posts] as the shown profile's own posts; every other list is empty. */
private class ProfilePostsRepository(
    private val posts: List<Post>,
    private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by delegate {
    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
        val key = request.queryKey.value
        val author = when {
            "bob" in key -> "bob"
            "user" in key -> "connected-user"
            else -> return RepositoryResult.Content(emptyList(), nextCursor = null)
        }
        return RepositoryResult.Content(posts.map { it.copy(author = author) }, nextCursor = null)
    }
}
