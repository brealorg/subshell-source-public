package app.subshell.prototype.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.PostFollowPhase
import app.subshell.prototype.data.account.PostFollowRequest
import app.subshell.prototype.data.account.PostFollowUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityRule
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.screens.HomeScreen
import app.subshell.prototype.ui.screens.PostScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * STEP3 (homeaction01): a post card shares its Reddit link through the system
 * share sheet and copies it from the overflow (which has no Share or
 * Download of its own); Home's Comments button opens the post at its
 * comments, which the post screen scrolls to once; a long press on a post's
 * image offers Share media, Share post link and Download.
 *
 * STEP3 (safety01a3, safety01a32): Hide post on the open post asks Reddit
 * once and the post stays open, now offering Unhide post; Report opens the
 * community's report reasons and sends the chosen one with the community;
 * a custom reason is capped at 100 characters; Report waits while the
 * reasons load; without a Reddit account there is Hide but no Report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3PostActionsComposeTest {
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

    private fun signedIn() = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun shown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun withVideo(content: @androidx.compose.runtime.Composable () -> Unit) {
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme { CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) { content() } }
        }
        composeRule.waitForIdle()
    }

    private val post = SampleData.posts.first().copy(id = "t3_abc123", permalink = "/r/kotlin/comments/abc123/a_title/")

    @Test
    fun aCardSharesAndCopiesItsRedditLinkAndItsOverflowHasNoShareOrDownload() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        withVideo { PostCard(post = post, state = state, onOpen = {}) }

        composeRule.onAllNodesWithContentDescription("Share").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        val chooser = shadowOf(context).nextStartedActivity
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.contains("reddit.com/r/kotlin/comments/abc123"))

        composeRule.onAllNodesWithContentDescription("Post actions").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(0, shown("Share"))
        assertEquals(0, shown("Download"))
        composeRule.onNode(hasClickAction() and hasText("Copy link")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertTrue(clip.getItemAt(0).text.toString().contains("reddit.com/r/kotlin/comments/abc123"))
    }

    @Test
    fun homesCommentsButtonOpensThePostAtItsComments() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        withVideo { HomeScreen(state = state) }

        composeRule.onAllNodes(hasClickAction() and hasContentDescription("Comments")).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)
        assertTrue(state.isPostEntryTargetPending(state.postVisitId, FeedPostEntryTarget.COMMENTS))
    }

    @Test
    fun thePostScreenScrollsToTheCommentsOnceWhenOpenedAtThem() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val long = post.copy(body = (1..80).joinToString("\n\n") { "Body paragraph $it" }, comments = 3)
        state.openFromMainFeed(long, FeedPostEntryTarget.COMMENTS)
        val comments = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = long.id,
            comments = (1..3).map { n ->
                Comment(id = "t1_c$n", author = "someone", age = "1h", body = "Comment number $n", score = 1, depth = 0, accent = 0xFF336699)
            },
            fullDiscussionLoaded = true,
            contentRevision = 1L,
        )
        withVideo { PostScreen(post = long, state = state, onBack = {}, redditCommentsState = comments) }

        composeRule.onAllNodesWithText("Comment number 1").onFirst().assertIsDisplayed()
        assertFalse(state.isPostEntryTargetPending(state.postVisitId, FeedPostEntryTarget.COMMENTS))
    }

    @Test
    fun aLongPressOnAPostImageOffersShareMediaShareLinkAndDownload() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val image: Post = SampleData.posts.first { it.kind == PostKind.IMAGE && it.mediaAsset != null }
        withVideo { PostCard(post = image, state = state, onOpen = {}) }

        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)).onFirst()
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.waitForIdle()

        assertEquals(1, shown("Share media"))
        assertEquals(1, shown("Share post link"))
        assertEquals(1, shown("Download"))
    }

    // STEP3 (error01a2): a signed-in reader can ask for the comments again
    // from the post's overflow; without a Reddit account there is nothing to ask.
    private fun openPostOverflow() {
        composeRule.onAllNodesWithContentDescription("Post actions").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun aSignedInReaderCanRefreshTheCommentsFromTheOverflow() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val refreshes = mutableListOf<String>()
        withVideo {
            PostScreen(
                post = post,
                state = state,
                onBack = {},
                onRetryRedditComments = { account, postId, _ -> refreshes += "$account/$postId" },
            )
        }
        refreshes.clear()

        openPostOverflow()
        composeRule.onNode(hasClickAction() and hasText("Refresh comments")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf("${account.stableId}/${post.id}"), refreshes)
    }

    @Test
    fun withoutARedditAccountTheOverflowHasNoRefreshComments() {
        ensureWorkManagerInitialized(context)
        val local = app.subshell.prototype.data.account.localFixtureAccountSummaries(listOf("root"))
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = local,
            initialActiveAccountId = local.first().stableId,
            initialInteractionSideConfigured = true,
        )
        withVideo { PostScreen(post = post, state = state, onBack = {}) }

        openPostOverflow()

        assertEquals(0, shown("Refresh comments"))
    }

    // STEP3 (safety01a3, safety01a32): hide, unhide and report from the post menu.
    private val visibility = mutableListOf<RedditPostVisibilityRequest>()
    private val reports = mutableListOf<RedditContentReportRequest>()

    private fun signedInWithSafety() = signedIn().also { state ->
        state.setAuthenticatedContentSafetyRequesters(
            postVisibilityRequester = { request -> visibility += request },
            reportRequester = { request -> reports += request },
        )
    }

    private val textPost = post.copy(
        kind = PostKind.TEXT,
        title = "A post worth hiding",
        body = "Plain body",
        mediaAsset = null,
        nsfw = false,
    )

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun reportButton() = composeRule.onAllNodes(hasClickAction() and hasText("Report")).onFirst()

    private fun reasonsLoaded(community: String) = RedditCommunityInfoUiState(
        accountStableId = account.stableId,
        communityName = community,
        loaded = true,
        snapshot = CommunityInfoSnapshot(
            communityName = community,
            freeFormReports = true,
            rules = listOf(
                CommunityRule(shortName = "No spam", kind = "link", violationReason = "no-spam"),
                CommunityRule(shortName = "Be kind", kind = "all", violationReason = "be-kind"),
            ),
        ),
    )

    @Test
    fun hidingTheOpenPostAsksRedditOnceAndThePostStaysWithUnhide() {
        ensureWorkManagerInitialized(context)
        val state = signedInWithSafety()
        withVideo { PostScreen(post = textPost, state = state, onBack = {}) }

        openPostOverflow()
        click(hasClickAction() and hasText("Hide post"))

        assertEquals(listOf(RedditPostVisibilityRequest(account.stableId, "t3_abc123", true)), visibility)
        assertTrue("the open post stays", shown("A post worth hiding") >= 1)
        openPostOverflow()
        // One safety action at a time: Unhide waits for Reddit's answer.
        composeRule.onAllNodes(hasClickAction() and hasText("Unhide post")).onFirst().assertIsNotEnabled()
        assertEquals(0, shown("Hide post"))
    }

    @Test
    fun unhidingTheOpenPostAfterRedditConfirmedTheHideSendsHiddenFalse() {
        ensureWorkManagerInitialized(context)
        val state = signedInWithSafety()
        withVideo { PostScreen(post = textPost, state = state, onBack = {}) }
        openPostOverflow()
        click(hasClickAction() and hasText("Hide post"))
        composeRule.runOnIdle {
            state.synchronizeContentSafetyState(
                ContentSafetyUiState(
                    phase = ContentSafetyPhase.SUCCEEDED,
                    operation = ContentSafetyOperation.SET_POST_HIDDEN,
                    accountStableId = account.stableId,
                    thingFullname = "t3_abc123",
                    hidden = true,
                ),
            )
        }
        composeRule.waitForIdle()

        openPostOverflow()
        click(hasClickAction() and hasText("Unhide post"))

        assertEquals(
            listOf(
                RedditPostVisibilityRequest(account.stableId, "t3_abc123", true),
                RedditPostVisibilityRequest(account.stableId, "t3_abc123", false),
            ),
            visibility,
        )
        assertTrue(shown("A post worth hiding") >= 1)
    }

    @Test
    fun reportSendsTheChosenRuleWithTheCommunityOnlyAfterARuleIsChosen() {
        ensureWorkManagerInitialized(context)
        val state = signedInWithSafety()
        state.synchronizeReportReasonCommunityInfo(reasonsLoaded(textPost.community))
        withVideo { PostCard(post = textPost, state = state, onOpen = {}) }

        openPostOverflow()
        click(hasClickAction() and hasText("Report"))
        assertEquals(1, shown("Report post"))
        reportButton().assertIsNotEnabled()

        click(hasClickAction() and hasText("No spam"))
        reportButton().assertIsEnabled()
        click(hasClickAction() and hasText("Report"))

        assertEquals(
            listOf(RedditContentReportRequest(account.stableId, "t3_abc123", "no-spam", textPost.community)),
            reports,
        )
        assertTrue(visibility.isEmpty())
    }

    @Test
    fun aCustomReasonIsCappedAtOneHundredCharacters() {
        ensureWorkManagerInitialized(context)
        val state = signedInWithSafety()
        state.synchronizeReportReasonCommunityInfo(reasonsLoaded(textPost.community))
        withVideo { PostCard(post = textPost, state = state, onOpen = {}) }
        openPostOverflow()
        click(hasClickAction() and hasText("Report"))

        click(hasClickAction() and hasText("Something else"))
        val field = composeRule.onAllNodes(hasSetTextAction()).onFirst()
        field.performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("x".repeat(101))) }
        composeRule.waitForIdle()
        assertEquals("", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        reportButton().assertIsNotEnabled()

        field.performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("y".repeat(100))) }
        composeRule.waitForIdle()
        click(hasClickAction() and hasText("Report"))

        assertEquals(listOf("y".repeat(100)), reports.map(RedditContentReportRequest::reason))
    }

    @Test
    fun reportWaitsWhileTheReasonsLoad() {
        ensureWorkManagerInitialized(context)
        val state = signedInWithSafety()
        state.synchronizeReportReasonCommunityInfo(
            RedditCommunityInfoUiState(accountStableId = account.stableId, communityName = textPost.community, loading = true),
        )
        withVideo { PostCard(post = textPost, state = state, onOpen = {}) }
        openPostOverflow()
        click(hasClickAction() and hasText("Report"))

        assertEquals(1, shown("Loading report reasons"))
        reportButton().assertIsNotEnabled()
        assertTrue(reports.isEmpty())
    }

    @Test
    fun withoutARedditAccountThePostMenuHidesButHasNoReport() {
        ensureWorkManagerInitialized(context)
        val local = app.subshell.prototype.data.account.localFixtureAccountSummaries(listOf("root"))
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = local,
            initialActiveAccountId = local.first().stableId,
            initialInteractionSideConfigured = true,
        )
        withVideo { PostCard(post = textPost, state = state, onOpen = {}) }

        openPostOverflow()

        assertEquals(1, composeRule.onAllNodes(hasClickAction() and hasText("Hide post")).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasClickAction() and hasText("Report")).fetchSemanticsNodes().size)
    }

    // STEP3 (followpost01a1b): the overflow follows a post for a Reddit account.
    @Test
    fun aSignedInCardFollowsFromTheOverflowAndThenOffersUnfollow() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val follows = mutableListOf<PostFollowRequest>()
        state.setAuthenticatedPostFollowRequester { request -> follows += request }
        withVideo { PostCard(post = textPost, state = state, onOpen = {}) }

        openPostOverflow()
        composeRule.onNode(hasClickAction() and hasText("Follow post")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf(PostFollowRequest(account.stableId, "t3_abc123", followed = true)), follows)

        composeRule.runOnIdle {
            state.synchronizePostFollowState(
                PostFollowUiState(
                    phase = PostFollowPhase.SUCCEEDED,
                    accountStableId = account.stableId,
                    postFullname = "t3_abc123",
                    followed = true,
                    completionRevision = 1L,
                ),
            )
        }
        openPostOverflow()
        assertEquals(1, composeRule.onAllNodes(hasClickAction() and hasText("Unfollow post")).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasText("Follow post")).fetchSemanticsNodes().size)
    }

    @Test
    fun anotherPostsFollowInFlightDisablesTheFollowItem() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val follows = mutableListOf<PostFollowRequest>()
        state.setAuthenticatedPostFollowRequester { request -> follows += request }
        state.togglePostFollow(textPost.copy(id = "t3_other"))
        assertEquals(1, follows.size)
        withVideo { PostCard(post = textPost, state = state, onOpen = {}) }

        openPostOverflow()

        composeRule.onAllNodes(hasText("Follow post")).onFirst().assertIsNotEnabled()
        assertEquals("nothing more was asked", 1, follows.size)
    }

    @Test
    fun withoutARedditAccountThePostMenuHasNoFollow() {
        ensureWorkManagerInitialized(context)
        val local = app.subshell.prototype.data.account.localFixtureAccountSummaries(listOf("root"))
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = local,
            initialActiveAccountId = local.first().stableId,
            initialInteractionSideConfigured = true,
        )
        withVideo { PostCard(post = textPost, state = state, onOpen = {}) }

        openPostOverflow()

        assertEquals(0, composeRule.onAllNodes(hasText("Follow post")).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasText("Unfollow post")).fetchSemanticsNodes().size)
    }
}
