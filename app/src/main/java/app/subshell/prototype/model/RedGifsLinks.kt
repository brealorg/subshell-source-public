package app.subshell.prototype.model

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/*
 * ISSUE207_REDGIFS_PLAYS_IN_THE_APP_V1: a RedGIFs post Reddit sends without
 * its own video copy keeps `subshell-redgifs://<id>` as its video source; the
 * player turns it into the clip's MP4 when it plays (media/RedGifs.kt).
 */

internal const val REDGIFS_VIDEO_SCHEME = "subshell-redgifs"

internal fun redGifsVideoUri(clipId: String): String = "$REDGIFS_VIDEO_SCHEME://$clipId"

/**
 * The clip id of a RedGIFs post URL (`redgifs.com/watch/<id>`, `/ifr/<id>`,
 * `thumbs2.redgifs.com/<Id>-mobile.mp4`, `media.redgifs.com/<Id>.mp4`), in
 * lower case, or null. `i.redgifs.com` carries image posts and stays as it is.
 */
internal fun redGifsClipId(url: String?): String? {
    val parsed = url?.trim()?.toHttpUrlOrNull() ?: return null
    val host = parsed.host.lowercase()
    if (host != "redgifs.com" && !host.endsWith(".redgifs.com")) return null
    if (host == "i.redgifs.com") return null
    val segments = parsed.pathSegments.filter(String::isNotEmpty)
    val raw = when {
        segments.size >= 2 && segments[0].lowercase() in setOf("watch", "ifr") -> segments[1]
        segments.size == 1 && (host.startsWith("thumbs") || host.startsWith("media.")) -> segments[0]
        else -> null
    } ?: return null
    return raw.substringBefore('-').substringBefore('.').lowercase().takeIf(::isRedGifsClipId)
}

internal fun isRedGifsClipId(id: String): Boolean =
    id.isNotEmpty() && id.length <= 128 && id.all { it in 'a'..'z' || it in '0'..'9' }
