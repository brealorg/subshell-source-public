package app.subshell.prototype.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextRange
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.model.RedditPeopleSearchItem
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #229: while `@` or `u/` is being typed the reply composer lists people from
 * the thread; a tap writes `u/name ` and the list goes; from two letters on it
 * asks Reddit after a short pause and lists the answer for that query; a
 * draft that may not search never asks, and a locked reply shows nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue229MentionSuggestionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val searches = mutableListOf<String>()
    private var remote by mutableStateOf(RedditPeopleSearchUiState())

    private fun typed(text: String) = TextFieldState(text, TextRange(text.length))

    private fun show(
        state: TextFieldState,
        thread: List<String>,
        searchEnabled: Boolean = true,
        locked: Boolean = false,
    ) {
        composeRule.setContent {
            SubshellTheme {
                CompactReplyComposer(
                    state = state,
                    media = null,
                    placeholder = "Reply",
                    sendEnabled = true,
                    onSend = {},
                    onMediaReceived = {},
                    onMediaRemoved = {},
                    onPickImage = {},
                    onPickGif = {},
                    mediaEnabled = true,
                    interactionActionsAtStart = false,
                    inputLocked = locked,
                    mentions = ComposerMentions(
                        thread = thread,
                        remote = remote,
                        searchEnabled = searchEnabled,
                        onSearch = { searches += it },
                    ),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun shown(name: String) =
        composeRule.onAllNodesWithContentDescription("Mention u/$name").fetchSemanticsNodes().size

    @Test
    fun aThreadNameIsWrittenAsAMentionAndTheListGoes() {
        val state = typed("Thanks @bo")
        show(state, thread = listOf("bob", "alice"))

        assertEquals(1, shown("bob"))
        assertEquals(0, shown("alice"))

        composeRule.onNodeWithContentDescription("Mention u/bob").performClick()
        composeRule.waitForIdle()

        assertEquals("Thanks u/bob ", state.text.toString())
        assertEquals(TextRange(13), state.selection)
        assertEquals(0, composeRule.onAllNodes(hasContentDescription("Mention u/", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun redditIsAskedAfterAPauseAndItsAnswerForThatQueryIsListed() {
        val state = typed("u/ca")
        show(state, thread = emptyList())

        composeRule.mainClock.advanceTimeBy(MENTION_SEARCH_DEBOUNCE_MS + 100L)
        composeRule.waitForIdle()
        assertEquals(listOf("ca"), searches)

        // An answer for another query is not shown.
        remote = RedditPeopleSearchUiState(searchQuery = "cx", items = listOf(person("cxavier")), loaded = true)
        composeRule.waitForIdle()
        assertEquals(0, shown("cxavier"))

        remote = RedditPeopleSearchUiState(searchQuery = "ca", items = listOf(person("carol")), loaded = true)
        composeRule.waitForIdle()
        assertEquals(1, shown("carol"))
    }

    @Test
    fun aDraftThatMayNotSearchKeepsTheThreadButNeverAsksReddit() {
        show(typed("@al"), thread = listOf("alice"), searchEnabled = false)

        composeRule.mainClock.advanceTimeBy(MENTION_SEARCH_DEBOUNCE_MS + 100L)
        composeRule.waitForIdle()

        assertTrue(searches.isEmpty())
        assertEquals(1, shown("alice"))
    }

    @Test
    fun aLockedReplyShowsNoSuggestions() {
        show(typed("@al"), thread = listOf("alice"), locked = true)

        assertEquals(0, shown("alice"))
    }

    private fun person(name: String) = RedditPeopleSearchItem(username = name, title = name, description = "")
}
