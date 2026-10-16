package app.subshell.prototype.update

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val UPDATE01_ACTION_DEBUG_PREVIEW_WHATS_NEW =
    "app.subshell.prototype.action.DEBUG_PREVIEW_WHATS_NEW"
internal const val UPDATE01_ACTION_DEBUG_PREVIEW_AVAILABLE =
    "app.subshell.prototype.action.DEBUG_PREVIEW_UPDATE_AVAILABLE"

internal sealed interface UpdatePresentationRequest {
    data class UpdateAvailable(
        val release: UpdateManifestRelease,
        val force: Boolean,
    ) : UpdatePresentationRequest

    data object PreviewWhatsNew : UpdatePresentationRequest
}

internal object UpdatePresentationCoordinator {
    private val mutableRequest =
        MutableStateFlow<UpdatePresentationRequest?>(null)

    val request: StateFlow<UpdatePresentationRequest?> =
        mutableRequest.asStateFlow()

    fun offerAvailable(release: UpdateManifestRelease) {
        mutableRequest.value =
            UpdatePresentationRequest.UpdateAvailable(
                release = release,
                force = false,
            )
    }

    fun openAvailable(release: UpdateManifestRelease) {
        mutableRequest.value =
            UpdatePresentationRequest.UpdateAvailable(
                release = release,
                force = true,
            )
    }

    fun previewWhatsNew() {
        mutableRequest.value = UpdatePresentationRequest.PreviewWhatsNew
    }

    /** OCB-656: an automatic offer is dropped; one the reader asked for stays. */
    fun withdrawAvailable() {
        val current = mutableRequest.value
        if (current is UpdatePresentationRequest.UpdateAvailable && !current.force) {
            mutableRequest.value = null
        }
    }

    fun clear(expected: UpdatePresentationRequest? = null) {
        if (expected == null || mutableRequest.value == expected) {
            mutableRequest.value = null
        }
    }
}

internal fun shouldAutoPresentUpdate(
    release: UpdateManifestRelease,
    currentVersionCode: Int,
    lastPresentedVersionCode: Int,
    lastPresentedAtEpochMillis: Long = 0L,
    nowEpochMillis: Long = System.currentTimeMillis(),
): Boolean =
    release.versionCode > currentVersionCode &&
        (
            release.versionCode > lastPresentedVersionCode ||
                // UPDATE09_KNOWN_BAD_VERSION_ADVISORY_V1: a known problem in
                // the installed version brings the dialog back once a day.
                (
                    release.advisoryFor(currentVersionCode) != null &&
                        (
                            lastPresentedAtEpochMillis <= 0L ||
                                nowEpochMillis < lastPresentedAtEpochMillis ||
                                nowEpochMillis - lastPresentedAtEpochMillis >=
                                UPDATE09_ADVISORY_REPEAT_INTERVAL_MILLIS
                            )
                    )
            )

internal fun consumeUpdatePresentationIntent(
    context: Context,
    intent: Intent,
    currentVersionCode: Int,
    debug: Boolean,
): Boolean {
    return when (intent.action) {
        UPDATE01_ACTION_OPEN_UPDATE -> {
            // UPDATE02_OPEN_UPDATE_INTENT_CONSUMED_ONCE_V1: clear the action
            // so a recreated activity does not open the dialog again, and
            // ignore the intent when Recents relaunches the task with it.
            intent.action = null
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
                return true
            }
            val expectedVersion =
                intent.getIntExtra(UPDATE01_EXTRA_VERSION_CODE, 0)
            val release =
                UpdateStateStore(context)
                    .availableRelease(currentVersionCode)
                    ?.takeIf { candidate ->
                        expectedVersion <= 0 ||
                            candidate.versionCode == expectedVersion
                    }
            release?.let(UpdatePresentationCoordinator::openAvailable)
            true
        }

        UPDATE01_ACTION_DEBUG_PREVIEW_WHATS_NEW -> {
            if (!debug) return false
            UpdatePresentationCoordinator.previewWhatsNew()
            true
        }

        UPDATE01_ACTION_DEBUG_PREVIEW_AVAILABLE -> {
            if (!debug) return false
            UpdatePresentationCoordinator.openAvailable(
                UpdateManifestRelease(
                    schemaVersion = UPDATE01_MANIFEST_SCHEMA_VERSION,
                    channel = UPDATE01_BETA_CHANNEL,
                    versionCode = currentVersionCode + 1,
                    versionName = "preview-next-beta",
                    publishedAt = "debug-preview",
                    releaseUrl =
                        "https://github.com/brealorg/subshell/releases/tag/v0.77.0-beta.2",
                    apkSha256 = "0".repeat(64),
                    headline = "A newer subshell beta is available.",
                    highlights = listOf(
                        "This is a local DEV preview of the update dialog.",
                        "Production update checks remain disabled in debug builds.",
                    ),
                ),
            )
            true
        }

        else -> false
    }
}
