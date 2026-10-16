package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.isValidRedditUsername
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.markdownLinkTargetsForRenderedText
import app.subshell.prototype.ui.screens.UserProfileScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE46_REDDIT_USERNAME_LENGTH_V1: a 1-2 character username used to crash
 * the signed-in profile. Reddit names are 3..20 characters; user/me is the
 * own profile; anything invalid that still arrives is shown, not thrown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue46ShortUsernameTest {
    private val composeRule = createComposeRule()

    // The Robolectric "lightZ ... Infinity" display flake hit this class once
    // (2026-10-04); the guard restores the display before the test.
    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val me = AccountSessionSummary(
        stableId = "reddit:someone",
        username = "someone",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(me),
        initialActiveAccountId = me.stableId,
        initialInteractionSideConfigured = true,
    )

    @Test
    fun linksToNamesRedditCannotHaveAreNotProfiles() {
        assertNull(resolveRedditTarget("https://www.reddit.com/user/ab"))
        assertNull(resolveRedditTarget("/u/x"))
        assertEquals(RedditTarget.User("abc"), resolveRedditTarget("/u/abc"))
        assertEquals(RedditTarget.User("me"), resolveRedditTarget("https://www.reddit.com/user/me"))
    }

    @Test
    fun aShortMentionStaysText() {
        val text = "ask u/ab or u/abc"
        val links = markdownLinkTargetsForRenderedText(text, text).map { text.substring(it.start, it.end) }
        assertEquals(listOf("u/abc"), links)
    }

    @Test
    fun userMeOpensTheOwnProfileWhenSignedIn() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = signedIn()

        assertTrue(state.openRedditTarget(RedditTarget.User("me")))
        assertEquals("someone", state.selectedUser)
    }

    @Test
    fun anInvalidNameIsShownInsteadOfCrashing() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = signedIn()
        assertFalse(isValidRedditUsername("ab"))

        composeRule.setContent { SubshellTheme { UserProfileScreen(username = "ab", state = state) } }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("This user can't be shown").fetchSemanticsNodes().size)
    }
}
