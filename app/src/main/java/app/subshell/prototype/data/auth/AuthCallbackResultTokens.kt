package app.subshell.prototype.data.auth

/**
 * SUBSHELL_AUTHCALLBACK10_ONE_CANCELLED_TOKEN_V1
 * The result token for "the user closed the browser without finishing the
 * authorization". The auth tab dispatch and the compatibility login window
 * produce it; the connect flow and every callback owner read it.
 */
internal const val AUTH_CALLBACK_CANCELLED_TOKEN = "cancelled"

/**
 * SUBSHELL_AUTHCALLBACK11_AUTH_TAB_FAILURE_TOKENS_V1
 * Prefix of an auth tab result that ended without a callback for another
 * reason than a close: verification of the redirect failed or timed out, or
 * an unknown result. It starts with the connect flow's `rejected:` prefix,
 * so every owner reads it as a failed authorization the user can retry.
 */
internal const val AUTH_TAB_REJECTED_TOKEN_PREFIX = "rejected:AUTH_TAB_"

/** A browser that ended without a callback: closed, or failed before one arrived. */
internal fun isBrowserEndedWithoutCallbackToken(resultToken: String): Boolean =
    resultToken == AUTH_CALLBACK_CANCELLED_TOKEN ||
        resultToken.startsWith(AUTH_TAB_REJECTED_TOKEN_PREFIX)
