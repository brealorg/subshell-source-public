package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A3ContentStateParserTest {
    @Test
    fun lockStateRequiresBooleanAndPermissionStateIsNotInvented() {
        val unlocked = moderatorContentState(
            locked = JsonPrimitive(false),
            distinguished = JsonNull,
            canModPost = JsonPrimitive(true),
        )
        assertEquals(false, unlocked.locked)
        assertEquals(false, unlocked.distinguished)
        assertEquals(true, unlocked.canModerate)

        val malformed = moderatorContentState(
            locked = JsonPrimitive("false"),
            distinguished = JsonPrimitive("moderator"),
            canModPost = JsonPrimitive("true"),
        )
        assertNull(malformed.locked)
        assertEquals(true, malformed.distinguished)
        assertNull(malformed.canModerate)
    }

    @Test
    fun explicitNullMeansNotDistinguishedButOmittedFieldStaysUnknown() {
        val explicitNull = moderatorContentState(
            locked = JsonPrimitive(false),
            distinguished = JsonNull,
            canModPost = JsonPrimitive(true),
        )
        assertEquals(false, explicitNull.distinguished)

        val omitted = moderatorContentState(
            locked = JsonPrimitive(false),
            distinguished = MISSING_MODERATOR_DISTINGUISHED_FIELD,
            canModPost = JsonPrimitive(true),
        )
        assertNull(omitted.distinguished)
    }

    @Test
    fun typedDtoPreservesOmittedVersusExplicitNullDistinguished() {
        val json = SubshellNetworkJson.providerJson
        fun data(payload: String) = json.decodeFromString(
            RedditModerationThingDataDto.serializer(),
            payload,
        )

        val omitted = data("""{"locked":false,"can_mod_post":true}""")
        val explicit = data("""{"locked":false,"can_mod_post":true,"distinguished":null}""")
        assertEquals(MISSING_MODERATOR_DISTINGUISHED_FIELD, omitted.distinguished)
        assertEquals(JsonNull, explicit.distinguished)
        assertNull(
            moderatorContentState(omitted.locked, omitted.distinguished, omitted.canModPost).distinguished,
        )
        assertFalse(
            requireNotNull(
                moderatorContentState(explicit.locked, explicit.distinguished, explicit.canModPost).distinguished,
            ),
        )
    }

    @Test
    fun onlyModeratorStringIsAcceptedAsDistinguished() {
        assertTrue(
            requireNotNull(
                moderatorContentState(null, JsonPrimitive("moderator"), JsonPrimitive(true)).distinguished,
            ),
        )
        listOf("admin", "special", "false").forEach { raw ->
            assertNull(
                moderatorContentState(null, JsonPrimitive(raw), JsonPrimitive(true)).distinguished,
            )
        }
    }
}
