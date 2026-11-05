package io.github.brealorg.subshell.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.brealorg.subshell.data.account.CustomFeedSummary
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiState
import io.github.brealorg.subshell.ui.BackTwiceToExitNotice
import io.github.brealorg.subshell.ui.BackTwiceToExitNoticeScope
import io.github.brealorg.subshell.ui.BackTwiceToExitWindowMillis
import io.github.brealorg.subshell.ui.MainDestination
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.error.TrayTone
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import io.github.brealorg.subshell.ui.rootBackFeedSource
import io.github.brealorg.subshell.ui.rootBackLandingSource
import kotlinx.coroutines.delay

/**
 * SubshellApp's own Back handlers, in their original composition order
 * (a later-composed handler wins, so the drawer and the route hosts,
 * composed after this call, keep Back first):
 * 1. the route stack, with the predictive gesture (ISSUE27);
 * 2. at the root, back to the landing feed source (ROOTBACK01);
 * 3. at the true root, the optional back-twice-to-exit prompt (ISSUE26);
 * 4. a custom feed shown at Home leaves for the main feed (ISSUE25).
 * Their enables are mutually exclusive.
 *
 * Moved out of SubshellApp verbatim. The two states the scaffold reads
 * (the predictive gesture and the exit prompt's tray) and the active
 * custom feed stay owned by SubshellApp and are passed in.
 */
@Composable
internal fun RootBackHandlers(
    state: SubshellState,
    predictiveRouteBackState: PredictiveRouteBackState,
    backExitNoticeState: ViewportErrorHostState,
    activeCustomFeed: CustomFeedSummary?,
    activeCustomFeedPathState: MutableState<String?>,
    pendingAuthenticatedLandingAccountIdState: MutableState<String?>,
    redditSubscriptionsState: RedditSubscriptionsUiState,
) {
    var activeCustomFeedPath by activeCustomFeedPathState
    var pendingAuthenticatedLandingAccountId by pendingAuthenticatedLandingAccountIdState
    PredictiveRouteBackHandler(
        enabled = state.canNavigateBack,
        state = predictiveRouteBackState,
    ) {
        state.navigateBack()
    }
    // ROOTBACK01_DEFAULT_ROOT_BACK_V1: see RootBackPolicy.kt. Off while a
    // custom feed is shown; the handler below leaves it first.
    val rootBackSource = rootBackFeedSource(
        destination = state.destination,
        feedSource = state.feedSource,
        landingSource = rootBackLandingSource(
            accountStableId = state.activeAccountId,
            authenticated = state.activeAccountIsAuthenticated,
            subscriptions = redditSubscriptionsState,
        ),
    )
    BackHandler(
        enabled = !state.canNavigateBack &&
            activeCustomFeed == null &&
            rootBackSource != null,
    ) {
        rootBackSource?.let { source ->
            pendingAuthenticatedLandingAccountId = null
            state.selectFeedSource(source)
        }
    }
    // ISSUE26_BACK_TWICE_TO_EXIT_V1: optional. At the true root (nothing
    // above claims Back), the first Back only shows the prompt in the
    // viewport tray, on its own host state so it neither replaces a real
    // error nor reaches diagnostics. While it shows, this handler is off,
    // so the next Back reaches the system. It hides after the window,
    // when the root is left, or when dismissed.
    val backExitArmed = backExitNoticeState.currentRequest != null
    val atBackExitRoot = !state.canNavigateBack &&
        activeCustomFeed == null &&
        rootBackSource == null
    LaunchedEffect(backExitArmed, atBackExitRoot) {
        if (!backExitArmed) return@LaunchedEffect
        if (atBackExitRoot) delay(BackTwiceToExitWindowMillis)
        backExitNoticeState.clear(BackTwiceToExitNoticeScope)
    }
    BackHandler(
        enabled = state.backTwiceToExit && atBackExitRoot && !backExitArmed,
    ) {
        // clear() first forgets an earlier dismissal of the same prompt.
        backExitNoticeState.clear(BackTwiceToExitNoticeScope)
        backExitNoticeState.present(
            scopeKey = BackTwiceToExitNoticeScope,
            presentation = BackTwiceToExitNotice,
            failureDetails = null,
            onRetry = null,
            // ISSUE222_TRAY_HAS_A_NEUTRAL_TONE_V1: a prompt, not an error.
            tone = TrayTone.INFO,
        )
    }
    // ISSUE25_CUSTOM_FEED_BACK_V1: a custom feed replaces the main feed at
    // Home without a route, so Back leaves it for the main feed. Composed
    // before the drawer and the route hosts, which keep Back first.
    BackHandler(
        enabled = activeCustomFeed != null &&
            !state.canNavigateBack &&
            state.destination == MainDestination.HOME,
    ) {
        activeCustomFeedPath = null
    }
}
