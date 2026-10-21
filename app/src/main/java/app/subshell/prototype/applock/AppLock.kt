package app.subshell.prototype.applock

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import app.subshell.prototype.inbox.InboxNotificationCenter

// ISSUE38_APP_LOCK_V1: optional device unlock for the whole app (issue #38).
// Device-local: a security choice for this device, never part of a Subshell
// backup.

internal const val APP_LOCK_MAX_DELAY_MINUTES = 24 * 60
internal const val APP_LOCK_DEFAULT_DELAY_MINUTES = 1

/** Immediately, 1 min, 5 min, 15 min, 1 h. Any other value is a custom delay. */
internal val APP_LOCK_PRESET_DELAY_MINUTES = listOf(0, 1, 5, 15, 60)

/**
 * A trip out of the app that Subshell started itself (browser login, picker,
 * backup file, share sheet) does not lock, unless it lasts longer than this.
 */
internal const val APP_LOCK_EXTERNAL_TRIP_GRACE_MS = 10 * 60 * 1_000L

internal data class AppLockSettings(
    val enabled: Boolean = false,
    val delayMinutes: Int = APP_LOCK_DEFAULT_DELAY_MINUTES,
    val hideNotificationContent: Boolean = false,
)

/**
 * Notifications say only that there is new activity. Background sync cannot
 * see whether the app is unlocked right now, so this holds whenever the lock
 * is on.
 */
internal val AppLockSettings.hidesNotificationContent: Boolean
    get() = enabled && hideNotificationContent

internal fun normalizeAppLockDelayMinutes(minutes: Int): Int =
    minutes.coerceIn(0, APP_LOCK_MAX_DELAY_MINUTES)

/** A custom delay from hours and minutes, each part clamped first. */
internal fun appLockDelayMinutes(hours: Int, minutes: Int): Int =
    normalizeAppLockDelayMinutes(hours.coerceIn(0, 24) * 60 + minutes.coerceIn(0, 59))

internal fun AppLockSettings.normalized(): AppLockSettings =
    copy(delayMinutes = normalizeAppLockDelayMinutes(delayMinutes))

/**
 * When the app is locked. Times are monotonic milliseconds
 * (`SystemClock.elapsedRealtime()` in production).
 *
 * A new controller starts locked when the lock is on: a new controller means
 * a new process or a new launch after the activity finished.
 */
internal class AppLockController(initial: AppLockSettings) {
    var settings: AppLockSettings by mutableStateOf(initial.normalized())
        private set

    var locked: Boolean by mutableStateOf(settings.enabled)
        private set

    private var backgroundSinceMs: Long? = null
    private var externalTripStartedMs: Long? = null

    /** Turning the lock on does not lock now: the user has just unlocked to do it. */
    fun updateSettings(next: AppLockSettings) {
        settings = next.normalized()
        if (!settings.enabled) {
            locked = false
            backgroundSinceMs = null
            externalTripStartedMs = null
        }
    }

    fun onExternalTripStarted(nowMs: Long) {
        if (settings.enabled && !locked) externalTripStartedMs = nowMs
    }

    /**
     * Nothing is recorded while already locked: the unlock itself may leave
     * the activity (the device credential screen before Android 11). With no
     * delay and no exempt trip the lock engages here, before the app is seen
     * again.
     */
    fun onStopped(nowMs: Long, changingConfigurations: Boolean) {
        if (!settings.enabled || changingConfigurations || locked) return
        if (settings.delayMinutes == 0 && externalTripStartedMs == null) {
            locked = true
            return
        }
        backgroundSinceMs = nowMs
    }

    fun onStarted(nowMs: Long) {
        val since = backgroundSinceMs
        val tripStarted = externalTripStartedMs
        backgroundSinceMs = null
        externalTripStartedMs = null
        if (!settings.enabled || since == null) return
        if (tripStarted != null && nowMs - tripStarted in 0..APP_LOCK_EXTERNAL_TRIP_GRACE_MS) return
        val away = nowMs - since
        if (away < 0 || away >= settings.delayMinutes * 60_000L) locked = true
    }

    /** OCB-392: the start that marked a trip failed; nothing left the app. */
    fun onExternalTripFailed() {
        externalTripStartedMs = null
    }

    /** A started trip that never took the app to the background (a translucent picker) ends here. */
    fun onResumed() {
        externalTripStartedMs = null
    }

    fun unlock() {
        locked = false
    }
}

internal const val APP_LOCK_PREFERENCES = "subshell_app_lock"
internal const val APP_LOCK_ENABLED_KEY = "app_lock_enabled"
internal const val APP_LOCK_DELAY_MINUTES_KEY = "app_lock_delay_minutes"
internal const val APP_LOCK_HIDE_NOTIFICATION_CONTENT_KEY = "app_lock_hide_notification_content"

internal object AppLockSettingsStore {
    fun read(context: Context): AppLockSettings {
        val preferences = context.applicationContext
            .getSharedPreferences(APP_LOCK_PREFERENCES, Context.MODE_PRIVATE)
        return AppLockSettings(
            enabled = preferences.getBoolean(APP_LOCK_ENABLED_KEY, false),
            delayMinutes = preferences.getInt(
                APP_LOCK_DELAY_MINUTES_KEY,
                APP_LOCK_DEFAULT_DELAY_MINUTES,
            ),
            hideNotificationContent = preferences.getBoolean(
                APP_LOCK_HIDE_NOTIFICATION_CONTENT_KEY,
                false,
            ),
        ).normalized()
    }

    fun write(context: Context, settings: AppLockSettings) {
        val normalized = settings.normalized()
        val hidBefore = read(context).hidesNotificationContent
        context.applicationContext
            .getSharedPreferences(APP_LOCK_PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                putBoolean(APP_LOCK_ENABLED_KEY, normalized.enabled)
                putInt(APP_LOCK_DELAY_MINUTES_KEY, normalized.delayMinutes)
                putBoolean(
                    APP_LOCK_HIDE_NOTIFICATION_CONTENT_KEY,
                    normalized.hideNotificationContent,
                )
            }
        // LOW_INBOX_HIDING_CONTENT_REDACTS_SHOWN_NOTIFICATIONS_V1 (OCB-563)
        if (!hidBefore && normalized.hidesNotificationContent) {
            runCatching { InboxNotificationCenter.redactShownNotifications(context) }
        }
    }
}

/**
 * Keeps the controller across rotation and activity recreation. A new
 * process or a new launch after the activity finished gets a new one, which
 * starts locked. A plain ViewModel: the store is read with the caller's
 * context, not a factory-cached Application.
 */
class AppLockViewModel : ViewModel() {
    private var controller: AppLockController? = null

    internal fun controller(context: Context): AppLockController =
        controller ?: AppLockController(AppLockSettingsStore.read(context)).also { controller = it }
}

/**
 * MED6_UNLOCK_PROMPT_NOT_REOPENED_BY_ITS_OWN_SCREEN_V1 (OCB-575): when to
 * show the unlock prompt. Before Android 11 the device credential screen is
 * its own activity: Subshell stops behind it, and clearing the "shown" flag
 * in onStop made onResume open the prompt again after Back, so it could not
 * be cancelled. A stop while the prompt is open no longer clears the flag; a
 * cancelled prompt stays closed until the user leaves and returns.
 */
internal class AppUnlockPromptGate {
    private var shown = false
    private var inFlight = false

    /** True when a prompt should open now; it is then marked open. */
    fun shouldPrompt(locked: Boolean): Boolean {
        if (!locked || shown) return false
        shown = true
        inFlight = true
        return true
    }

    /** A prompt opened another way (the lock screen's Unlock button). */
    fun onPromptStarted() {
        shown = true
        inFlight = true
    }

    fun onPromptFinished(unlocked: Boolean) {
        inFlight = false
        if (unlocked) shown = false
    }

    fun onStopped() {
        if (!inFlight) shown = false
    }
}
