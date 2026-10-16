package app.subshell.prototype.ui

// ISSUE34_USER_TAGS_V1: private notes and coloured tags on Reddit usernames.
// Stored per signed-in account (keyed on the account's stable id), never
// sent to Reddit.

const val USER_TAG_LABEL_MAX_LENGTH = 24
const val USER_TAG_NOTE_MAX_LENGTH = 500

private const val USER_TAGS_KEY_PREFIX = "user_tags:"
private val USER_TAG_USERNAME_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")

internal fun userTagsPreferenceKey(accountStableId: String): String =
    USER_TAGS_KEY_PREFIX + accountStableId

/** Fixed palette; the stored value is the enum name, the colours live in the UI. */
enum class UserTagColor {
    GREY,
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    TEAL,
    BLUE,
    PURPLE,
}

data class UserTag(
    val label: String = "",
    val color: UserTagColor = UserTagColor.GREY,
    val note: String = "",
    // TAGCOLOR01_UNKNOWN_COLOUR_KEPT_V1 (OCB-644): a colour name this build
    // does not know (a newer build wrote it) shows as grey but is written back
    // as it was, until the user picks another colour for this tag.
    val unknownColorName: String? = null,
)

/** The colour name to store: an unknown one kept verbatim, else this build's. */
internal val UserTag.storedColorName: String
    get() = unknownColorName ?: color.name

/** This build's colour for a stored name, or grey with the name kept. */
internal fun userTagColorFromStoredName(name: String?): Pair<UserTagColor, String?> {
    val known = name?.let { value -> UserTagColor.entries.firstOrNull { it.name == value } }
    return when {
        known != null -> known to null
        name.isNullOrBlank() -> UserTagColor.GREY to null
        else -> UserTagColor.GREY to name
    }
}

/** Lowercased username without a `u/` or `/u/` prefix, or null if it is not a Reddit username. */
internal fun normalizeUserTagUsername(username: String): String? {
    val trimmed = username.trim()
        .removePrefix("/")
        .let { name ->
            when {
                name.startsWith("u/", ignoreCase = true) -> name.drop(2)
                name.startsWith("user/", ignoreCase = true) -> name.drop(5)
                else -> name
            }
        }
    return trimmed.takeIf { name -> USER_TAG_USERNAME_PATTERN.matches(name) }?.lowercase()
}

/** Label on one line, both fields trimmed and capped; null when nothing is left to keep. */
internal fun normalizedUserTagOrNull(tag: UserTag): UserTag? {
    val label = tag.label
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeWholeCharacters(USER_TAG_LABEL_MAX_LENGTH)
        .trim()
    val note = tag.note
        .trim()
        .takeWholeCharacters(USER_TAG_NOTE_MAX_LENGTH)
        .trim()
    if (label.isEmpty() && note.isEmpty()) return null
    return UserTag(label = label, color = tag.color, note = note, unknownColorName = tag.unknownColorName)
}

// LOWUI_TAGS_CUT_AT_WHOLE_GRAPHEMES_V1 (OCB-105): flags, ZWJ sequences, skin
// tones and keycaps are several code points; the cut is at the last
// character boundary that fits.
private fun String.takeWholeCharacters(maxLength: Int): String {
    if (length <= maxLength) return this
    val boundaries = java.text.BreakIterator.getCharacterInstance()
    boundaries.setText(this)
    val end = boundaries.preceding(maxLength + 1).takeIf { it != java.text.BreakIterator.DONE } ?: 0
    return substring(0, end.coerceAtMost(maxLength))
}

internal fun normalizeUserTags(tags: Map<String, UserTag>): Map<String, UserTag> =
    buildMap {
        tags.forEach { (username, tag) ->
            val normalizedUsername = normalizeUserTagUsername(username) ?: return@forEach
            val normalizedTag = normalizedUserTagOrNull(tag) ?: return@forEach
            put(normalizedUsername, normalizedTag)
        }
    }
