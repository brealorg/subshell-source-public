package app.subshell.prototype.backup

import app.subshell.prototype.ui.SubshellSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BACKUP06 (review of the pre-Claude backup package, finding 10, owner
 * decision): a corrupt userTags or moderatorSavedResponses module leaves the
 * current data unchanged, like every other module. It used to decode as
 * "authoritatively empty" and wipe all tags or saved responses on restore.
 * A present, genuinely empty module still clears.
 */
class Backup06MalformedModuleTest {
    @Test
    fun userTagsOfTheWrongTypeAreMissingNotEmpty() {
        assertNull(decodeWith("userTags", JsonArray(emptyList())).userTagsByAccount)
        assertNull(decodeWith("userTags", JsonPrimitive("oops")).userTagsByAccount)
        assertEquals(emptyMap<String, Any>(), decodeWith("userTags", JsonObject(emptyMap())).userTagsByAccount)
    }

    @Test
    fun savedResponsesOfTheWrongTypeOrWithOnlyUnreadableRowsAreMissing() {
        assertNull(decodeWith("moderatorSavedResponses", JsonObject(emptyMap())).moderatorSavedResponses)
        assertNull(
            decodeWith(
                "moderatorSavedResponses",
                JsonArray(listOf(JsonPrimitive("row"), JsonObject(mapOf("id" to JsonPrimitive("x"))))),
            ).moderatorSavedResponses,
        )
        assertEquals(
            emptyList<PortableModeratorSavedResponse>(),
            decodeWith("moderatorSavedResponses", JsonArray(emptyList())).moderatorSavedResponses,
        )
    }

    @Test
    fun readableSavedResponsesSurviveNextToUnreadableRows() {
        val valid = JsonObject(
            mapOf(
                "id" to JsonPrimitive("a"),
                "title" to JsonPrimitive("Alpha"),
                "body" to JsonPrimitive("Body"),
                "updatedAtEpochMs" to JsonPrimitive(5L),
            ),
        )
        val decoded = decodeWith("moderatorSavedResponses", JsonArray(listOf(JsonPrimitive("row"), valid)))
        assertEquals(listOf("a"), decoded.moderatorSavedResponses?.map(PortableModeratorSavedResponse::id))
    }

    private fun decodeWith(module: String, value: JsonElement): SettingsSnapshot {
        val encoded = SettingsSnapshotCodec.encode(
            SettingsSnapshot(
                id = "backup-1",
                name = "Backup",
                createdAtEpochMillis = 1L,
                appVersion = "0.78.0",
                kind = SnapshotKind.MANUAL,
                deviceId = "device",
                settings = SubshellSettings(),
            ),
        )
        val root = Json.parseToJsonElement(encoded).jsonObject
        return SettingsSnapshotCodec.decode(JsonObject(root + (module to value)).toString())
    }
}
