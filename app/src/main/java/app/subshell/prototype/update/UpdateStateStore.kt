package app.subshell.prototype.update

import android.content.Context
import androidx.core.content.edit
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Three days of checks without a usable manifest before Help & about says so. */
internal const val UPDATE04_CHECK_FAILING_AFTER_MILLIS = 3L * 24L * 60L * 60L * 1000L

internal class UpdateStateStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun observeProcessStart(currentVersionCode: Int): InstalledVersionTransition {
        require(currentVersionCode > 0)
        val previous = preferences.getInt(KEY_LAST_OBSERVED_VERSION_CODE, 0)
        val transition = installedVersionTransition(previous, currentVersionCode)

        preferences.edit {
            putInt(KEY_LAST_OBSERVED_VERSION_CODE, currentVersionCode)
            if (
                transition == InstalledVersionTransition.UPGRADE &&
                lastAcknowledgedChangelogVersionCode() < currentVersionCode
            ) {
                putInt(KEY_PENDING_WHATS_NEW_VERSION_CODE, currentVersionCode)
            }
        }
        return transition
    }

    fun recordPackageReplaced(currentVersionCode: Int) {
        require(currentVersionCode > 0)
        preferences.edit {
            putInt(KEY_LAST_OBSERVED_VERSION_CODE, currentVersionCode)
            if (lastAcknowledgedChangelogVersionCode() < currentVersionCode) {
                putInt(KEY_PENDING_WHATS_NEW_VERSION_CODE, currentVersionCode)
            }
        }
    }

    fun pendingWhatsNewVersionCode(): Int =
        preferences.getInt(KEY_PENDING_WHATS_NEW_VERSION_CODE, 0)

    fun lastAcknowledgedChangelogVersionCode(): Int =
        preferences.getInt(KEY_LAST_ACKNOWLEDGED_CHANGELOG_VERSION_CODE, 0)

    fun acknowledgeWhatsNew(versionCode: Int) {
        if (versionCode <= 0) return
        preferences.edit {
            putInt(
                KEY_LAST_ACKNOWLEDGED_CHANGELOG_VERSION_CODE,
                maxOf(lastAcknowledgedChangelogVersionCode(), versionCode),
            )
            if (pendingWhatsNewVersionCode() <= versionCode) {
                remove(KEY_PENDING_WHATS_NEW_VERSION_CODE)
            }
        }
    }

    fun shouldCheck(
        nowEpochMillis: Long,
        minimumIntervalMillis: Long,
    ): Boolean {
        if (nowEpochMillis <= 0L || minimumIntervalMillis < 0L) return false
        val last = preferences.getLong(KEY_LAST_CHECK_EPOCH_MILLIS, 0L)
        // UPDATE02: a last check in the future (the clock was wrong, then
        // corrected) counts as stale instead of blocking checks until then.
        return last <= 0L ||
            nowEpochMillis < last ||
            nowEpochMillis - last >= minimumIntervalMillis
    }

    fun recordCheck(nowEpochMillis: Long) {
        if (nowEpochMillis <= 0L) return
        preferences.edit { putLong(KEY_LAST_CHECK_EPOCH_MILLIS, nowEpochMillis) }
    }

    fun lastPresentedUpdateVersionCode(): Int =
        preferences.getInt(KEY_LAST_PRESENTED_UPDATE_VERSION_CODE, 0)

    fun markUpdatePresented(versionCode: Int) {
        if (versionCode <= 0) return
        preferences.edit {
            putInt(
                KEY_LAST_PRESENTED_UPDATE_VERSION_CODE,
                maxOf(lastPresentedUpdateVersionCode(), versionCode),
            )
        }
    }

    /** UPDATE08: the update dialog asked Android for the notification permission once. */
    fun notificationPermissionAsked(): Boolean =
        preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false)

    fun markNotificationPermissionAsked() {
        preferences.edit { putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true) }
    }

    fun lastNotifiedVersionCode(): Int =
        preferences.getInt(KEY_LAST_NOTIFIED_VERSION_CODE, 0)

    fun markNotified(versionCode: Int) {
        if (versionCode <= 0) return
        preferences.edit {
            putInt(
                KEY_LAST_NOTIFIED_VERSION_CODE,
                maxOf(lastNotifiedVersionCode(), versionCode),
            )
        }
    }

    /**
     * UPDATE03: the notified and presented markers only ever rise, so a
     * manifest with a mistyped high versionCode (2410 for 241) silenced every
     * later release. A manifest that supersedes a higher one (a correction)
     * lowers them below its own versionCode so that release is offered.
     */
    fun lowerMarkersBelow(versionCode: Int) {
        if (versionCode <= 0) return
        val ceiling = versionCode - 1
        preferences.edit {
            if (lastNotifiedVersionCode() > ceiling) {
                putInt(KEY_LAST_NOTIFIED_VERSION_CODE, ceiling)
            }
            if (lastPresentedUpdateVersionCode() > ceiling) {
                putInt(KEY_LAST_PRESENTED_UPDATE_VERSION_CODE, ceiling)
            }
        }
    }

    /**
     * UPDATE04_UPDATE_CHECK_HEALTH_V1
     * When update checks started to produce no usable manifest (a failed
     * fetch, or a manifest this build rejects: newer schema, wrong URL,
     * moved address). Cleared by the next usable manifest.
     */
    fun recordUsableManifest() {
        preferences.edit { remove(KEY_UNUSABLE_MANIFEST_SINCE_EPOCH_MILLIS) }
    }

    fun recordUnusableManifest(nowEpochMillis: Long) {
        if (nowEpochMillis <= 0L) return
        if (preferences.getLong(KEY_UNUSABLE_MANIFEST_SINCE_EPOCH_MILLIS, 0L) > 0L) return
        preferences.edit { putLong(KEY_UNUSABLE_MANIFEST_SINCE_EPOCH_MILLIS, nowEpochMillis) }
    }

    fun updateCheckFailing(nowEpochMillis: Long): Boolean {
        val since = preferences.getLong(KEY_UNUSABLE_MANIFEST_SINCE_EPOCH_MILLIS, 0L)
        return since > 0L && nowEpochMillis - since >= UPDATE04_CHECK_FAILING_AFTER_MILLIS
    }

    /** The last stored release, whether or not it is newer than this build. */
    fun knownRelease(): UpdateManifestRelease? = availableRelease(currentVersionCode = 0)

    fun saveAvailableRelease(release: UpdateManifestRelease?) {
        preferences.edit {
            if (release == null) {
                remove(KEY_AVAILABLE_RELEASE_JSON)
            } else {
                putString(
                    KEY_AVAILABLE_RELEASE_JSON,
                    SubshellNetworkJson.providerJson.encodeToString(release),
                )
            }
        }
    }

    fun availableRelease(currentVersionCode: Int): UpdateManifestRelease? {
        val encoded = preferences.getString(KEY_AVAILABLE_RELEASE_JSON, null)
            ?: return null
        val decoded = runCatching {
            SubshellNetworkJson.providerJson
                .decodeFromString<UpdateManifestRelease>(encoded)
        }.getOrNull()
            ?: return null
        val normalized = normalizeUpdateManifest(decoded) ?: return null
        return normalized.takeIf { it.isNewerThan(currentVersionCode) }
    }

    companion object {
        const val PREFERENCES_NAME = "subshell_device_preferences"

        private const val KEY_LAST_OBSERVED_VERSION_CODE =
            "update01.last_observed_version_code"
        private const val KEY_PENDING_WHATS_NEW_VERSION_CODE =
            "update01.pending_whats_new_version_code"
        private const val KEY_LAST_ACKNOWLEDGED_CHANGELOG_VERSION_CODE =
            "update01.last_acknowledged_changelog_version_code"
        private const val KEY_LAST_CHECK_EPOCH_MILLIS =
            "update01.last_check_epoch_ms"
        private const val KEY_LAST_PRESENTED_UPDATE_VERSION_CODE =
            "update01.last_presented_update_version_code"
        private const val KEY_UNUSABLE_MANIFEST_SINCE_EPOCH_MILLIS =
            "update04.unusable_manifest_since_epoch_ms"
        private const val KEY_LAST_NOTIFIED_VERSION_CODE =
            "update01.last_notified_version_code"
        private const val KEY_AVAILABLE_RELEASE_JSON =
            "update01.available_release_json"
        private const val KEY_NOTIFICATION_PERMISSION_ASKED =
            "update08.notification_permission_asked"
    }
}
