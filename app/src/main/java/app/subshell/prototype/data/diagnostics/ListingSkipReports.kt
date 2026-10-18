package app.subshell.prototype.data.diagnostics

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * DIAG04_LISTING_SKIP_EVENT_V1: a read listing that leaves out a post it
 * cannot decode (LISTINGSKIP01) says so in the diagnostics log, with the
 * field it failed on. The listing code has no context, so the activity
 * installs the recorder once per process; each field is recorded at most
 * once per process (indexes do not count), so a feed that keeps returning
 * the same odd post does not fill the log.
 */
internal object ListingSkipReports {
    @Volatile
    private var recorder: ((String?) -> Unit)? = null
    private val reported = ConcurrentHashMap.newKeySet<String>()

    fun install(context: Context) {
        if (recorder != null) return
        val store = DiagnosticEventStore(context.applicationContext)
        recorder = { path -> store.recordListingPostSkipped(path) }
    }

    fun report(jsonPath: String?) {
        val record = recorder ?: return
        val key = jsonPath?.replace(LIST_INDEX, "[*]") ?: UNKNOWN_PATH_KEY
        if (reported.size >= MAX_REPORTED_PATHS || !reported.add(key)) return
        runCatching { record(jsonPath) }
    }

    internal fun installForTest(record: ((String?) -> Unit)?) {
        recorder = record
        reported.clear()
    }

    private val LIST_INDEX = Regex("""\[\d+\]""")
    private const val UNKNOWN_PATH_KEY = "-"
    private const val MAX_REPORTED_PATHS = 32
}

/**
 * DIAG05_TARGET_LOOKUP_FAILURE_IS_RECORDED_V1 / DIAG05_SIGN_IN_FAILURES_ARE_RECORDED_V1:
 * a failure that reaches no screen with its details (a post looked up behind an
 * inbox item or a link comes back to the screen as "unavailable"; the sign-in's
 * identity read and first feed page have no screen) is recorded here with what
 * Reddit answered.
 */
internal fun recordFailureWithoutAScreen(
    context: Context,
    scopeKey: String,
    details: app.subshell.prototype.data.error.FailureDetails?,
) {
    runCatching {
        DiagnosticEventStore(context.applicationContext).recordPresentedFailure(
            scopeKey = scopeKey,
            presentationKind = DiagnosticPresentationKind.INLINE,
            details = details,
        )
    }
}
