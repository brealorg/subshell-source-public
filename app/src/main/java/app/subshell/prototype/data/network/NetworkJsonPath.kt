package app.subshell.prototype.data.network

// DIAG03_JSON_FIELD_PATH_V1 (#202): a response kotlinx cannot decode was only
// "Malformed response". kotlinx names the field it failed on ("... at path:
// $.json.data.things[0].data"); that path is kept so a changed Reddit answer
// can be pinned to its field. Only the path's own characters are kept, and
// map keys or other non-index brackets become [*]: no values, no exception text.
internal const val JSON_FIELD_PATH_MAX_CHARS = 120

private val jsonPathInMessage = Regex("""at path:? (\$\S*)""")
private val nonIndexBracket = Regex("""\[(?!\d+\])[^\]]*\]""")

internal fun Throwable.safeJsonFieldPath(): String? =
    message?.let { jsonPathInMessage.find(it)?.groupValues?.get(1) }?.let(::sanitizeJsonFieldPath)

internal fun sanitizeJsonFieldPath(raw: String): String? {
    val cleaned = nonIndexBracket.replace(raw.trim(), "[*]")
        .filter { char -> (char.code < 128 && char.isLetterOrDigit()) || char in "_\$.[]*-" }
        .take(JSON_FIELD_PATH_MAX_CHARS)
    return cleaned.takeIf { it.startsWith("$") && it.length > 1 }
}
