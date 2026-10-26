package app.subshell.prototype.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.subshell.prototype.MainActivity
import android.webkit.CookieManager
import android.webkit.WebStorage
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN
import app.subshell.prototype.data.auth.reddit.RedditOAuth

class RedReaderCompatibilityLoginActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var callbackHandled = false
    private var authorizationResumes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // LOW9_LOGIN_WINDOW_KEEPS_ITS_ONCE_ONLY_STATE_V1 (OCB-438): the resume
        // budget and the handled flag survive rotation and process recreation.
        callbackHandled = savedInstanceState?.getBoolean(CALLBACK_HANDLED_STATE) ?: false
        authorizationResumes = savedInstanceState?.getInt(AUTHORIZATION_RESUMES_STATE) ?: 0
        val authorizationUri = intent
            .getStringExtra(AUTHORIZATION_URL_EXTRA)
            ?.toUri()
            ?.takeIf { uri -> uri.scheme == HTTPS_SCHEME }
            ?: run {
                returnToMain(MISSING_URI_RESULT_TOKEN)
                return
            }

        val freshAccountSession =
            intent.getBooleanExtra(
                FRESH_ACCOUNT_SESSION_EXTRA,
                false,
            )

        webView = createLoginWebView()
        setContentView(loginWindowContent(webView))
        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                // LOW9 (OCB-595): one result per login window; Back after the
                // callback was handled sends nothing more.
                cancelLogin()
            }
        }

        val restored =
            savedInstanceState != null && webView.restoreState(savedInstanceState) != null
        if (!restored) {
            loadAuthorization(
                authorizationUri = authorizationUri,
                freshAccountSession = freshAccountSession,
            )
        }
    }

    private fun loadAuthorization(
        authorizationUri: Uri,
        freshAccountSession: Boolean,
    ) {
        if (!freshAccountSession) {
            webView.loadUrl(authorizationUri.toString())
            return
        }

        webView.clearCache(true)
        webView.clearHistory()
        WebStorage.getInstance().deleteAllData()

        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            runOnUiThread {
                if (
                    !isFinishing &&
                    !isDestroyed &&
                    ::webView.isInitialized
                ) {
                    webView.loadUrl(
                        authorizationUri.toString(),
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(CALLBACK_HANDLED_STATE, callbackHandled)
        outState.putInt(AUTHORIZATION_RESUMES_STATE, authorizationResumes)
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    // ISSUE242_LOGIN_WINDOW_KEEPS_CLEAR_OF_THE_SYSTEM_BARS_V1 (#242): Android
    // 15+ draws every window edge to edge, so Reddit's page sat under the status
    // bar and its buttons under the gesture bar, and the keyboard covered the
    // sign-in fields (no window resize any more). The page is padded by the
    // system bars, the display cutout and the keyboard.
    private fun loginWindowContent(page: WebView): View = FrameLayout(this).apply {
        addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val clear = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(clear.left, clear.top, clear.right, clear.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun createLoginWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        allowDarkPage(settings)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                if (!request.isForMainFrame) return false
                return handleNavigation(request.url)
            }

            @Deprecated("Required for WebView callback compatibility below API 24")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                handleNavigation(url.toUri())

            // Reddit may leave the login page with a script navigation that
            // shouldOverrideUrlLoading never sees; the history update does.
            override fun doUpdateVisitedHistory(
                view: WebView,
                url: String?,
                isReload: Boolean,
            ) {
                if (url != null && isRedditFrontPage(url.toUri())) {
                    leaveFrontPage()
                }
            }
        }
    }

    private fun handleNavigation(uri: Uri): Boolean = when (
        loginNavigationFor(uri, callbackUri = loginCallbackUriOf(intent.getStringExtra(AUTHORIZATION_URL_EXTRA)))
    ) {
        LoginNavigation.CALLBACK -> {
            if (!callbackHandled) {
                callbackHandled = true
                lifecycleScope.launch {
                    returnToMain(processAuthCallbackOffMain(applicationContext, uri).toResultToken())
                }
            }
            true
        }
        LoginNavigation.LEAVE_FRONT_PAGE -> {
            leaveFrontPage()
            true
        }
        LoginNavigation.LOAD -> false
        LoginNavigation.BLOCK -> true
    }

    // SUBSHELL_AUTHCALLBACK07_LOGIN_FRONT_PAGE_CANCELS_V1
    // Reddit's own close (X) on its login page goes to the front page, which
    // left the user browsing reddit.com inside the login window. Reaching the
    // front page ends the login as cancelled, exactly like Back.
    // SUBSHELL_AUTHCALLBACK08_SIGNED_IN_FRONT_PAGE_RESUMES_V1
    // A Google/Apple/2FA sign-in can land on the front page after it
    // succeeded, when Reddit loses the way back to the authorization. Signed
    // in, the window reopens the authorization page once so the user can
    // allow it; signed out, the front page means Reddit's own close.
    private fun leaveFrontPage() {
        if (callbackHandled) return
        val signedIn = hasRedditSessionCookie(
            CookieManager.getInstance().getCookie(REDDIT_COOKIE_URL),
        )
        val authorizationUrl = intent.getStringExtra(AUTHORIZATION_URL_EXTRA)
        when (
            loginFrontPageAction(
                signedIn = signedIn,
                authorizationResumes = authorizationResumes,
                hasAuthorizationUrl = authorizationUrl != null,
            )
        ) {
            LoginFrontPageAction.RESUME_AUTHORIZATION -> {
                authorizationResumes += 1
                webView.loadUrl(requireNotNull(authorizationUrl))
            }
            LoginFrontPageAction.CANCEL -> cancelLogin()
        }
    }

    private fun cancelLogin() {
        if (callbackHandled) return
        callbackHandled = true
        returnToMain(AUTH_CALLBACK_CANCELLED_TOKEN)
    }

    private fun returnToMain(resultToken: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putAuthCallbackResult(resultToken),
        )
        finish()
    }

    companion object {
        private const val CALLBACK_HANDLED_STATE = "subshell.login.callback_handled"
        private const val AUTHORIZATION_RESUMES_STATE = "subshell.login.authorization_resumes"
        private const val AUTHORIZATION_URL_EXTRA =
            "app.subshell.prototype.auth.REDREADER_AUTHORIZATION_URL"
        private const val FRESH_ACCOUNT_SESSION_EXTRA =
            "app.subshell.prototype.auth.REDREADER_FRESH_ACCOUNT_SESSION"
        private const val HTTPS_SCHEME = "https"
        private const val REDDIT_COOKIE_URL = "https://www.reddit.com/"
        private const val MISSING_URI_RESULT_TOKEN = "missing_uri"

        fun createIntent(
            context: Context,
            authorizationUrl: String,
            freshAccountSession: Boolean = false,
        ): Intent =
            Intent(
                context,
                RedReaderCompatibilityLoginActivity::class.java,
            )
                .putExtra(
                    AUTHORIZATION_URL_EXTRA,
                    authorizationUrl,
                )
                .putExtra(
                    FRESH_ACCOUNT_SESSION_EXTRA,
                    freshAccountSession,
                )
    }
}

private val DEFAULT_LOGIN_CALLBACK_URI: Uri =
    RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI.toUri()

// ISSUE240_LOGIN_WINDOW_MATCHES_THE_CONFIGURED_REDIRECT_V1: the callback is the
// redirect URI the authorization asked for (the reader may have changed it);
// the shipped RedReader one when the URL names none.
internal fun loginCallbackUriOf(authorizationUrl: String?): Uri =
    authorizationUrl
        ?.toUri()
        ?.getQueryParameter("redirect_uri")
        ?.toUri()
        ?.takeIf { uri -> !uri.scheme.isNullOrEmpty() && !uri.host.isNullOrEmpty() }
        ?: DEFAULT_LOGIN_CALLBACK_URI

// STEP3_LOGIN_NAVIGATION_DECISION_IS_A_FUNCTION_V1: what the login window does
// with a main-frame navigation, decided without the window (behaviour as
// before; Step3RedReaderLoginNavigationTest).
internal enum class LoginNavigation {
    CALLBACK,
    LEAVE_FRONT_PAGE,
    LOAD,
    BLOCK,
}

/**
 * Only the callback (the authorization's redirect scheme and host, RedReader's
 * by default) is the sign-in's answer; Reddit's front page ends or resumes the
 * login; HTTPS pages of Reddit and its sign-in providers load; anything else
 * is blocked.
 */
internal fun loginNavigationFor(
    uri: Uri,
    callbackUri: Uri = DEFAULT_LOGIN_CALLBACK_URI,
): LoginNavigation = when {
    uri.scheme.equals(callbackUri.scheme, ignoreCase = true) &&
        uri.host.equals(callbackUri.host, ignoreCase = true) -> LoginNavigation.CALLBACK
    isRedditFrontPage(uri) -> LoginNavigation.LEAVE_FRONT_PAGE
    uri.scheme == "https" && isLoginWindowHost(uri.host) -> LoginNavigation.LOAD
    else -> LoginNavigation.BLOCK
}

/**
 * SECAUDIT11_LOGIN_WINDOW_LOADS_ONLY_REDDIT_AND_ITS_PROVIDERS_V1 (security
 * audit #11, owner): the login window has no address bar, so a page off Reddit
 * could not be told apart from Reddit's own while the reader types a password.
 * Its main frame loads only Reddit and the providers Reddit's login offers:
 * Google (google.com, its country domains, where its sign-in sets cookies, and
 * accounts.youtube.com) and Apple.
 */
internal fun isLoginWindowHost(host: String?): Boolean {
    val name = host?.trimEnd('.')?.lowercase() ?: return false
    fun within(domain: String) = name == domain || name.endsWith(".$domain")
    return within("reddit.com") ||
        within("apple.com") ||
        name == "accounts.youtube.com" ||
        GOOGLE_HOST.matches(name)
}

// google.com, google.no, google.co.uk, google.com.au and their subdomains.
private val GOOGLE_HOST = Regex("""^([a-z0-9-]+\.)*google\.(com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})$""")

private val REDDIT_FRONT_PAGE_HOSTS = setOf(
    "reddit.com",
    "www.reddit.com",
    "new.reddit.com",
    "old.reddit.com",
    "sh.reddit.com",
)

/**
 * Reddit's front page, the place its login page's close (X) leads to. A
 * login or authorization page never lives at the root, so reaching it means
 * the user left the login.
 */
internal fun isRedditFrontPage(uri: Uri): Boolean =
    uri.scheme == "https" &&
        uri.host?.lowercase() in REDDIT_FRONT_PAGE_HOSTS &&
        uri.path.let { path -> path.isNullOrEmpty() || path == "/" }

internal enum class LoginFrontPageAction {
    RESUME_AUTHORIZATION,
    CANCEL,
}

/**
 * Signed in (Reddit's `reddit_session` cookie, set only after a login; the
 * anonymous `token_v2` exists before it), the front page is a login that
 * lost its way back: reopen the authorization, once. Otherwise it is
 * Reddit's own close, and the login ends as cancelled.
 */
internal fun loginFrontPageAction(
    signedIn: Boolean,
    authorizationResumes: Int,
    hasAuthorizationUrl: Boolean,
): LoginFrontPageAction =
    if (signedIn && hasAuthorizationUrl && authorizationResumes < MAX_AUTHORIZATION_RESUMES) {
        LoginFrontPageAction.RESUME_AUTHORIZATION
    } else {
        LoginFrontPageAction.CANCEL
    }

private const val MAX_AUTHORIZATION_RESUMES = 1

/** Reads cookie names only; the values are session secrets. */
internal fun hasRedditSessionCookie(cookieHeader: String?): Boolean =
    cookieHeader.orEmpty()
        .split(';')
        .any { cookie -> cookie.substringBefore('=').trim() == "reddit_session" }

// ISSUE242_LOGIN_PAGE_FOLLOWS_THE_DARK_THEME_V1 (#242): on Android 13+ the page
// sees the dark theme (prefers-color-scheme) and is darkened if it has none of
// its own; Android 10-12 need force dark for the same.
private fun allowDarkPage(settings: WebSettings) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        settings.isAlgorithmicDarkeningAllowed = true
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        @Suppress("DEPRECATION")
        settings.forceDark = WebSettings.FORCE_DARK_AUTO
    }
}
