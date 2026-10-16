package app.subshell.prototype.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.ui.app.CustomFeedLinkEffect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REDDITURL04a3: a custom feed link opens the account's own feed as the
 * drawer would, and any other feed under its path name until Reddit's
 * metadata replaces it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomFeedLinkEffectComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val me = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun feed(path: String, name: String) = CustomFeedSummary(
        path = path,
        displayName = name,
        description = "",
        visibility = "public",
        subredditNames = listOf("rust", "golang"),
    )

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(me),
        initialActiveAccountId = me.stableId,
        initialInteractionSideConfigured = true,
    )

    @Test
    fun anotherUsersFeedOpensUnderItsPathNameThenTakesRedditsMetadata() {
        val state = signedIn()
        val activePath = mutableStateOf<String?>(null)
        val linked = mutableStateOf<CustomFeedSummary?>(null)
        var pendingResolve: ((CustomFeedSummary?) -> Unit)? = null
        val resolveCalls = mutableListOf<String>()
        composeRule.setContent {
            CustomFeedLinkEffect(
                state = state,
                customFeedsState = CustomFeedsUiState(accountStableId = me.stableId),
                activeCustomFeedPathState = activePath,
                linkedCustomFeedState = linked,
                onResolveCustomFeed = { _, path, done ->
                    resolveCalls += path
                    pendingResolve = done
                },
            )
        }

        state.openRedditTarget(RedditTarget.CustomFeed("someone", "systems"))
        composeRule.waitForIdle()

        assertEquals("/user/someone/m/systems/", activePath.value)
        assertEquals("systems", linked.value?.displayName)
        assertEquals(listOf("/user/someone/m/systems/"), resolveCalls)

        composeRule.runOnIdle { pendingResolve?.invoke(feed("/user/someone/m/systems", "Systems programming")) }
        composeRule.waitForIdle()

        assertEquals("Systems programming", linked.value?.displayName)
        assertEquals("/user/someone/m/systems/", linked.value?.path)
    }

    @Test
    fun anOwnFeedOpensWithTheListsPathAndNeedsNoRead() {
        val state = signedIn()
        val activePath = mutableStateOf<String?>(null)
        val linked = mutableStateOf<CustomFeedSummary?>(null)
        val resolveCalls = mutableListOf<String>()
        composeRule.setContent {
            CustomFeedLinkEffect(
                state = state,
                customFeedsState = CustomFeedsUiState(
                    accountStableId = me.stableId,
                    feeds = listOf(feed("/user/me/m/Mine/", "Mine")),
                ),
                activeCustomFeedPathState = activePath,
                linkedCustomFeedState = linked,
                onResolveCustomFeed = { _, path, _ -> resolveCalls += path },
            )
        }

        state.openRedditTarget(RedditTarget.CustomFeed(null, "mine"))
        composeRule.waitForIdle()

        assertEquals("/user/me/m/Mine/", activePath.value)
        assertEquals(null, linked.value)
        assertEquals(emptyList<String>(), resolveCalls)
    }
}
