package app.subshell.prototype.data.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import app.subshell.prototype.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class DiagnosticReportEnvironment(
    val appVersionName: String,
    val appVersionCode: Long,
    val buildType: String,
    val debugSlot: String,
    val androidApi: Int,
    val androidRelease: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val smallestScreenWidthDp: Int,
    // ISSUE240_DIAGNOSTICS_FLAG_CHANGED_API_VALUES_V1: default/custom/unknown
    // only, never the values.
    val redditApiValues: String = "default",
)

internal data class StagedDiagnosticReport(
    val file: File,
    val text: String,
)

internal data class DiagnosticGitHubRepository(
    val owner: String,
    val name: String,
) {
    init {
        require(owner.matches(GITHUB_OWNER_PATTERN))
        require(name.matches(GITHUB_REPOSITORY_PATTERN))
    }
}

internal data class DiagnosticSupportConfiguration(
    val emailAddress: String? = null,
    val githubRepository: DiagnosticGitHubRepository? = null,
) {
    init {
        require(emailAddress == null || emailAddress.matches(SUPPORT_EMAIL_PATTERN))
    }

    val hasCanonicalDestinations: Boolean
        get() = emailAddress != null || githubRepository != null
}

/** Canonical public support destination for user-initiated diagnostic reports. */
internal object SubshellDiagnosticSupport {
    val configuration = DiagnosticSupportConfiguration(
        githubRepository = DiagnosticGitHubRepository(
            owner = "brealorg",
            name = "subshell",
        ),
    )
}

internal fun diagnosticReportEnvironment(context: Context): DiagnosticReportEnvironment {
    val configuration = context.resources.configuration
    return DiagnosticReportEnvironment(
        appVersionName = safeEnvironmentText(BuildConfig.VERSION_NAME),
        appVersionCode = BuildConfig.VERSION_CODE.toLong(),
        buildType = safeEnvironmentText(BuildConfig.BUILD_TYPE),
        debugSlot = safeEnvironmentText(BuildConfig.DEBUG_SLOT),
        androidApi = Build.VERSION.SDK_INT,
        androidRelease = safeEnvironmentText(Build.VERSION.RELEASE.orEmpty()),
        deviceManufacturer = safeEnvironmentText(Build.MANUFACTURER.orEmpty()),
        deviceModel = safeEnvironmentText(Build.MODEL.orEmpty()),
        screenWidthDp = configuration.screenWidthDp.coerceAtLeast(0),
        screenHeightDp = configuration.screenHeightDp.coerceAtLeast(0),
        smallestScreenWidthDp = configuration.smallestScreenWidthDp.coerceAtLeast(0),
    )
}

internal fun buildDiagnosticReport(
    snapshot: DiagnosticSnapshot,
    environment: DiagnosticReportEnvironment,
    generatedAtEpochMs: Long = System.currentTimeMillis(),
    crashes: List<CrashRecord> = emptyList(),
): String = buildString {
    appendLine("subshell diagnostic report")
    appendLine("schema=DIAG01A1_V1")
    appendLine("generated_utc=${formatUtcTimestamp(generatedAtEpochMs)}")
    appendLine()
    appendLine("[environment]")
    appendLine("app_version=${environment.appVersionName}")
    appendLine("app_version_code=${environment.appVersionCode}")
    appendLine("build_type=${environment.buildType}")
    appendLine("debug_slot=${environment.debugSlot.ifBlank { "none" }}")
    appendLine("android_api=${environment.androidApi}")
    appendLine("android_release=${environment.androidRelease}")
    appendLine("device=${environment.deviceManufacturer} ${environment.deviceModel}".trim())
    appendLine("screen_width_dp=${environment.screenWidthDp}")
    appendLine("screen_height_dp=${environment.screenHeightDp}")
    appendLine("smallest_screen_width_dp=${environment.smallestScreenWidthDp}")
    appendLine("reddit_api_values=${environment.redditApiValues}")
    appendLine()
    appendLine("[buffer]")
    appendLine("events=${snapshot.events.size}")
    appendLine("dropped_events=${snapshot.droppedEventCount}")
    appendLine("invalid_records=${snapshot.invalidRecordCount}")
    appendLine("bytes_used=${snapshot.bytesUsed}")
    appendLine("max_events=${snapshot.maxEvents}")
    appendLine("max_bytes=${snapshot.maxBytes}")
    appendLine()
    appendLine("[privacy]")
    appendLine("raw_content=not_collected")
    appendLine("usernames=not_collected")
    appendLine("oauth_tokens=not_collected")
    appendLine("cookies=not_collected")
    appendLine("request_response_bodies=not_collected")
    // CRASH01_CRASHES_ARE_RECORDED_LOCALLY_V1
    appendLine("exception_messages=not_collected")
    appendLine("crash_details=exception_classes_and_code_locations_only")
    appendLine()
    appendLine("[crashes]")
    crashes.forEach { crash ->
        append(formatUtcTimestamp(crash.timestampEpochMs))
        append(" kind=${crash.kind.token}")
        append(" app_version_code=${crash.appVersionCode ?: "unknown"}")
        if (crash.exceptionClasses.isNotEmpty()) {
            append(" exception=${crash.exceptionClasses.joinToString("<-")}")
        }
        crash.topFrame?.let { append(" top=$it") }
        if (crash.appFrames.isNotEmpty()) {
            append(" app_frames=${crash.appFrames.joinToString(",")}")
        }
        appendLine()
    }
    appendLine()
    appendLine("[events]")
    snapshot.events.forEach { event ->
        append(formatUtcTimestamp(event.timestampEpochMs))
        append(" level=${event.level.name.lowercase(Locale.ROOT)}")
        append(" subsystem=${event.subsystem.name.lowercase(Locale.ROOT)}")
        append(" operation=${event.operation.name.lowercase(Locale.ROOT)}")
        append(" outcome=${event.outcome.name.lowercase(Locale.ROOT)}")
        event.scopeFingerprint?.let { append(" scope=$it") }
        // DIAG04_EVENT_FIELDS_APPENDED_V1
        event.action?.let { append(" action=$it") }
        event.appVersionFrom?.let { append(" from_version_code=$it") }
        event.appVersionTo?.let { append(" to_version_code=$it") }
        event.frames?.let { append(" frames=$it") }
        event.jankFrames?.let { append(" jank_frames=$it") }
        event.worstFrameMs?.let { append(" worst_frame_ms=$it") }
        event.failure?.let { failure ->
            failure.httpStatusCode?.let { append(" http=$it") }
            failure.retryable?.let { append(" retryable=$it") }
            failure.retryAfterMs?.let { append(" retry_after_ms=$it") }
            failure.transportKind?.let { append(" transport=$it") }
            failure.protocolKind?.let { append(" protocol=$it") }
            failure.jsonPath?.let { append(" json_path=$it") }
            failure.localKind?.let { append(" local=$it") }
            failure.exceptionClass?.let { append(" exception=$it") }
            failure.providerCode?.let { append(" provider_code=${reportToken(it)}") }
            failure.mediaCode?.let { append(" media_code=${reportToken(it)}") }
            failure.networkValidated?.let { append(" network_validated=$it") }
            failure.networkMetered?.let { append(" network_metered=$it") }
            failure.deviceIdle?.let { append(" device_idle=$it") }
        }
        appendLine()
    }
}

internal fun stageDiagnosticReport(
    context: Context,
    reportText: String,
    generatedAtEpochMs: Long = System.currentTimeMillis(),
): StagedDiagnosticReport {
    val directory = File(context.applicationContext.cacheDir, DIAGNOSTIC_REPORT_DIRECTORY)
    if (!directory.exists() && !directory.mkdirs()) {
        throw IllegalStateException("Unable to create diagnostic report directory")
    }
    directory.listFiles()?.forEach { existing ->
        if (existing.isFile) runCatching { existing.delete() }
    }
    val file = File(
        directory,
        "subshell-diagnostic-${formatFileTimestamp(generatedAtEpochMs)}.txt",
    )
    FileOutputStream(file).use { output ->
        output.write(reportText.toByteArray(Charsets.UTF_8))
        output.fd.sync()
    }
    return StagedDiagnosticReport(file = file, text = reportText)
}

internal fun diagnosticReportClipboardText(report: StagedDiagnosticReport): String =
    report.text

internal fun copyDiagnosticReport(
    context: Context,
    report: StagedDiagnosticReport,
): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    return runCatching {
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "subshell diagnostic report",
                diagnosticReportClipboardText(report),
            ),
        )
        true
    }.getOrDefault(false)
}

internal fun shareDiagnosticReport(
    context: Context,
    report: StagedDiagnosticReport,
): Boolean {
    val uri = diagnosticReportUri(context, report) ?: return false
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "subshell diagnostic report")
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "subshell diagnostic report", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching {
        context.startActivity(Intent.createChooser(send, "Share diagnostic report"))
        true
    }.getOrDefault(false)
}

internal fun shareDiagnosticReportByEmail(
    context: Context,
    report: StagedDiagnosticReport,
    emailAddress: String,
    environment: DiagnosticReportEnvironment,
): Boolean {
    val normalizedEmail = emailAddress.trim().takeIf { it.matches(SUPPORT_EMAIL_PATTERN) }
        ?: return false
    val uri = diagnosticReportUri(context, report) ?: return false
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(normalizedEmail))
        putExtra(
            Intent.EXTRA_SUBJECT,
            "subshell diagnostic report · ${environment.appVersionName}",
        )
        putExtra(
            Intent.EXTRA_TEXT,
            "Please describe what happened. A sanitized subshell diagnostic report is attached.",
        )
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "subshell diagnostic report", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching {
        context.startActivity(Intent.createChooser(send, "Send diagnostic report by email"))
        true
    }.getOrDefault(false)
}

internal fun buildPrefilledDiagnosticGitHubIssueUrl(
    repository: DiagnosticGitHubRepository,
    environment: DiagnosticReportEnvironment,
): String {
    val issueEndpoint = java.net.URI(
        "https",
        "github.com",
        "/${repository.owner}/${repository.name}/issues/new",
        null,
    ).toASCIIString()
    val additionalContext = buildString {
        append(
            "A sanitized diagnostic report was generated in subshell. " +
                "Attach the .txt report if it is needed for triage.",
        )
        appendLine()
        appendLine()
        append(
            "Screen: ${environment.screenWidthDp} × ${environment.screenHeightDp} dp " +
                "(smallest ${environment.smallestScreenWidthDp} dp)",
        )
    }
    val fields = listOf(
        "template" to GITHUB_BUG_REPORT_TEMPLATE,
        "version" to "${environment.appVersionName} (${environment.appVersionCode})",
        "android" to "Android ${environment.androidRelease} (API ${environment.androidApi})",
        "device" to "${environment.deviceManufacturer} ${environment.deviceModel}".trim(),
        "additional" to additionalContext,
    )
    return issueEndpoint + "?" + fields.joinToString("&") { (key, value) ->
        "${urlEncode(key)}=${urlEncode(value)}"
    }
}

internal fun openPrefilledDiagnosticGitHubIssue(
    context: Context,
    repository: DiagnosticGitHubRepository,
    environment: DiagnosticReportEnvironment,
): Boolean {
    val url = buildPrefilledDiagnosticGitHubIssueUrl(repository, environment)
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    }.getOrDefault(false)
}

private fun diagnosticReportUri(
    context: Context,
    report: StagedDiagnosticReport,
): Uri? {
    if (!report.file.isFile || report.file.length() <= 0L) return null
    return runCatching {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            report.file,
        )
    }.getOrNull()
}

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

private fun reportToken(value: String): String =
    value
        .replace(Regex("[\\p{Cc}\\p{Cf}\\s]+"), "_")
        .take(MAX_REPORT_TOKEN_LENGTH)
        .ifBlank { "redacted" }

private fun safeEnvironmentText(value: String): String =
    value
        .replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_ENVIRONMENT_TEXT_LENGTH)

private fun formatUtcTimestamp(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(epochMs.coerceAtLeast(0L)))

private fun formatFileTimestamp(epochMs: Long): String =
    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(epochMs.coerceAtLeast(0L)))

private const val DIAGNOSTIC_REPORT_DIRECTORY = "diagnostics_reports"
private const val GITHUB_BUG_REPORT_TEMPLATE = "bug_report.yml"
private const val MAX_REPORT_TOKEN_LENGTH = 160
private const val MAX_ENVIRONMENT_TEXT_LENGTH = 96
private val GITHUB_OWNER_PATTERN = Regex("[A-Za-z0-9-]{1,100}")
private val GITHUB_REPOSITORY_PATTERN = Regex("[A-Za-z0-9._-]{1,100}")
private val SUPPORT_EMAIL_PATTERN = Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
