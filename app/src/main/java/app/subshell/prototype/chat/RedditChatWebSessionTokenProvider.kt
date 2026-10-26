package app.subshell.prototype.chat

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal sealed interface RedditChatWebTokenResult {
    data class Success(
        val token: String,
        val expiresAtEpochMs: Long?,
    ) : RedditChatWebTokenResult

    data object NoRedditWebSession : RedditChatWebTokenResult
    data object Failed : RedditChatWebTokenResult
}

internal data class RedditChatCachedWebToken(
    val token: String,
    val expiresAtEpochMs: Long,
    val redditSessionFingerprint: String,
)

internal fun redditChatWebTokenCacheUsable(
    cached: RedditChatCachedWebToken?,
    redditSessionFingerprint: String?,
    nowEpochMs: Long,
    expirySkewMs: Long = RedditChatWebSessionTokenProvider.TOKEN_EXPIRY_SKEW_MS,
): Boolean = cached != null &&
    redditSessionFingerprint != null &&
    cached.redditSessionFingerprint == redditSessionFingerprint &&
    nowEpochMs < cached.expiresAtEpochMs - expirySkewMs

internal fun redditChatSessionFingerprint(cookieHeader: String): String? {
    val session = cookieHeader
        .split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("reddit_session=") }
        ?.substringAfter('=', missingDelimiterValue = "")
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return MessageDigest.getInstance("SHA-256")
        .digest(session.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

// STEP3_MATRIX_WEB_TOKEN_SEAM_V1: the Matrix reader and writers take this
// interface, so a test can hand them a fixed token instead of minting one in a
// WebView. RedditChatWebSessionTokenProvider is the only production source.
internal interface RedditChatWebTokenSource {
    suspend fun token(): RedditChatWebTokenResult

    fun invalidate(expectedToken: String? = null)
}

/**
 * Mints Reddit's first-party web JWT from the existing reddit.com WebView session.
 *
 * The bridge is attached only to a one-shot WebView pinned to reddit.com. It never receives
 * arbitrary navigation, local files, content:// URLs, or a non-Reddit origin.
 */
internal class RedditChatWebSessionTokenProvider(
    context: Context,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : RedditChatWebTokenSource {
    private val appContext = context.applicationContext
    private val mintMutex = Mutex()
    @Volatile
    private var cachedToken: RedditChatCachedWebToken? = null

    override suspend fun token(): RedditChatWebTokenResult {
        mintMutex.lock()
        try {
            return withContext(Dispatchers.Main.immediate) {
                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie(REDDIT_ORIGIN).orEmpty()
                val sessionFingerprint = redditChatSessionFingerprint(cookies)
                if (sessionFingerprint == null) {
                    cachedToken = null
                    return@withContext RedditChatWebTokenResult.NoRedditWebSession
                }

                val cached = cachedToken
                if (
                    redditChatWebTokenCacheUsable(
                        cached = cached,
                        redditSessionFingerprint = sessionFingerprint,
                        nowEpochMs = nowEpochMs(),
                    )
                ) {
                    return@withContext RedditChatWebTokenResult.Success(
                        token = requireNotNull(cached).token,
                        expiresAtEpochMs = cached.expiresAtEpochMs,
                    )
                }
                cachedToken = null

                val bootstrapUrl = if (cookies.contains("csrf_token=")) {
                    REDDIT_BOOTSTRAP_URL
                } else {
                    REDDIT_ORIGIN + "/"
                }

                // SECAUDIT12_CHAT_BRIDGE_ANSWERS_ONLY_ITS_OWN_SCRIPT_V1 (security
                // audit #12): any reddit.com frame in this WebView can call the
                // bridge. Only the injected script knows this mint's nonce, so
                // a call without it is ignored (it can neither feed a token nor
                // end the mint early).
                val bridgeNonce = UUID.randomUUID().toString()
                suspendCancellableCoroutine<RedditChatWebTokenResult> { continuation ->
                    val handler = Handler(Looper.getMainLooper())
                    var finished = false
                    var fetchStarted = false
                    lateinit var webView: WebView

                    fun complete(result: RedditChatWebTokenResult) {
                        if (finished) return
                        finished = true
                        handler.removeCallbacksAndMessages(null)
                        runCatching {
                            webView.removeJavascriptInterface(BRIDGE_NAME)
                            webView.stopLoading()
                            webView.loadUrl("about:blank")
                            webView.destroy()
                        }
                        if (continuation.isActive) continuation.resume(result)
                    }

                    val bridge = object {
                        @JavascriptInterface
                        fun onToken(nonce: String?, responseBody: String) {
                            if (!redditChatBridgeCallIsOwn(bridgeNonce, nonce)) return
                            handler.post {
                                val parsed = runCatching { JSONObject(responseBody) }.getOrNull()
                                val token = parsed
                                    ?.optString("token")
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                                if (token == null) {
                                    complete(RedditChatWebTokenResult.Failed)
                                } else {
                                    val expires = redditChatTokenExpiryEpochMs(
                                        expires = parsed.opt("expires"),
                                        token = token,
                                    )
                                    if (
                                        expires != null &&
                                        nowEpochMs() < expires - TOKEN_EXPIRY_SKEW_MS
                                    ) {
                                        cachedToken = RedditChatCachedWebToken(
                                            token = token,
                                            expiresAtEpochMs = expires,
                                            redditSessionFingerprint = sessionFingerprint,
                                        )
                                    }
                                    complete(
                                        RedditChatWebTokenResult.Success(
                                            token = token,
                                            expiresAtEpochMs = expires,
                                        ),
                                    )
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onError(nonce: String?, reason: String) {
                            if (!redditChatBridgeCallIsOwn(bridgeNonce, nonce)) return
                            @Suppress("UNUSED_VARIABLE")
                            val ignored = reason
                            handler.post { complete(RedditChatWebTokenResult.Failed) }
                        }
                    }

                    webView = createPinnedWebView(
                        bridge = bridge,
                        onRedditPageReady = {
                            if (!fetchStarted) {
                                fetchStarted = true
                                webView.evaluateJavascript(redditChatTokenFetchScript(bridgeNonce), null)
                            }
                        },
                    )
                    continuation.invokeOnCancellation {
                        handler.post { complete(RedditChatWebTokenResult.Failed) }
                    }
                    handler.postDelayed(
                        { complete(RedditChatWebTokenResult.Failed) },
                        TOKEN_TIMEOUT_MS,
                    )
                    webView.loadUrl(bootstrapUrl)
                }
            }
        } finally {
            mintMutex.unlock()
        }
    }

    /**
     * Invalidates only the token that actually failed. A stale failure can never evict a newer JWT.
     */
    override fun invalidate(expectedToken: String?) {
        val cached = cachedToken ?: return
        if (expectedToken == null || cached.token == expectedToken) {
            cachedToken = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createPinnedWebView(
        bridge: Any,
        onRedditPageReady: () -> Unit,
    ): WebView = WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        addJavascriptInterface(bridge, BRIDGE_NAME)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = !isRedditHttps(request.url)

            @Deprecated("Required for WebView callback compatibility below API 24")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                !isRedditHttps(Uri.parse(url))

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = if (isRedditHttps(request.url)) {
                null
            } else {
                WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    ByteArrayInputStream(ByteArray(0)),
                )
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (isRedditHttps(Uri.parse(url))) {
                    onRedditPageReady()
                }
            }
        }
    }

    private fun isRedditHttps(uri: Uri?): Boolean {
        if (uri == null || !uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host == "reddit.com" || host.endsWith(".reddit.com")
    }

    companion object {
        private const val BRIDGE_NAME = "SubshellChatTokenBridge"
        private const val REDDIT_ORIGIN = "https://www.reddit.com"
        private const val REDDIT_BOOTSTRAP_URL = "https://www.reddit.com/robots.txt"
        private const val TOKEN_TIMEOUT_MS = 12_000L
        internal const val TOKEN_EXPIRY_SKEW_MS = 5 * 60 * 1_000L
    }
}

/**
 * LOW_CHAT_TOKEN_EXPIRY_IN_ANY_UNIT_V1 (OCB-600): the format of the token
 * response's "expires" is not documented or verified. A number below 10^12 is
 * epoch seconds, a larger one epoch milliseconds; anything else (missing, a
 * string) falls back to the JWT's own "exp" claim (seconds). Read as
 * milliseconds unchecked, a seconds value disabled token caching and expired
 * the cached chat rooms at once.
 */
internal fun redditChatTokenExpiryEpochMs(expires: Any?, token: String): Long? {
    val numeric = when (expires) {
        is Number -> expires.toLong()
        is String -> expires.trim().toLongOrNull()
        else -> null
    }?.takeIf { it > 0L }
    if (numeric != null) {
        return if (numeric < EPOCH_MS_THRESHOLD) numeric * 1_000L else numeric
    }
    val payload = token.split('.').getOrNull(1) ?: return null
    val exp = runCatching {
        val json = String(java.util.Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        JSONObject(json).optLong("exp", 0L)
    }.getOrNull()?.takeIf { it > 0L } ?: return null
    return if (exp < EPOCH_MS_THRESHOLD) exp * 1_000L else exp
}

private const val EPOCH_MS_THRESHOLD = 1_000_000_000_000L

/** SECAUDIT12_CHAT_BRIDGE_ANSWERS_ONLY_ITS_OWN_SCRIPT_V1 */
internal fun redditChatBridgeCallIsOwn(expectedNonce: String, receivedNonce: String?): Boolean =
    receivedNonce != null &&
        MessageDigest.isEqual(
            expectedNonce.toByteArray(Charsets.UTF_8),
            receivedNonce.toByteArray(Charsets.UTF_8),
        )

/** The token fetch, run in the main frame only; [nonce] is a UUID (no quote can break out). */
internal fun redditChatTokenFetchScript(nonce: String): String {
    require(nonce.all { it.isLetterOrDigit() || it == '-' }) { "Bridge nonce must be a UUID" }
    return """
            (function(){
              var nonce = '$nonce';
              try {
                var csrfCookie = document.cookie.split(';')
                  .map(function(v){ return v.trim(); })
                  .find(function(v){ return v.indexOf('csrf_token=') === 0; });
                var csrf = csrfCookie ? decodeURIComponent(csrfCookie.substring('csrf_token='.length)) : '';
                fetch('/svc/shreddit/token', {
                  method: 'POST',
                  credentials: 'include',
                  headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                  },
                  body: JSON.stringify({csrf_token: csrf})
                })
                .then(function(response){ return response.text(); })
                .then(function(body){ SubshellChatTokenBridge.onToken(nonce, body); })
                .catch(function(error){ SubshellChatTokenBridge.onError(nonce, String(error)); });
              } catch (error) {
                SubshellChatTokenBridge.onError(nonce, String(error));
              }
            })();
    """.trimIndent()
}
