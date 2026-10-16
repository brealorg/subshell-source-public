package app.subshell.prototype.data.auth

import app.subshell.prototype.data.account.PostManageOperation

internal enum class PostManageAuthorizationContext(
    val wireKey: String,
) {
    FLAIR("flair"),
    MODERATION("moderation"),
    EDIT("edit"),
    ;

    companion object {
        fun fromOperation(
            operation: PostManageOperation,
        ): PostManageAuthorizationContext = when (operation) {
            PostManageOperation.FLAIR_ACCESS,
            PostManageOperation.SET_FLAIR,
            -> FLAIR

            PostManageOperation.SET_NSFW,
            PostManageOperation.SET_SPOILER,
            -> MODERATION

            PostManageOperation.SET_SEND_REPLIES -> EDIT
        }

        fun fromWireKey(value: String?): PostManageAuthorizationContext? {
            val normalized =
                runCatching {
                    normalizeAuthCapabilityAuthorizationContextKey(value)
                }.getOrNull() ?: return null
            return entries.firstOrNull { it.wireKey == normalized }
        }
    }
}
