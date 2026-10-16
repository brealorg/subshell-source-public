package app.subshell.prototype.ui

// COMPOSER10_COMMENT_MEDIA_RULES_V1: which uploads a community allows in
// comments, from Reddit's `allowed_media_in_comments` (verified on r/pics
// 2026-09-29: ["giphy", "static", "expression", "video"]). "static" is an
// image upload, "animated" a GIF upload. Unknown (no field, not loaded) is
// null, and then Reddit decides, as before.
data class CommentMediaPolicy(
    val imagesAllowed: Boolean,
    val gifsAllowed: Boolean,
)

internal fun commentMediaPolicyFromReddit(allowedMediaInComments: List<String>?): CommentMediaPolicy? =
    allowedMediaInComments?.let { types ->
        val allowed = types.map { type -> type.trim().lowercase() }.toSet()
        CommentMediaPolicy(
            imagesAllowed = "static" in allowed,
            gifsAllowed = "animated" in allowed,
        )
    }

internal fun commentMediaPolicyKey(accountStableId: String, community: String): String =
    "$accountStableId|${community.trim().removePrefix("r/").removePrefix("/r/").lowercase()}"
