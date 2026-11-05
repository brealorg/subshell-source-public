package io.github.brealorg.subshell.ui.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.brealorg.subshell.ui.SecondaryDestination
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.error.ErrorAction
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.TrayTone
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState

// SETTINGSAUDIT01_REVIEW_SETTINGS_NUDGE_V1 (settings audit, owner 2026-10-13):
// subshell starts with good defaults instead of a longer onboarding, and once,
// three days after a new install first ran, the tray suggests a look through
// Settings. An install that already existed when this shipped never gets it.
// Device-local, not part of backups. The tray's one action button runs
// onRetry, so "Open Settings" is that action with its own label (as #211).

internal const val SETTINGS_REVIEW_NUDGE_SCOPE = "settings-review-nudge"
internal const val SETTINGS_REVIEW_NUDGE_DELAY_MS = 3L * 24L * 60L * 60L * 1_000L

internal const val NUDGE_PREFERENCES = "subshell_settings_review_nudge"
private const val FIRST_RUN_AT_KEY = "first_run_at_epoch_ms"
private const val DONE_KEY = "done"

internal fun settingsReviewNudgePresentation(): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = "Make subshell yours",
    message = "Settings has choices for the theme, feeds, video, comments and more.",
    primaryAction = ErrorAction(ErrorActionKind.RETRY, "Open Settings"),
    secondaryAction = ErrorAction(ErrorActionKind.DISMISS, "Dismiss"),
)

/** What the nudge should do now; a pure decision so it can be tested. */
internal enum class SettingsReviewNudgeStep { MARK_EXISTING_INSTALL, START_CLOCK, WAIT, SHOW, NOTHING }

internal fun settingsReviewNudgeStep(
    done: Boolean,
    firstRunAtEpochMillis: Long?,
    nowEpochMillis: Long,
    newInstall: Boolean,
): SettingsReviewNudgeStep = when {
    done -> SettingsReviewNudgeStep.NOTHING
    firstRunAtEpochMillis == null && !newInstall -> SettingsReviewNudgeStep.MARK_EXISTING_INSTALL
    firstRunAtEpochMillis == null -> SettingsReviewNudgeStep.START_CLOCK
    nowEpochMillis - firstRunAtEpochMillis >= SETTINGS_REVIEW_NUDGE_DELAY_MS -> SettingsReviewNudgeStep.SHOW
    else -> SettingsReviewNudgeStep.WAIT
}

internal object SettingsReviewNudgeStore {
    fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NUDGE_PREFERENCES, Context.MODE_PRIVATE)
}

@Composable
internal fun SettingsReviewNudge(
    state: SubshellState,
    noticeHost: ViewportErrorHostState,
    clock: () -> Long = System::currentTimeMillis,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val preferences = SettingsReviewNudgeStore.preferences(context)
        val firstRunAt = preferences.getLong(FIRST_RUN_AT_KEY, -1L).takeIf { it >= 0L }
        // A new install still shows the first-run interaction-side question;
        // an existing one answered it long ago.
        when (
            settingsReviewNudgeStep(
                done = preferences.getBoolean(DONE_KEY, false),
                firstRunAtEpochMillis = firstRunAt,
                nowEpochMillis = clock(),
                newInstall = state.commentActionOnboardingVisible,
            )
        ) {
            SettingsReviewNudgeStep.MARK_EXISTING_INSTALL ->
                preferences.edit().putBoolean(DONE_KEY, true).apply()
            SettingsReviewNudgeStep.START_CLOCK ->
                preferences.edit().putLong(FIRST_RUN_AT_KEY, clock()).apply()
            SettingsReviewNudgeStep.SHOW -> {
                preferences.edit().putBoolean(DONE_KEY, true).apply()
                noticeHost.present(
                    scopeKey = SETTINGS_REVIEW_NUDGE_SCOPE,
                    presentation = settingsReviewNudgePresentation(),
                    failureDetails = null,
                    onRetry = {
                        noticeHost.clear(SETTINGS_REVIEW_NUDGE_SCOPE)
                        state.openSecondary(SecondaryDestination.SETTINGS)
                    },
                    tone = TrayTone.INFO,
                )
            }
            SettingsReviewNudgeStep.WAIT, SettingsReviewNudgeStep.NOTHING -> Unit
        }
    }
}
