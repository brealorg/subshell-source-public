package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.ComposerPolicyCoordinator
import app.subshell.prototype.auth.UserFlairCoordinator
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyResult
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.CommentComposerScreen
import app.subshell.prototype.ui.screens.commentMediaNotAllowedMessage
import app.subshell.prototype.ui.theme.SubshellTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMPOSER10_COMMENT_MEDIA_RULES_V1: the comment field follows the
 * community's allowed_media_in_comments; unknown rules allow everything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Composer10CommentMediaRulesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val gif = ComposerMediaAttachment("content://media/gif/1", "image/gif", ComposerMediaKind.GIF)
    private val image = ComposerMediaAttachment("content://media/image/1", "image/png", ComposerMediaKind.IMAGE)

    @Test
    fun redditsListBecomesImageAndGifRules() {
        // r/pics, 2026-09-29.
        assertEquals(
            CommentMediaPolicy(imagesAllowed = true, gifsAllowed = false),
            commentMediaPolicyFromReddit(listOf("giphy", "static", "expression", "video")),
        )
        assertEquals(
            CommentMediaPolicy(imagesAllowed = false, gifsAllowed = true),
            commentMediaPolicyFromReddit(listOf("ANIMATED")),
        )
        assertNull(commentMediaPolicyFromReddit(null))
        assertEquals(commentMediaPolicyKey("a", "r/Pics"), commentMediaPolicyKey("a", "pics"))
    }

    @Test
    fun onlyAForbiddenKindIsNamed() {
        val noGifs = CommentMediaPolicy(imagesAllowed = true, gifsAllowed = false)
        assertEquals(
            "This community doesn't allow GIFs in comments. Remove it to send the text.",
            commentMediaNotAllowedMessage(gif, noGifs),
        )
        assertNull(commentMediaNotAllowedMessage(image, noGifs))
        assertNull(commentMediaNotAllowedMessage(gif, null))
    }

    @Test
    fun rulesAreReadOncePerCommunityAndAgainAfterAFailedRead() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        var reads = 0
        var answer: CommentMediaPolicy? = null
        val loader = object : AuthenticatedComposerPolicyLoader {
            override suspend fun load(accountStableId: String, community: String) =
                AuthenticatedComposerPolicyResult.Unavailable
            override suspend fun loadCommentMedia(accountStableId: String, community: String): CommentMediaPolicy? {
                reads += 1
                return answer
            }
        }
        val coordinator = ComposerPolicyCoordinator(
            composerPolicyLoader = loader,
            composerCommunitySearchLoader = null,
            userFlairCoordinator = UserFlairCoordinator(manager = null, activeAccountStableId = { null }),
            accountSessionDirectory = { null },
            scope = scope,
        )
        coordinator.loadCommentMediaPolicy("a", "pics")
        assertTrue(coordinator.commentMediaPolicies.value.isEmpty())

        answer = CommentMediaPolicy(imagesAllowed = true, gifsAllowed = false)
        coordinator.loadCommentMediaPolicy("a", "pics")
        coordinator.loadCommentMediaPolicy("a", "Pics")

        assertEquals(2, reads)
        assertEquals(answer, coordinator.commentMediaPolicies.value[commentMediaPolicyKey("a", "pics")])
    }

    @Test
    fun aCommunityWithoutGifsTurnsTheGifActionOffAndBlocksAnAttachedGif() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val post = SampleData.posts.first()
        val alice = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
            initialActiveAccountId = alice.stableId,
        )
        state.openCommentComposer(post)
        val policies = mapOf(
            commentMediaPolicyKey(alice.stableId, post.community) to
                CommentMediaPolicy(imagesAllowed = true, gifsAllowed = false),
        )
        var requested: Pair<String, String>? = null
        composeRule.setContent {
            SubshellTheme {
                CommentComposerScreen(
                    post = post,
                    state = state,
                    onClose = {},
                    commentMediaPolicies = policies,
                    onLoadCommentMediaPolicy = { account, community -> requested = account to community },
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(alice.stableId to post.community, requested)
        composeRule.onNode(hasContentDescription("GIF") and hasClickAction()).assertIsNotEnabled()
        composeRule.onNode(hasContentDescription("Image") and hasClickAction()).assertIsEnabled()

        composeRule.runOnIdle {
            state.updateCommentComposerDraft(requireNotNull(state.commentComposerDraft).copy(media = gif))
        }
        composeRule.waitForIdle()
        assertEquals(
            1,
            composeRule.onAllNodesWithText(
                "This community doesn't allow GIFs in comments. Remove it to send the text.",
            ).fetchSemanticsNodes().size,
        )
    }
}
