package app.subshell.prototype.data

import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditListingDto
import app.subshell.prototype.data.network.reddit.toNetworkPage
import app.subshell.prototype.model.MediaAsset
import app.subshell.prototype.model.Post
import kotlinx.serialization.decodeFromString

internal fun redditSensitiveMediaLabMappedPosts(rawJson: String): List<Post> =
    SubshellNetworkJson.providerJson
        .decodeFromString<RedditListingDto>(rawJson)
        .toNetworkPage(nowEpochSeconds = REDDIT_SENSITIVE_MEDIA_LAB_NOW)
        .items

internal fun redditSensitiveMediaLabPosts(): List<Post> {
    if (!BuildConfig.DEBUG || BuildConfig.DEBUG_SLOT != "dev2") return emptyList()
    val rawJson = runCatching {
        val provider = Class.forName(
            "app.subshell.prototype.data.SensitiveMediaLabDebugFixtureProvider",
        )
        provider.getMethod("rawJson").invoke(null) as String
    }.getOrNull() ?: return emptyList()

    return localizeSensitiveMediaLabPosts(redditSensitiveMediaLabMappedPosts(rawJson))
}

internal fun localizeSensitiveMediaLabPosts(posts: List<Post>): List<Post> =
    posts.map(::localizeSensitiveMediaLabPost)

private fun localizeSensitiveMediaLabPost(post: Post): Post = when (post.id) {
    "t3_lab_ordinary_image" -> post.copy(
        mediaAsset = MediaAsset.ANDROID_DESK,
        remoteImageUri = null,
    )
    "t3_lab_spoiler_image" -> post.copy(
        mediaAsset = MediaAsset.LINUX_DESK,
        remoteImageUri = null,
    )
    "t3_lab_nsfw_image" -> post.copy(
        mediaAsset = MediaAsset.HOMELAB,
        remoteImageUri = null,
    )
    "t3_lab_nsfw_video" -> post.copy(
        mediaAsset = MediaAsset.VIDEO_LAB_A,
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
    )
    "t3_lab_nsfw_gallery" -> post.copy(
        gallery = post.gallery.mapIndexed { index, item ->
            item.copy(
                asset = when (index) {
                    0 -> MediaAsset.ANDROID_DESK
                    1 -> MediaAsset.LINUX_DESK
                    else -> MediaAsset.HOMELAB
                },
                remoteImageUri = null,
            )
        },
    )
    else -> post
}

private const val REDDIT_SENSITIVE_MEDIA_LAB_NOW = 2_000_000_000L
