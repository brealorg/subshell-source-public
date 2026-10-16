package app.subshell.prototype.data.drafts

import app.subshell.prototype.ui.ComposerFlair
import app.subshell.prototype.ui.ComposerPostType
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.crosspost01A1CDraft
import app.subshell.prototype.ui.crosspost01A1CPolicy
import app.subshell.prototype.ui.toCrosspostWriteRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Crosspost01A1CDraftStorePolicyTest {
    private val ordinaryKey = "post_composer_drafts.v1"
    private val crosspostKey = "post_composer_crosspost_drafts.v1"

    @Test
    fun sourceOwnerDestinationAndNativeRequestKeySurviveRealStoreRecreation() {
        val memory = Crosspost01A1AMemoryPreferences()
        val draft = crosspost01A1CDraft().copy(title = "Custom title", sendReplies = false, flair = ComposerFlair("f", "News"))
        val policy = crosspost01A1CPolicy().copy(flairOptions = listOf(draft.flair!!))
        val before = requireNotNull(draft.toCrosspostWriteRequest("owner", true, policy))
        ComposerDraftStore(memory.preferences).save(listOf(draft))
        val restored = ComposerDraftStore(memory.preferences).load().single()
        assertEquals(draft, restored)
        assertEquals(before.requestKey, restored.toCrosspostWriteRequest("owner", true, policy)?.requestKey)
        assertEquals("t3_source123", restored.crosspostSource?.fullname)
    }

    @Test
    fun crosspostsAreNeverWrittenToTheLegacyUnknownTypeFallbackKey() {
        val memory = Crosspost01A1AMemoryPreferences()
        val crosspost = crosspost01A1CDraft()
        val ordinary = PostComposerDraft(id = "ordinary", title = "Ordinary", accountStableId = "owner")
        ComposerDraftStore(memory.preferences).save(listOf(crosspost, ordinary))
        val legacy = Json.parseToJsonElement(requireNotNull(memory.values[ordinaryKey])).jsonArray
        val sourceBound = Json.parseToJsonElement(requireNotNull(memory.values[crosspostKey])).jsonArray
        assertEquals(1, legacy.size)
        assertEquals(JsonPrimitive("TEXT"), legacy.single().jsonObject["type"])
        assertEquals(JsonPrimitive("CROSSPOST"), sourceBound.single().jsonObject["type"])
        assertFalse(memory.values[ordinaryKey]!!.contains("t3_source123"))
        assertEquals(2, ComposerDraftStore(memory.preferences).load().size)
    }

    @Test
    fun literalLegacyDraftWithoutSourceFieldsStillLoadsUnchanged() {
        val memory = Crosspost01A1AMemoryPreferences()
        // Literal pre-A1C record, not a current-encoder suffix/truncation fixture.
        memory.values[ordinaryKey] = """
          [{"id":"legacy","accountStableId":"owner","community":"target","type":"TEXT",
            "title":"Legacy title","body":"Legacy body","linkUrl":"","inputMode":"MARKDOWN",
            "media":[],"flair":null,"nsfw":false,"spoiler":false,"sendReplies":true,
            "createdAtEpochMs":1000,"updatedAtEpochMs":2000}]
        """.trimIndent()
        val draft = ComposerDraftStore(memory.preferences).load().single()
        assertEquals("legacy", draft.id)
        assertEquals("Legacy body", draft.body)
        assertEquals(ComposerPostType.TEXT, draft.type)
        assertNull(draft.crosspostSource)
    }

    @Test
    fun malformedCrosspostRowDoesNotEraseUnrelatedValidDraftsOrBecomeText() {
        val memory = Crosspost01A1AMemoryPreferences()
        val good = crosspost01A1CDraft().copy(id = "good")
        ComposerDraftStore(memory.preferences).save(listOf(good, PostComposerDraft(id = "ordinary", title = "Ordinary")))
        val row = Json.parseToJsonElement(memory.values[crosspostKey]!!).jsonArray.single().jsonObject
        val badSource = JsonObject(row["crosspostSource"]!!.jsonObject + ("fullname" to JsonPrimitive("t1_comment")))
        val bad = JsonObject(row + mapOf("id" to JsonPrimitive("bad"), "crosspostSource" to badSource))
        memory.values[crosspostKey] = JsonArray(listOf(bad, row)).toString()
        assertEquals(setOf("good", "ordinary"), ComposerDraftStore(memory.preferences).load().map { it.id }.toSet())
    }

    @Test
    fun missingSourceWrongTypeAndCrosspostInLegacyKeyAreNotRestoredAsText() {
        val memory = Crosspost01A1AMemoryPreferences()
        ComposerDraftStore(memory.preferences).save(listOf(crosspost01A1CDraft()))
        val row = Json.parseToJsonElement(memory.values[crosspostKey]!!).jsonArray.single().jsonObject
        for (bad in listOf(
            JsonObject(row + ("crosspostSource" to JsonNull)),
            JsonObject(row + ("type" to JsonPrimitive("TEXT"))),
            JsonObject(row + ("accountStableId" to JsonNull)),
        )) {
            memory.values[crosspostKey] = JsonArray(listOf(bad)).toString()
            assertTrue(ComposerDraftStore(memory.preferences).load().isEmpty())
        }
        memory.values[ordinaryKey] = JsonArray(listOf(row)).toString()
        memory.values[crosspostKey] = "[]"
        assertTrue(ComposerDraftStore(memory.preferences).load().isEmpty())
    }

    @Test
    fun bothKeysAreStagedInTheSameEditorBeforeCommit() {
        val memory = Crosspost01A1AMemoryPreferences()
        val editor = memory.preferences.edit()
        val drafts = listOf(crosspost01A1CDraft(), PostComposerDraft(id = "ordinary", title = "Ordinary"))
        ComposerDraftStore(memory.preferences).stageRestore(editor, drafts)
        assertTrue(memory.values.isEmpty())
        assertTrue(editor.commit())
        assertNotNull(memory.values[ordinaryKey])
        assertNotNull(memory.values[crosspostKey])
        assertEquals(2, ComposerDraftStore(memory.preferences).load().size)
    }

    @Test
    fun invalidSaveCannotOverwriteAlreadyStoredDrafts() {
        val memory = Crosspost01A1AMemoryPreferences()
        val store = ComposerDraftStore(memory.preferences)
        store.save(listOf(crosspost01A1CDraft()))
        val before = memory.values.toMap()
        assertTrue(runCatching { store.save(listOf(crosspost01A1CDraft().copy(crosspostSource = null))) }.isFailure)
        assertTrue(runCatching { store.save(listOf(crosspost01A1CDraft().copy(type = ComposerPostType.TEXT))) }.isFailure)
        assertEquals(before, memory.values)
    }

    @Test
    fun deletingAllDraftsClearsBothLogicalCollections() {
        val memory = Crosspost01A1AMemoryPreferences()
        val store = ComposerDraftStore(memory.preferences)
        store.save(listOf(crosspost01A1CDraft(), PostComposerDraft(id = "ordinary", title = "Ordinary")))
        store.save(emptyList())
        assertEquals("[]", memory.values[ordinaryKey])
        assertEquals("[]", memory.values[crosspostKey])
        assertTrue(ComposerDraftStore(memory.preferences).load().isEmpty())
    }

    @Test
    fun ownerAndSourceSeparationSurvivesMixedAccountsAndTotalDraftLimit() {
        val memory = Crosspost01A1AMemoryPreferences()
        val drafts = (1..110).map { n ->
            crosspost01A1CDraft().copy(id = "draft-$n", accountStableId = "owner-$n", updatedAtEpochMs = n.toLong() + 10_000L)
        }
        ComposerDraftStore(memory.preferences).save(drafts)
        val restored = ComposerDraftStore(memory.preferences).load()
        assertEquals(100, restored.size)
        assertEquals("owner-110", restored.first().accountStableId)
        assertEquals("owner-11", restored.last().accountStableId)
    }
}
