package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.RedditBannedUser
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditCommunityWikiUiState
import app.subshell.prototype.data.account.ModeratorUserManagementUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.model.CommunityRule
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.components.CommunityAboutSheet
import app.subshell.prototype.ui.components.ModeratorUserManagementDialog
import app.subshell.prototype.ui.screens.galleryPageKeys
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOWUI1: low-severity crashes in ui/ (OCB-098, OCB-128, OCB-155, OCB-315).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LowUi1CrashesComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aRuleAndAModeratorNamedLikeAFixedItemDoNotCrashTheAboutSheet() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                CommunityAboutSheet(
                    communityLabel = "r/kotlin",
                    communityName = "kotlin",
                    accountStableId = "reddit:me",
                    authenticated = true,
                    fallbackDescription = "",
                    uiState = RedditCommunityInfoUiState(
                        accountStableId = "reddit:me",
                        communityName = "kotlin",
                        snapshot = CommunityInfoSnapshot(
                            communityName = "kotlin",
                            rules = listOf(CommunityRule(shortName = "about-title")),
                            moderators = listOf(CommunityModerator(username = "about-title")),
                        ),
                        loaded = true,
                    ),
                    wikiUiState = RedditCommunityWikiUiState(),
                    onLoadWikiIndex = { _, _, _ -> },
                    onLoadWikiPage = { _, _, _, _ -> },
                    onAuthorizeWiki = { _, _ -> },
                    moderatorIdentity = { username -> UserFixture(username = username, description = "", karma = "") },
                    onEnsureModeratorIdentity = {},
                    onDismiss = {},
                    onRetry = {},
                    onOpenModerator = {},
                )
            }
        }
        composeRule.onNodeWithText("Rules").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("about-title", substring = true).assertExists()
        composeRule.onNodeWithText("Moderators").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("about-title", substring = true).assertExists()
    }

    @Test
    fun aBannedUserNamedLikeAFixedItemDoesNotCrashUserManagement() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                ModeratorUserManagementDialog(
                    accountStableId = "reddit:me",
                    activeUsername = "me",
                    state = ModeratorUserManagementUiState(
                        visible = true,
                        accountStableId = "reddit:me",
                        communityName = "subshell",
                        bannedUsers = listOf(RedditBannedUser(username = "banned-more")),
                        loaded = true,
                        nextCursor = "t2_next",
                    ),
                    onRefresh = {},
                    onAppend = {},
                    onMutate = {},
                    onAuthorize = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("banned-more", substring = true).assertExists()
    }

    @Test
    fun aDraftWithTheSameImageTwiceGetsAKeyPerPage() {
        val keys = galleryPageKeys(listOf("content://a", "content://b", "content://a"))

        assertEquals(3, keys.toSet().size)
        assertEquals(listOf("content://a", "content://b"), keys.take(2))
    }

    @Test
    fun anOversizedProcessSnapshotIsNotSavedInsteadOfThrowing() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val post = SampleData.posts.first()
        state.openCommentComposer(post)
        val draft = requireNotNull(state.commentComposerDraft)

        state.updateCommentComposerDraft(draft.copy(body = "short"))
        assertNotNull(subshellProcessSnapshotWithinBudget(encodeSubshellProcessSnapshot(state.processStateSnapshot())))

        state.updateCommentComposerDraft(draft.copy(body = "a".repeat(600_000)))
        assertNull(subshellProcessSnapshotWithinBudget(encodeSubshellProcessSnapshot(state.processStateSnapshot())))
    }
}
