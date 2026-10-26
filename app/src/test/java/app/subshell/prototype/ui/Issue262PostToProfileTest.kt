package app.subshell.prototype.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.subshell.prototype.data.auth.reddit.redditComposerAboutRequest
import app.subshell.prototype.data.network.reddit.normalizeRedditTextPostCommunity
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.screens.V4CommunitySheet
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
 * #262: a reader could not post to their own profile; the composer's picker
 * offered communities only. Reddit takes a profile post as a post to the
 * profile's community u_<username>, which the composer policy and the submit
 * already handle; the picker now offers it first and labels it u/<username>.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue262PostToProfileTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val joined = ComposerCommunityOption(name = "subshell", joined = true)

    private fun names(options: List<ComposerCommunityOption>) = options.map(ComposerCommunityOption::name)

    @Test
    fun theSignedInAccountsProfileComesFirst() {
        assertEquals(
            listOf("u_tester", "subshell"),
            names(composerCommunityPickerOptions(listOf(joined), emptyList(), query = "", authenticated = true, profileUsername = "tester")),
        )
        assertEquals(
            "signed out there is no profile to post to",
            listOf("subshell"),
            names(composerCommunityPickerOptions(listOf(joined), emptyList(), query = "", authenticated = false, profileUsername = "tester")),
        )
    }

    @Test
    fun typingTheNameOrProfileFindsItAndOtherWordsDoNot() {
        fun found(query: String) = "u_tester" in names(
            composerCommunityPickerOptions(listOf(joined), emptyList(), query = query, authenticated = true, profileUsername = "tester"),
        )
        assertTrue(found("test"))
        assertTrue(found("u/tester"))
        assertTrue(found("profile"))
        assertFalse(found("subshell"))
    }

    @Test
    fun aProfileIsLabelledUAndACommunityR() {
        assertEquals("u/tester", composerDestinationLabel("u_tester"))
        assertEquals("r/subshell", composerDestinationLabel("subshell"))
        assertEquals("r/subshell", composerDestinationLabel("r/subshell"))
        assertTrue(isComposerProfileDestination("u_tester"))
        assertFalse(isComposerProfileDestination("unixporn"))
    }

    // The posted post (and every post in a profile) shows u/<name>, not r/u_<name>.
    @Test
    fun aProfilePostIsLabelledUWhereverPostsShowTheirCommunity() {
        val state = SubshellState()
        assertEquals("u/tester", state.subredditLabel("u_tester"))
        assertEquals("r/linux", state.subredditLabel("linux"))
    }

    @Test
    fun theComposerPolicyAndTheSubmitUseTheProfilesCommunity() {
        assertEquals("u_tester", normalizeRedditTextPostCommunity(composerProfileDestination("tester")))
        assertEquals(listOf("r", "u_tester", "about"), redditComposerAboutRequest("u_tester").pathSegments)
    }

    @Test
    fun thePickerShowsYourProfileAndPicksIt() {
        var picked: ComposerCommunityOption? = null
        composeRule.setContent {
            V4CommunitySheet(
                query = "",
                onQueryChange = {},
                knownOptions = listOf(joined),
                remoteSearch = ComposerCommunitySearchSnapshot(),
                selectedCommunity = "subshell",
                authenticated = true,
                onSelect = { picked = it },
                onDismiss = {},
                profileUsername = "tester",
            )
        }
        composeRule.onNodeWithText("Your profile", substring = true).assertExists()
        composeRule.onNodeWithText("u/tester").performClick()
        composeRule.runOnIdle { assertEquals("u_tester", picked?.name) }
    }
}
