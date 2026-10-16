package app.subshell.prototype.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditModmailMessage
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.USER_TAG_LABEL_MAX_LENGTH
import app.subshell.prototype.ui.UserTag
import app.subshell.prototype.ui.UserTagColor
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE34_USER_TAGS_V1: the tag chip and the note entry points.
 * The editor's fields are tested without their AlertDialog: under this
 * Robolectric harness any text field inside a dialog window keeps Compose
 * from ever going idle (even a bare BasicTextField with the clock paused),
 * so the dialog wrapper itself is device-verified only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue34UserTagsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val stored = mutableMapOf<String, Map<String, UserTag>>()

    private val post = Post(
        id = "t3_tagged",
        community = "android",
        author = "alice",
        age = "1h",
        title = "A post by alice",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    @Test
    fun aTaggedAuthorShowsTheChipOnThePostCardAndItFollowsEdits() {
        stored[account.stableId] = mapOf("alice" to UserTag("friend", UserTagColor.GREEN))
        val state = state(listOf(account))
        composeRule.setContent { SubshellTheme { PostCard(post = post, state = state, onOpen = {}) } }

        composeRule.onNodeWithContentDescription("Your tag: friend").assertIsDisplayed()

        composeRule.runOnIdle { state.setUserTag("alice", UserTag(note = "only a note")) }
        composeRule.onNodeWithContentDescription("Your tag: Note").assertIsDisplayed()
        composeRule.runOnIdle { state.setUserTag("alice", null) }
        composeRule.onNodeWithContentDescription("Your tag: Note").assertDoesNotExist()
    }

    @Test
    fun thePostMenuOffersANoteWhenSignedInButNeverForYourselfOrDeletedAuthors() {
        val signedIn = state(listOf(account))
        composeRule.setContent { SubshellTheme { PostCard(post = post, state = signedIn, onOpen = {}) } }
        composeRule.onNodeWithContentDescription("Post actions").performClick()
        composeRule.onNodeWithText("Private note on u/alice").assertIsDisplayed()

        assertTrue(signedIn.canTagUser("u/Alice"))
        assertFalse(signedIn.canTagUser(account.username.uppercase()))
        assertFalse(signedIn.canTagUser("[deleted]"))
    }

    @Test
    fun signedOutThePostMenuHasNoNoteItem() {
        val state = state(localFixtureAccountSummaries(listOf("local")))
        composeRule.setContent { SubshellTheme { PostCard(post = post, state = state, onOpen = {}) } }

        composeRule.onNodeWithContentDescription("Post actions").performClick()
        composeRule.onNodeWithText("About u/alice").assertIsDisplayed()
        composeRule.onNodeWithText("Private note on u/alice").assertDoesNotExist()
        assertFalse(state.canTagUser("alice"))
    }

    @Test
    fun editorFieldsKeepTheTagOnOneLineCapItAndSelectAColour() {
        var label by mutableStateOf("")
        var color by mutableStateOf(UserTagColor.GREY)
        var note by mutableStateOf("")
        composeRule.setContent {
            SubshellTheme {
                UserTagEditorFields(
                    label = label,
                    onLabelChange = { label = it },
                    color = color,
                    onColorChange = { color = it },
                    note = note,
                    onNoteChange = { note = it },
                )
            }
        }

        composeRule.onNode(hasSetTextAction() and hasText("Tag"))
            .performTextReplacement("good\nperson " + "x".repeat(40))
        composeRule.runOnIdle {
            assertEquals(USER_TAG_LABEL_MAX_LENGTH, label.length)
            assertFalse(label.contains('\n'))
            assertTrue(label.startsWith("good person"))
        }
        composeRule.onNode(hasSetTextAction() and hasText("Note")).performTextReplacement("Knows Compose")
        composeRule.onNodeWithContentDescription("Blue").performClick()
        composeRule.onNodeWithContentDescription("Blue").assertIsSelected()
        // LOWUI7_TOUCH_TARGETS_ARE_48DP_V1 (OCB-165)
        composeRule.onNodeWithContentDescription("Blue")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.runOnIdle {
            assertEquals(UserTagColor.BLUE, color)
            assertEquals("Knows Compose", note)
        }
    }

    @Test
    fun theProfileSectionShowsTheNoteAndOffersAddOrEdit() {
        var tag by mutableStateOf<UserTag?>(UserTag("friend", UserTagColor.RED, "Met in r/android"))
        var edits = 0
        composeRule.setContent {
            SubshellTheme { UserTagProfileSection(tag = tag, onEdit = { edits++ }) }
        }

        composeRule.onNodeWithContentDescription("Your tag: friend").assertIsDisplayed()
        composeRule.onNodeWithText("Met in r/android").assertIsDisplayed()
        composeRule.onNodeWithText("Edit private note").performClick()
        composeRule.runOnIdle { assertEquals(1, edits) }

        composeRule.runOnIdle { tag = null }
        composeRule.onNodeWithText("Add private note").assertIsDisplayed()
        composeRule.onNodeWithText("Met in r/android").assertDoesNotExist()
    }

    @Test
    fun modmailShowsTheTagNextToAPersonButNeverNextToTheCommunityLabel() {
        val tag = UserTag("regular", UserTagColor.TEAL)
        var message by mutableStateOf(
            RedditModmailMessage(
                id = "m1",
                authorName = "alice",
                body = "Hello mods",
                createdEpochSeconds = null,
                isInternal = false,
                participatingAs = "participant_user",
            ),
        )
        composeRule.setContent {
            SubshellTheme {
                InboxModmailMessageRow(message = message, communityName = "android", authorTag = tag)
            }
        }
        composeRule.onNodeWithText("u/alice").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Your tag: regular").assertIsDisplayed()

        composeRule.runOnIdle { message = message.copy(authorHidden = true) }
        composeRule.onNodeWithText("r/android").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Your tag: regular").assertDoesNotExist()
    }

    @Test
    fun aNameWithoutATagDrawsNoChip() {
        composeRule.setContent {
            SubshellTheme { NameWithUserTag(tag = null) { androidx.compose.material3.Text("bob") } }
        }
        composeRule.onNodeWithText("bob").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Your tag: Note").assertDoesNotExist()
    }

    private fun state(accounts: List<AccountSessionSummary>): SubshellState = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = accounts,
        initialActiveAccountId = accounts.first().stableId,
        loadUserTags = { id -> stored[id].orEmpty() },
        persistUserTags = { id, tags -> stored[id] = tags },
    )
}
