package app.subshell.prototype.data.account

internal enum class OwnContentType {
    COMMENT,
    SELF_POST,
    OTHER_POST,
}

internal data class OwnContentActions(
    val canEdit: Boolean,
    val canDelete: Boolean,
) {
    companion object {
        val None = OwnContentActions(
            canEdit = false,
            canDelete = false,
        )
    }
}

internal fun ownContentActions(
    activeUsername: String?,
    author: String?,
    contentType: OwnContentType,
): OwnContentActions {
    val active = normalizeOwnContentUsername(activeUsername)
        ?: return OwnContentActions.None
    val contentAuthor = normalizeOwnContentUsername(author)
        ?: return OwnContentActions.None
    if (!active.equals(contentAuthor, ignoreCase = true)) {
        return OwnContentActions.None
    }
    return when (contentType) {
        OwnContentType.COMMENT,
        OwnContentType.SELF_POST,
        -> OwnContentActions(
            canEdit = true,
            canDelete = true,
        )
        OwnContentType.OTHER_POST -> OwnContentActions(
            canEdit = false,
            canDelete = true,
        )
    }
}

internal fun normalizeOwnContentUsername(value: String?): String? {
    val trimmed = value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    if (trimmed.equals("[deleted]", ignoreCase = true)) {
        return null
    }
    val candidate = when {
        trimmed.startsWith("/u/", ignoreCase = true) -> trimmed.drop(3)
        trimmed.startsWith("u/", ignoreCase = true) -> trimmed.drop(2)
        else -> trimmed
    }
    return candidate.takeIf { username ->
        username.length in 1..100 &&
            username.all { character ->
                character.isLetterOrDigit() ||
                    character == '_' ||
                    character == '-'
            }
    }
}
