package app.subshell.prototype.data.usertags

import app.subshell.prototype.backup.PortableUserTag
import app.subshell.prototype.backup.toPortableUserTag
import app.subshell.prototype.backup.toUserTag
import app.subshell.prototype.ui.UserTag
import app.subshell.prototype.ui.UserTagColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TAGCOLOR01 (OCB-644): a colour a newer build wrote became GREY on read and
 * every later save of that account's tags wrote GREY back for good.
 */
class TagColor01UnknownColourTest {
    @Test
    fun anUnknownColourShowsGreyAndIsWrittenBackAsItWas() {
        val stored = """{"alice":{"label":"friend","color":"FUTURE_VIOLET","note":""}}"""

        val decoded = decodeUserTags(stored)
        assertEquals(UserTagColor.GREY, decoded.getValue("alice").color)

        val withAnotherTag = decoded + ("bob" to UserTag(label = "new", color = UserTagColor.BLUE))
        val reencoded = encodeUserTags(withAnotherTag)

        assertTrue(reencoded, reencoded.contains("\"FUTURE_VIOLET\""))
        assertEquals("FUTURE_VIOLET", decodeUserTags(reencoded).getValue("alice").unknownColorName)
    }

    @Test
    fun aBackupKeepsAnUnknownColourToo() {
        val portable = PortableUserTag(label = "friend", color = "FUTURE_VIOLET", note = "")

        val tag = portable.toUserTag()

        assertEquals(UserTagColor.GREY, tag.color)
        assertEquals("FUTURE_VIOLET", tag.toPortableUserTag().color)
    }

    @Test
    fun aKnownColourHasNothingExtraToKeep() {
        val tag = decodeUserTags("""{"alice":{"label":"x","color":"BLUE","note":""}}""").getValue("alice")

        assertEquals(UserTagColor.BLUE, tag.color)
        assertNull(tag.unknownColorName)
    }
}
