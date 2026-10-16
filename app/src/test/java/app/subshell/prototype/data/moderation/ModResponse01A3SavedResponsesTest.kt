package app.subshell.prototype.data.moderation

import app.subshell.prototype.data.drafts.Crosspost01A1AMemoryPreferences
import app.subshell.prototype.data.account.RedditRemovalMessageType
import app.subshell.prototype.ui.moderatorRemovalMessageTypeKey
import app.subshell.prototype.ui.state.ModeratorResponseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModResponse01A3SavedResponsesTest {
    @Test
    fun storeRoundTripsNormalizesAndSurvivesGarbage() {
        val memory = Crosspost01A1AMemoryPreferences()
        val store = ModeratorSavedResponseStore(memory.preferences)
        assertTrue(store.load().isEmpty())

        store.save(
            listOf(
                ModeratorSavedResponse("b", "Zeta", "later body", updatedAtEpochMs = 5L),
                ModeratorSavedResponse("a", "alpha", "body", updatedAtEpochMs = 1L),
                ModeratorSavedResponse("a", "alpha dup", "dup", updatedAtEpochMs = 9L),
            ),
        )
        assertEquals(listOf("alpha", "Zeta"), store.load().map(ModeratorSavedResponse::title))

        memory.values[ModeratorSavedResponseStore.KEY] = "{not json"
        assertTrue(store.load().isEmpty())
        memory.values[ModeratorSavedResponseStore.KEY] = """[{"id":"x","title":"","body":"b","updatedAtEpochMs":1}]"""
        assertTrue("invalid rows are dropped, not fatal", store.load().isEmpty())
    }

    @Test
    fun stateSavesReplacesByTitleDeletesAndPersistsEveryChange() {
        val persisted = mutableListOf<List<ModeratorSavedResponse>>()
        var now = 100L
        var ids = 0
        val state = ModeratorResponseState(
            initialResponses = emptyList(),
            persist = { persisted += it },
            currentTimeMillis = { now },
            newId = { "id-${++ids}" },
        )

        assertNull("blank title is rejected", state.save("  ", "body"))
        assertNull("blank body is rejected", state.save("Rule 1", "   "))
        assertTrue(persisted.isEmpty())

        val first = state.save("  Rule 1 ", "  Please read rule 1.  ")!!
        assertEquals(ModeratorSavedResponse("id-1", "Rule 1", "Please read rule 1.", 100L), first)
        now = 200L
        val replaced = state.save("rule 1", "Updated text")!!
        assertEquals("id-1", replaced.id)
        assertEquals("the newest spelling of the title wins", listOf("rule 1"), state.responses.map(ModeratorSavedResponse::title))
        assertEquals("Updated text", state.responses.single().body)

        state.save("Rule 2", "Two")
        assertEquals(listOf("rule 1", "Rule 2"), state.responses.map(ModeratorSavedResponse::title))
        state.delete("id-1")
        state.delete("missing")
        assertEquals(listOf("Rule 2"), state.responses.map(ModeratorSavedResponse::title))
        assertEquals(4, persisted.size)
        assertEquals(state.responses.toList(), persisted.last())
    }

    @Test
    fun lastUsedMessageTypeIsPerAccountDeviceLocalAndDefaultsToPrivate() {
        val persisted = mutableListOf<Pair<String, RedditRemovalMessageType>>()
        val state = ModeratorResponseState(
            initialResponses = emptyList(),
            persist = {},
            currentTimeMillis = { 1L },
            initialMessageTypesByAccount = mapOf("acct-a" to RedditRemovalMessageType.PUBLIC),
            persistMessageType = { account, type -> persisted += account to type },
        )
        assertEquals(RedditRemovalMessageType.PUBLIC, state.messageTypeFor("acct-a"))
        assertEquals(RedditRemovalMessageType.PRIVATE, state.messageTypeFor("acct-b"))

        state.rememberMessageType("acct-b", RedditRemovalMessageType.PRIVATE_EXPOSED)
        state.rememberMessageType("acct-b", RedditRemovalMessageType.PRIVATE_EXPOSED)
        state.rememberMessageType("  ", RedditRemovalMessageType.PUBLIC)
        assertEquals(RedditRemovalMessageType.PRIVATE_EXPOSED, state.messageTypeFor("acct-b"))
        assertEquals(RedditRemovalMessageType.PUBLIC, state.messageTypeFor("acct-a"))
        assertEquals(listOf("acct-b" to RedditRemovalMessageType.PRIVATE_EXPOSED), persisted)

        // The preference key is device-local: not a portable settings key.
        assertEquals("mod_removal_message_type:acct-b", moderatorRemovalMessageTypeKey("acct-b"))
        assertTrue(app.subshell.prototype.ui.SubshellSettings.storageKeys.none { it.startsWith("mod_removal_message_type") })
    }

    @Test
    fun listIsBounded() {
        val state = ModeratorResponseState(
            initialResponses = emptyList(),
            persist = {},
            currentTimeMillis = { 1L },
        )
        repeat(ModeratorSavedResponse.MAX_RESPONSES) { index -> state.save("Title $index", "body") }
        assertNull("the limit refuses a new title", state.save("One too many", "body"))
        assertEquals(ModeratorSavedResponse.MAX_RESPONSES, state.responses.size)
        assertTrue("an existing title can still be updated", state.save("Title 0", "new") != null)
    }
}
