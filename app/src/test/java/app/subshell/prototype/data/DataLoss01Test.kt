package app.subshell.prototype.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.data.drafts.Crosspost01A1AMemoryPreferences
import app.subshell.prototype.data.drafts.TextPostSubmitAttemptStore
import app.subshell.prototype.data.moderation.ModeratorSavedResponse
import app.subshell.prototype.data.moderation.ModeratorSavedResponseStore
import app.subshell.prototype.data.usertags.keepUnreadableUserTags
import app.subshell.prototype.data.usertags.unreadableUserTagsKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DATALOSS01: stored data that could not be read was treated as empty, and
 * the next write replaced it for good (OCB-625, 629, 643, 648).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataLoss01Test {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    @Test
    fun oneUnreadableAttemptKeepsTheOtherAccountsAttemptsAndItself() {
        val prefs = Crosspost01A1AMemoryPreferences()
        val store = TextPostSubmitAttemptStore(prefs.preferences) { 1_000L }
        store.stage(request("reddit:a"))
        store.stage(request("reddit:b"))
        val key = prefs.values.keys.single()
        val rows = Json.parseToJsonElement(prefs.values.getValue(key)!!).jsonArray
        val broken = JsonObject(rows[0].jsonObject + ("crosspostFullname" to JsonPrimitive("not a fullname")))
        prefs.values[key] = JsonArray(listOf(broken, rows[1])).toString()

        assertEquals("reddit:b", store.load("reddit:b")?.request?.accountStableId)
        store.stage(request("reddit:c"))

        assertNotNull(store.load("reddit:b"))
        assertNotNull(store.load("reddit:c"))
        assertTrue(prefs.values.getValue(key)!!.contains("not a fullname"))
    }

    @Test
    fun anUnreadableSavedResponsesValueIsCopiedAsideBeforeASave() {
        val prefs = Crosspost01A1AMemoryPreferences()
        prefs.values[ModeratorSavedResponseStore.KEY] = "{broken"
        val store = ModeratorSavedResponseStore(prefs.preferences)

        store.save(listOf(ModeratorSavedResponse("r1", "Title", "Body", 1L)))

        assertEquals("{broken", prefs.values["moderator_saved_responses.unreadable"])
        assertEquals(1, store.load().size)
    }

    @Test
    fun readableSavedResponsesAreNotCopiedAside() {
        val prefs = Crosspost01A1AMemoryPreferences()
        val store = ModeratorSavedResponseStore(prefs.preferences)
        store.save(listOf(ModeratorSavedResponse("r1", "Title", "Body", 1L)))
        store.save(listOf(ModeratorSavedResponse("r2", "Other", "Body", 2L)))

        assertNull(prefs.values["moderator_saved_responses.unreadable"])
    }

    @Test
    fun unreadableUserTagsAreCopiedAsideBeforeTheyAreReplaced() {
        val preferences = application.getSharedPreferences("dataloss01-tags", Context.MODE_PRIVATE)
        val key = "user_tags:reddit:a"
        preferences.edit().clear().putString(key, "[not an object]").commit()

        preferences.edit().keepUnreadableUserTags(preferences, key).putString(key, "{}").commit()
        preferences.edit().keepUnreadableUserTags(preferences, key).putString(key, "{\"x\":{}}").commit()

        assertEquals("[not an object]", preferences.getString(unreadableUserTagsKey(key), null))
    }

    private fun request(account: String) = TextPostWriteRequest(
        accountStableId = account,
        community = "subshell",
        title = "Title",
        text = "Body",
    )
}
