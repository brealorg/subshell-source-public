package io.github.brealorg.subshell.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import io.github.brealorg.subshell.data.account.RedditCommunityWikiUiState
import io.github.brealorg.subshell.model.CommunityWikiPage
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WIKI04_A_PAGE_SAYS_IT_MAY_BE_OLDER_V1, WIKI04_THE_REVISION_DATE_IS_SHOWN_V1:
 * on the Pixel 11 (2026-11-18) an edit of r/astronotyet's `test1` on reddit.com
 * never reached Reddit's API: Subshell kept the older text. A page now says it may
 * be older and when it was last revised; config pages sync to both Wikis.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp")
class Wiki04OlderPageComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val olderNote = "Edits made in Reddit's new Wiki don't reach apps"

    private fun show(page: CommunityWikiPage) {
        composeRule.setContent {
            SubshellTheme {
                LazyColumn {
                    communityWikiItems(
                        state = RedditCommunityWikiUiState(
                            accountStableId = "reddit:me",
                            communityName = page.communityName,
                            page = page,
                        ),
                        communityName = page.communityName,
                        matches = true,
                        selectedPagePath = page.path,
                        showConfigurationPages = false,
                        onSelectedPagePathChange = {},
                        onShowConfigurationPagesChange = {},
                        onAuthorize = {},
                        onRetryIndex = {},
                        onRetryPage = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun anOrdinaryPageSaysItMayBeOlderAndWhenItWasRevised() {
        val revised = 1_735_689_600L // 2025-01-01
        show(
            CommunityWikiPage(
                communityName = "astronotyet",
                path = "test1",
                markdown = "Bla bla bla bla",
                revisionDateEpochSeconds = revised,
                revisionAuthor = "vpsj",
            ),
        )
        assertEquals(1, count(olderNote))
        assertEquals(1, count("Last revised ${communityWikiRevisionDate(revised)} by u/vpsj"))
    }

    @Test
    fun aConfigurationPageNeedsNoWarning() {
        show(
            CommunityWikiPage(
                communityName = "astronotyet",
                path = "config/automoderator",
                markdown = "---\naction: filter",
                revisionAuthor = "vpsj",
            ),
        )
        assertEquals(0, count(olderNote))
        assertEquals(1, count("Last revised by u/vpsj"))
    }
}
