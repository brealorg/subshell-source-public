package app.subshell.prototype.ui.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.account.RedditModmailParticipantMuteStatus
import app.subshell.prototype.data.account.RedditModmailTriageAction
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (modmail02, modmail03a1r2, modmail04a1, modmail05, modmail06): an open
 * Modmail conversation. The header archives an outside conversation (never an
 * internal one), says Archived/Highlighted/Muted, and its triage menu marks
 * read or unread, highlights, and mutes only a real participant of an outside
 * conversation; an unclear archive or read change offers "Refresh to verify".
 * The composer replies as the mod team by default or as the moderator, writes
 * internal notes visible to moderators only, and has no image or GIF actions.
 * The queue field lists Reddit's ten Modmail queues.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ModmailThreadComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val events = mutableListOf<String>()

    private fun conversation(
        internal: Boolean = false,
        archived: Boolean = false,
        highlighted: Boolean = false,
        unread: Boolean = false,
        participant: String? = "someone",
    ) = RedditModmailConversation(
        id = "c1",
        communityName = "subshell",
        subject = "Open question",
        participantName = participant,
        preview = "Hello",
        lastUpdatedEpochSeconds = 1L,
        unread = unread,
        isInternal = internal,
        messageCount = 1,
        archived = archived,
        highlighted = highlighted,
    )

    private fun header(
        conversation: RedditModmailConversation,
        muted: Boolean = false,
        archiveInProgress: Boolean = false,
        archiveOutcomeUnknown: Boolean = false,
        readStateOutcomeUnknown: Boolean = false,
    ) {
        composeRule.setContent {
            SubshellTheme {
                InboxModmailThreadHeader(
                    conversation = conversation,
                    participantMuteStatus = RedditModmailParticipantMuteStatus(muted = muted),
                    archiveInProgress = archiveInProgress,
                    archiveOutcomeUnknown = archiveOutcomeUnknown,
                    readStateOutcomeUnknown = readStateOutcomeUnknown,
                    onSetArchived = { events += "archived:$it" },
                    onSetUnread = { events += "unread:$it" },
                    onTriageAction = { events += "triage:$it" },
                    onVerifyArchived = { events += "verifyArchived" },
                    onVerifyReadState = { events += "verifyRead" },
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun click(text: String) {
        composeRule.onAllNodes(hasText(text) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun openTriageMenu() {
        composeRule.onAllNodes(hasContentDescription("Modmail triage actions")).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun anOutsideConversationArchivesAndAnArchivedOneUnarchives() {
        header(conversation(archived = true, highlighted = true), muted = true)

        assertEquals(1, count("r/subshell · u/someone · Archived · Highlighted · Muted"))
        click("Unarchive")

        assertEquals(listOf("archived:false"), events)
    }

    @Test
    fun anOpenOutsideConversationArchives() {
        header(conversation())

        click("Archive")

        assertEquals(listOf("archived:true"), events)
    }

    @Test
    fun anInternalConversationCannotBeArchivedOrMuted() {
        header(conversation(internal = true))

        assertEquals(0, count("Archive"))
        openTriageMenu()
        assertEquals(0, composeRule.onAllNodesWithText("Mute user", substring = true).fetchSemanticsNodes().size)
        assertEquals(1, count("Highlight"))
    }

    @Test
    fun theTriageMenuMarksReadHighlightsAndMutes() {
        header(conversation(unread = true))
        openTriageMenu()

        click("Mark read")
        openTriageMenu()
        click("Highlight")
        openTriageMenu()
        click("Mute user · 3 days")
        openTriageMenu()
        click("Mute user · 7 days")
        openTriageMenu()
        click("Mute user · 28 days")

        assertEquals(
            listOf(
                "unread:false",
                "triage:${RedditModmailTriageAction.HIGHLIGHT}",
                "triage:${RedditModmailTriageAction.MUTE_3_DAYS}",
                "triage:${RedditModmailTriageAction.MUTE_7_DAYS}",
                "triage:${RedditModmailTriageAction.MUTE_28_DAYS}",
            ),
            events,
        )
    }

    @Test
    fun aHighlightedReadConversationOfAMutedUserOffersTheOpposites() {
        header(conversation(highlighted = true), muted = true)
        openTriageMenu()

        assertEquals(0, count("Mute user · 3 days"))
        click("Mark unread")
        openTriageMenu()
        click("Remove highlight")
        openTriageMenu()
        click("Unmute user")

        assertEquals(
            listOf(
                "unread:true",
                "triage:${RedditModmailTriageAction.UNHIGHLIGHT}",
                "triage:${RedditModmailTriageAction.UNMUTE}",
            ),
            events,
        )
    }

    @Test
    fun aConversationWithoutAParticipantCannotMute() {
        header(conversation(participant = null))
        openTriageMenu()

        assertEquals(0, composeRule.onAllNodesWithText("Mute user", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun anUnclearArchiveOffersRefreshToVerify() {
        header(conversation(), archiveOutcomeUnknown = true)

        assertEquals(0, count("Archive"))
        click("Refresh to verify")

        assertEquals(listOf("verifyArchived"), events)
    }

    @Test
    fun anUnclearReadChangeOffersRefreshToVerify() {
        header(conversation(), readStateOutcomeUnknown = true)

        click("Refresh to verify")

        assertEquals(listOf("verifyRead"), events)
    }

    @Test
    fun anArchiveInProgressOffersNoButtons() {
        header(conversation(), archiveInProgress = true, archiveOutcomeUnknown = true)

        assertEquals(0, count("Archive"))
        assertEquals(0, count("Refresh to verify"))
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Modmail triage actions").fetchSemanticsNodes().size)
    }

    private val sends = mutableListOf<Triple<String, Boolean, Boolean>>()

    private fun composer() {
        composeRule.setContent {
            SubshellTheme {
                InboxModmailComposer(
                    conversation = conversation(),
                    moderatorUsername = "mod_user",
                    sendInProgress = false,
                    sendOutcomeUnknown = false,
                    sendRevision = 0L,
                    onSend = { body, internal, hidden -> sends += Triple(body, internal, hidden) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun typeAndSend(text: String, sendLabel: String = "Send Modmail reply") {
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text)
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasContentDescription(sendLabel)).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun aReplyGoesAsTheModTeamByDefault() {
        composer()

        assertTrue(composeRule.onAllNodesWithText("Replying as").fetchSemanticsNodes().isNotEmpty())
        assertTrue(count("r/subshell mod team") >= 1)
        assertEquals(0, count("Image"))
        assertEquals(0, count("GIF"))

        typeAndSend("Hi")

        assertEquals(listOf(Triple("Hi", false, true)), sends)
    }

    @Test
    fun aReplyCanGoAsTheModerator() {
        composer()

        composeRule.onAllNodes(hasText("r/subshell mod team")).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        click("u/mod_user")
        typeAndSend("Hi")

        assertEquals(listOf(Triple("Hi", false, false)), sends)
    }

    @Test
    fun anInternalNoteIsForModeratorsOnly() {
        composer()

        click("Internal note")
        assertEquals(1, count("Visible to moderators only"))
        typeAndSend("Note", sendLabel = "Add internal note")

        assertEquals(listOf(Triple("Note", true, false)), sends)
    }

    @Test
    fun theQueueFieldListsRedditsTenQueues() {
        val selected = mutableListOf<RedditModmailListMode>()
        composeRule.setContent {
            SubshellTheme {
                InboxModmailQueueFilters(
                    selectedMode = RedditModmailListMode.ACTIVE,
                    enabled = true,
                    onSelect = { selected += it },
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, count("Queue"))
        assertEquals(1, count("Active"))

        assertEquals(1, composeRule.onAllNodesWithContentDescription("Choose Modmail queue").fetchSemanticsNodes().size)
        // The whole field is the click target; the icon sits inside it.
        composeRule.onAllNodes(hasClickAction() and hasText("Queue")).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(1, count("Modmail queue"))
        for (label in listOf(
            "New", "In progress", "Filtered", "Highlighted", "Archived",
            "Appeals", "Join requests", "Mod discussions", "Notifications",
        )) {
            assertTrue(label, count(label) >= 1)
        }
        click("Archived")

        assertEquals(listOf(RedditModmailListMode.ARCHIVED), selected)
    }

    @Test
    fun aDisabledQueueFieldCannotOpen() {
        composeRule.setContent {
            SubshellTheme {
                InboxModmailQueueFilters(
                    selectedMode = RedditModmailListMode.ACTIVE,
                    enabled = false,
                    onSelect = {},
                )
            }
        }
        composeRule.waitForIdle()

        val field = composeRule.onAllNodes(hasClickAction() and hasText("Queue")).fetchSemanticsNodes()
        assertTrue(field.all { it.config.getOrNull(SemanticsProperties.Disabled) != null })
    }
}
