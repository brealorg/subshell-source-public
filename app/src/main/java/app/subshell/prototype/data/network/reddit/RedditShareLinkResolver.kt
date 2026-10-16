package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.SubshellNetworkTransport
import app.subshell.prototype.data.network.awaitResponse
import java.io.IOException
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

// REDDITURL03_SHARE_LINK_REDIRECT_V1
// A share link (`/r/<sub>/s/<id>`, `/u/<user>/s/<id>`) is opaque: only Reddit
// knows what it points at, and it answers with an HTTP redirect. Resolving
// one is a read without credentials: HEAD requests on a client that never
// follows redirects by itself, reading `Location` until the URL is no
// longer a share link. Verified 2026-09-25: an unknown share id answers 307
// to its community.
internal class RedditShareLinkResolver(
    private val callFactory: Call.Factory = SubshellNetworkTransport.sharedCallFactory,
    private val userAgent: String = REDDIT_SHARE_LINK_USER_AGENT,
    private val maxRedirects: Int = REDDIT_SHARE_LINK_MAX_REDIRECTS,
) {
    /** The URL the share link leads to, or null when Reddit does not say. */
    suspend fun resolve(shareUrl: String): String? {
        var current = shareUrl.trim().toHttpUrlOrNull() ?: return null
        repeat(maxRedirects) {
            val location = try {
                callFactory.newCall(
                    Request.Builder()
                        .url(current)
                        .head()
                        .header("User-Agent", userAgent)
                        .build(),
                ).awaitResponse().use { response ->
                    if (response.code !in REDIRECT_CODES) return null
                    response.header("Location")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                return null
            }
            val next = location?.let(current::resolve) ?: return null
            if (!next.isRedditShareLink()) return next.toString()
            current = next
        }
        return null
    }
}

/** `/r|u|user/<name>/s/<id>` on a reddit.com host. */
internal fun HttpUrl.isRedditShareLink(): Boolean {
    val segments = pathSegments.filter(String::isNotEmpty)
    return (host == "reddit.com" || host.endsWith(".reddit.com")) &&
        segments.size == 4 &&
        segments[0].lowercase() in REDDIT_SHARE_LINK_OWNER_SEGMENTS &&
        segments[2] == "s"
}

private val REDIRECT_CODES = 300..399
private val REDDIT_SHARE_LINK_OWNER_SEGMENTS = setOf("r", "u", "user")
private const val REDDIT_SHARE_LINK_USER_AGENT = "subshell-links/1.0"
private const val REDDIT_SHARE_LINK_MAX_REDIRECTS = 5
