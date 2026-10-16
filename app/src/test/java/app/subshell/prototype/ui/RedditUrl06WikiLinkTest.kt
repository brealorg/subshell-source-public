package app.subshell.prototype.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditCommunityWikiUiState
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.components.CommunityAboutSheet
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** REDDITURL06: a wiki link opens that page in the community's about sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RedditUrl06WikiLinkTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openingAWikiLinkHandsTheCommunityScreenItsPageOnce() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        state.openRedditTarget(RedditTarget.Community("kotlin", wikiPage = "faq"))

        assertEquals("kotlin", (state.currentNavigationRoute as SubshellRoute.Community).communityName)
        assertNull(state.consumeCommunityWikiPage("android"))
        assertEquals("faq", state.consumeCommunityWikiPage("Kotlin"))
        assertNull(state.consumeCommunityWikiPage("kotlin"))
    }

    @Test
    fun theAboutSheetOpensTheLinkedWikiPage() {
        val pages = mutableListOf<String>()
        val indexes = mutableListOf<String>()
        renderSheet(linkedWikiPage = "guides/setup", pages = pages, indexes = indexes)

        // The sheet may ask twice while it opens; the wiki loader ignores a
        // request whose key is already loading (CommunityWikiCoordinator).
        assertEquals(listOf("guides/setup"), pages.distinct())
        assertEquals(emptyList<String>(), indexes)
    }

    @Test
    fun aLinkToTheWikiItselfOpensItsPageList() {
        val pages = mutableListOf<String>()
        val indexes = mutableListOf<String>()
        renderSheet(linkedWikiPage = "", pages = pages, indexes = indexes)

        assertEquals(emptyList<String>(), pages)
        assertEquals(listOf("kotlin"), indexes.distinct())
    }

    private fun renderSheet(
        linkedWikiPage: String?,
        pages: MutableList<String>,
        indexes: MutableList<String>,
    ) {
        var shown = 0
        composeRule.setContent {
            SubshellTheme {
                CommunityAboutSheet(
                    communityLabel = "r/kotlin",
                    communityName = "kotlin",
                    accountStableId = "reddit:me",
                    authenticated = true,
                    fallbackDescription = "",
                    uiState = RedditCommunityInfoUiState(),
                    wikiUiState = RedditCommunityWikiUiState(),
                    onLoadWikiIndex = { _, community, _ -> indexes += community },
                    onLoadWikiPage = { _, _, page, _ -> pages += page },
                    onAuthorizeWiki = { _, _ -> },
                    moderatorIdentity = { username -> UserFixture(username = username, description = "", karma = "") },
                    onEnsureModeratorIdentity = {},
                    onDismiss = {},
                    onRetry = {},
                    onOpenModerator = {},
                    linkedWikiPage = linkedWikiPage,
                    onLinkedWikiPageShown = { shown += 1 },
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, shown)
    }
}
