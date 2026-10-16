package app.subshell.prototype.data.diagnostics

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.network.sanitizeJsonFieldPath
import java.util.Locale
import java.util.UUID

internal enum class DiagnosticLevel {
    INFO,
    WARNING,
    ERROR,
}

internal enum class DiagnosticSubsystem {
    APP,
    SETTINGS,
    FEED,
    SEARCH,
    POSTS,
    COMMENTS,
    INBOX,
    MODMAIL,
    MESSAGES,
    COMMUNITY,
    PROFILE,
    MEDIA,
    AUTH,
    BACKUP,
    NETWORK,
    UI,
}

internal enum class DiagnosticOperation {
    APP_SESSION_STARTED,
    VIEWPORT_ERROR_PRESENTED,
    BLOCKING_ERROR_PRESENTED,
    DEGRADED_ERROR_PRESENTED,
    INLINE_ERROR_PRESENTED,
    DIALOG_ERROR_PRESENTED,
    BACKGROUND_SYNC_FAILED,
    BACKGROUND_AUTHORIZATION_REQUIRED,
    NOTIFICATION_POST_FAILED,
    USER_INCIDENT_MARKED,
    DIAGNOSTICS_OPENED,
    REPORT_CREATED,
    // CRASH01_CRASHES_ARE_RECORDED_LOCALLY_V1: added when the next start finds
    // a recorded crash, at the crash's own time.
    APP_CRASHED,
    APP_NATIVE_CRASHED,
    APP_NOT_RESPONDING,
}

internal enum class DiagnosticPresentationKind {
    BLOCKING,
    DEGRADED,
    INLINE,
    DIALOG,
}

internal enum class DiagnosticOutcome {
    STARTED,
    OBSERVED,
    SUCCEEDED,
    FAILED,
}

internal data class DiagnosticFailureSnapshot(
    val httpStatusCode: Int? = null,
    val retryAfterMs: Long? = null,
    val retryable: Boolean? = null,
    val transportKind: String? = null,
    val protocolKind: String? = null,
    val providerCode: String? = null,
    val mediaCode: String? = null,
    val localKind: String? = null,
    /** DIAG03_JSON_FIELD_PATH_V1: the sanitized response field a decode failed on. */
    val jsonPath: String? = null,
) {
    init {
        require(httpStatusCode == null || httpStatusCode in 100..599)
        require(retryAfterMs == null || retryAfterMs >= 0L)
        listOf(transportKind, protocolKind, localKind).filterNotNull().forEach { token ->
            require(token.matches(SAFE_ENUM_TOKEN_PATTERN))
        }
        require(providerCode == null || providerCode.length <= SAFE_DIAGNOSTIC_CODE_LIMIT)
        require(mediaCode == null || mediaCode.length <= SAFE_DIAGNOSTIC_CODE_LIMIT)
        require(jsonPath == null || sanitizeJsonFieldPath(jsonPath) == jsonPath)
    }

    companion object {
        fun from(details: FailureDetails?): DiagnosticFailureSnapshot? {
            details ?: return null
            return DiagnosticFailureSnapshot(
                httpStatusCode = details.httpStatusCode,
                retryAfterMs = details.retryAfterMs,
                retryable = details.retryable,
                transportKind = details.transportKind?.name?.lowercase(Locale.ROOT),
                protocolKind = details.protocolKind?.name?.lowercase(Locale.ROOT),
                providerCode = details.providerCode?.value,
                mediaCode = details.mediaCode?.value,
                localKind = details.localKind?.name?.lowercase(Locale.ROOT),
                jsonPath = details.jsonPath?.value?.let(::sanitizeJsonFieldPath),
            // LOW_DIAG_EMPTY_SNAPSHOT_IS_NONE_V1 (OCB-529): a snapshot with
            // nothing stored is no snapshot, as it reads back from the file,
            // so a repeat of the same event is deduplicated.
            ).takeUnless { snapshot -> snapshot == DiagnosticFailureSnapshot() }
        }
    }
}

internal data class DiagnosticEvent(
    val id: String,
    val timestampEpochMs: Long,
    val level: DiagnosticLevel,
    val subsystem: DiagnosticSubsystem,
    val operation: DiagnosticOperation,
    val outcome: DiagnosticOutcome,
    val failure: DiagnosticFailureSnapshot? = null,
    val scopeFingerprint: String? = null,
) {
    init {
        require(id.matches(DIAGNOSTIC_EVENT_ID_PATTERN))
        require(timestampEpochMs >= 0L)
        require(scopeFingerprint == null || scopeFingerprint.matches(SCOPE_FINGERPRINT_PATTERN))
    }

    internal fun dedupeKey(): String = buildString {
        append(level.name)
        append('|')
        append(subsystem.name)
        append('|')
        append(operation.name)
        append('|')
        append(outcome.name)
        append('|')
        append(failure)
        append('|')
        append(scopeFingerprint.orEmpty())
    }
}

internal object DiagnosticEvents {
    fun appSessionStarted(nowEpochMs: Long = System.currentTimeMillis()): DiagnosticEvent =
        DiagnosticEvent(
            id = newDiagnosticEventId(),
            timestampEpochMs = nowEpochMs,
            level = DiagnosticLevel.INFO,
            subsystem = DiagnosticSubsystem.APP,
            operation = DiagnosticOperation.APP_SESSION_STARTED,
            outcome = DiagnosticOutcome.STARTED,
        )

    fun diagnosticsOpened(nowEpochMs: Long = System.currentTimeMillis()): DiagnosticEvent =
        DiagnosticEvent(
            id = newDiagnosticEventId(),
            timestampEpochMs = nowEpochMs,
            level = DiagnosticLevel.INFO,
            subsystem = DiagnosticSubsystem.SETTINGS,
            operation = DiagnosticOperation.DIAGNOSTICS_OPENED,
            outcome = DiagnosticOutcome.OBSERVED,
        )

    fun appStoppedUnexpectedly(record: CrashRecord): DiagnosticEvent =
        DiagnosticEvent(
            id = newDiagnosticEventId(),
            timestampEpochMs = record.timestampEpochMs,
            level = DiagnosticLevel.ERROR,
            subsystem = DiagnosticSubsystem.APP,
            operation = when (record.kind) {
                CrashKind.CRASH -> DiagnosticOperation.APP_CRASHED
                CrashKind.NATIVE_CRASH -> DiagnosticOperation.APP_NATIVE_CRASHED
                CrashKind.NOT_RESPONDING -> DiagnosticOperation.APP_NOT_RESPONDING
            },
            outcome = DiagnosticOutcome.FAILED,
        )

    fun reportCreated(nowEpochMs: Long = System.currentTimeMillis()): DiagnosticEvent =
        DiagnosticEvent(
            id = newDiagnosticEventId(),
            timestampEpochMs = nowEpochMs,
            level = DiagnosticLevel.INFO,
            subsystem = DiagnosticSubsystem.SETTINGS,
            operation = DiagnosticOperation.REPORT_CREATED,
            outcome = DiagnosticOutcome.SUCCEEDED,
        )

    fun userIncidentMarked(nowEpochMs: Long = System.currentTimeMillis()): DiagnosticEvent =
        DiagnosticEvent(
            id = newDiagnosticEventId(),
            timestampEpochMs = nowEpochMs,
            level = DiagnosticLevel.WARNING,
            subsystem = DiagnosticSubsystem.UI,
            operation = DiagnosticOperation.USER_INCIDENT_MARKED,
            outcome = DiagnosticOutcome.OBSERVED,
        )

    internal fun viewportFailure(
        subsystem: DiagnosticSubsystem,
        scopeFingerprint: String,
        details: FailureDetails?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiagnosticEvent = DiagnosticEvent(
        id = newDiagnosticEventId(),
        timestampEpochMs = nowEpochMs,
        level = diagnosticLevelForViewportFailure(details),
        subsystem = subsystem,
        operation = DiagnosticOperation.VIEWPORT_ERROR_PRESENTED,
        outcome = DiagnosticOutcome.FAILED,
        failure = DiagnosticFailureSnapshot.from(details),
        scopeFingerprint = scopeFingerprint,
    )

    internal fun presentedFailure(
        subsystem: DiagnosticSubsystem,
        scopeFingerprint: String,
        presentationKind: DiagnosticPresentationKind,
        details: FailureDetails?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiagnosticEvent = DiagnosticEvent(
        id = newDiagnosticEventId(),
        timestampEpochMs = nowEpochMs,
        level = diagnosticLevelForPresentedFailure(
            presentationKind = presentationKind,
            details = details,
        ),
        subsystem = subsystem,
        operation = presentationKind.toDiagnosticOperation(),
        outcome = DiagnosticOutcome.FAILED,
        failure = DiagnosticFailureSnapshot.from(details),
        scopeFingerprint = scopeFingerprint,
    )

    internal fun backgroundFailure(
        subsystem: DiagnosticSubsystem,
        scopeFingerprint: String,
        details: FailureDetails?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiagnosticEvent = DiagnosticEvent(
        id = newDiagnosticEventId(),
        timestampEpochMs = nowEpochMs,
        level = diagnosticLevelForOperationalFailure(details),
        subsystem = subsystem,
        operation = DiagnosticOperation.BACKGROUND_SYNC_FAILED,
        outcome = DiagnosticOutcome.FAILED,
        failure = DiagnosticFailureSnapshot.from(details),
        scopeFingerprint = scopeFingerprint,
    )

    internal fun backgroundAuthorizationRequired(
        subsystem: DiagnosticSubsystem,
        scopeFingerprint: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiagnosticEvent = DiagnosticEvent(
        id = newDiagnosticEventId(),
        timestampEpochMs = nowEpochMs,
        level = DiagnosticLevel.WARNING,
        subsystem = subsystem,
        operation = DiagnosticOperation.BACKGROUND_AUTHORIZATION_REQUIRED,
        outcome = DiagnosticOutcome.FAILED,
        failure = DiagnosticFailureSnapshot(retryable = true),
        scopeFingerprint = scopeFingerprint,
    )

    internal fun notificationPostFailure(
        subsystem: DiagnosticSubsystem,
        scopeFingerprint: String,
        details: FailureDetails?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiagnosticEvent = DiagnosticEvent(
        id = newDiagnosticEventId(),
        timestampEpochMs = nowEpochMs,
        level = diagnosticLevelForOperationalFailure(details),
        subsystem = subsystem,
        operation = DiagnosticOperation.NOTIFICATION_POST_FAILED,
        outcome = DiagnosticOutcome.FAILED,
        failure = DiagnosticFailureSnapshot.from(details),
        scopeFingerprint = scopeFingerprint,
    )
}

internal fun diagnosticLevelForViewportFailure(details: FailureDetails?): DiagnosticLevel =
    if (details?.retryable == false) DiagnosticLevel.ERROR else DiagnosticLevel.WARNING

internal fun diagnosticLevelForPresentedFailure(
    presentationKind: DiagnosticPresentationKind,
    details: FailureDetails?,
): DiagnosticLevel = when {
    presentationKind == DiagnosticPresentationKind.DEGRADED -> DiagnosticLevel.WARNING
    presentationKind == DiagnosticPresentationKind.BLOCKING -> DiagnosticLevel.ERROR
    presentationKind == DiagnosticPresentationKind.DIALOG -> DiagnosticLevel.ERROR
    details?.retryable == false -> DiagnosticLevel.ERROR
    else -> DiagnosticLevel.WARNING
}

internal fun diagnosticLevelForOperationalFailure(details: FailureDetails?): DiagnosticLevel =
    if (details?.retryable == false) DiagnosticLevel.ERROR else DiagnosticLevel.WARNING

private fun DiagnosticPresentationKind.toDiagnosticOperation(): DiagnosticOperation = when (this) {
    DiagnosticPresentationKind.BLOCKING -> DiagnosticOperation.BLOCKING_ERROR_PRESENTED
    DiagnosticPresentationKind.DEGRADED -> DiagnosticOperation.DEGRADED_ERROR_PRESENTED
    DiagnosticPresentationKind.INLINE -> DiagnosticOperation.INLINE_ERROR_PRESENTED
    DiagnosticPresentationKind.DIALOG -> DiagnosticOperation.DIALOG_ERROR_PRESENTED
}

internal fun diagnosticSubsystemForScope(scopeKey: String): DiagnosticSubsystem {
    val normalized = scopeKey.lowercase(Locale.ROOT)
    val surfaceSubsystem = DIAGNOSTIC_SURFACE_MARKERS
        .mapNotNull { marker ->
            normalized.indexOf(marker.token)
                .takeIf { it >= 0 }
                ?.let { index -> DiagnosticSurfaceMatch(index = index, marker = marker) }
        }
        .minWithOrNull(
            compareBy<DiagnosticSurfaceMatch> { it.index }
                .thenByDescending { it.marker.token.length },
        )
        ?.marker
        ?.subsystem

    if (surfaceSubsystem != null) return surfaceSubsystem

    return when {
        "auth" in normalized || "oauth" in normalized || "account" in normalized ->
            DiagnosticSubsystem.AUTH
        "network" in normalized || "http" in normalized -> DiagnosticSubsystem.NETWORK
        else -> DiagnosticSubsystem.UI
    }
}

private data class DiagnosticSurfaceMarker(
    val token: String,
    val subsystem: DiagnosticSubsystem,
)

private data class DiagnosticSurfaceMatch(
    val index: Int,
    val marker: DiagnosticSurfaceMarker,
)

private val DIAGNOSTIC_SURFACE_MARKERS = listOf(
    // Compound user-visible surfaces beat their generic container marker
    // at the same scope position.
    DiagnosticSurfaceMarker("post-comments", DiagnosticSubsystem.COMMENTS),
    DiagnosticSurfaceMarker("modmail", DiagnosticSubsystem.MODMAIL),
    DiagnosticSurfaceMarker("setting", DiagnosticSubsystem.SETTINGS),
    DiagnosticSurfaceMarker("inbox", DiagnosticSubsystem.INBOX),
    DiagnosticSurfaceMarker("notification", DiagnosticSubsystem.INBOX),
    DiagnosticSurfaceMarker("comment", DiagnosticSubsystem.COMMENTS),
    DiagnosticSurfaceMarker("message", DiagnosticSubsystem.MESSAGES),
    DiagnosticSurfaceMarker("chat", DiagnosticSubsystem.MESSAGES),
    DiagnosticSurfaceMarker("conversation", DiagnosticSubsystem.MESSAGES),
    DiagnosticSurfaceMarker("community", DiagnosticSubsystem.COMMUNITY),
    DiagnosticSurfaceMarker("subreddit", DiagnosticSubsystem.COMMUNITY),
    DiagnosticSurfaceMarker("profile", DiagnosticSubsystem.PROFILE),
    DiagnosticSurfaceMarker("user", DiagnosticSubsystem.PROFILE),
    DiagnosticSurfaceMarker("media", DiagnosticSubsystem.MEDIA),
    DiagnosticSurfaceMarker("video", DiagnosticSubsystem.MEDIA),
    DiagnosticSurfaceMarker("image", DiagnosticSubsystem.MEDIA),
    DiagnosticSurfaceMarker("gif", DiagnosticSubsystem.MEDIA),
    DiagnosticSurfaceMarker("backup", DiagnosticSubsystem.BACKUP),
    DiagnosticSurfaceMarker("restore", DiagnosticSubsystem.BACKUP),
    DiagnosticSurfaceMarker("search", DiagnosticSubsystem.SEARCH),
    DiagnosticSurfaceMarker("post", DiagnosticSubsystem.POSTS),
    DiagnosticSurfaceMarker("feed", DiagnosticSubsystem.FEED),
    DiagnosticSurfaceMarker("home", DiagnosticSubsystem.FEED),
)

private fun newDiagnosticEventId(): String = UUID.randomUUID().toString()

private const val SAFE_DIAGNOSTIC_CODE_LIMIT = 80
private val SAFE_ENUM_TOKEN_PATTERN = Regex("[a-z0-9_]{1,64}")
private val DIAGNOSTIC_EVENT_ID_PATTERN = Regex("[0-9a-fA-F-]{36}")
private val SCOPE_FINGERPRINT_PATTERN = Regex("[0-9a-f]{16}")
