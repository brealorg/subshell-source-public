package app.subshell.prototype.data.network.reddit

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

// EDITREADBACK01_EDITED_STAMP_V1: Reddit writes a rich-text edit back as its
// own markdown, which need not equal the markdown that was sent. Its `edited`
// stamp (false, or epoch seconds, both from Reddit's clock) moving past the
// value read just before sending shows the edit landed without comparing
// text. Edits inside Reddit's grace window leave `edited` false, so they fall
// back to the text comparison.

/** false → 0.0 (never edited), a number → that stamp, anything else → null. */
internal fun redditEditedStamp(value: JsonElement?): Double? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    primitive.booleanOrNull?.let { edited -> return if (edited) null else 0.0 }
    return primitive.doubleOrNull
}

/** True only when both stamps are known and [after] is later than [before]. */
internal fun redditEditedSince(before: Double?, after: Double?): Boolean =
    before != null && after != null && after > before

/**
 * The `edited` stamp read before a rich-text edit, per account and item, for
 * the text that was sent, so a later Check again (a new data source) can use
 * it. Only the newest [capacity] items are kept.
 */
internal class RichTextEditStamps(private val capacity: Int = 16) {
    private data class Entry(val text: String, val editedBefore: Double)

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun remember(accountStableId: String, thingFullname: String, text: String, editedBefore: Double) {
        val key = key(accountStableId, thingFullname)
        entries.remove(key)
        entries[key] = Entry(text, editedBefore)
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun editedBefore(accountStableId: String, thingFullname: String, text: String): Double? =
        entries[key(accountStableId, thingFullname)]?.takeIf { entry -> entry.text == text }?.editedBefore

    private fun key(accountStableId: String, thingFullname: String) = "$accountStableId\n$thingFullname"
}
