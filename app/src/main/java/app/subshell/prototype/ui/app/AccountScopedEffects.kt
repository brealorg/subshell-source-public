package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.subshell.prototype.ui.AuthenticatedMainFeedLandingDecision
import app.subshell.prototype.ui.FeedSource
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.authenticatedMainFeedLandingRouteEligible
import app.subshell.prototype.ui.resolveAuthenticatedLandingFeed
import app.subshell.prototype.ui.uiDebugLog

/**
 * The effects SubshellApp runs when the active account changes: the
 * account-scoped moderator directory refresh (MOD01A1) and the one-shot authenticated main-feed landing after a connect
 * (AUTH07B2: wait for authoritative subscriptions, then Home or Popular;
 * abort on an account change or a manual choice, defer on a context route).
 *
 * Moved out of SubshellApp verbatim, in their original order.
 */
@Composable
internal fun AccountScopedEffects(
    state: SubshellState,
    moderatorTools: ModeratorToolsEntryPoints,
    redditFeed: FeedEntryPoints,
    activeCustomFeedPath: String?,
    pendingAuthenticatedLandingAccountIdState: MutableState<String?>,
) {
    val onLoadRedditModeratorCommunities = moderatorTools.onLoadRedditModeratorCommunities
    val redditSubscriptionsState = redditFeed.redditSubscriptionsState
    var pendingAuthenticatedLandingAccountId by pendingAuthenticatedLandingAccountIdState
    // MOD01A1_ACCOUNT_SCOPED_MODERATOR_DIRECTORY_REFRESH_V1
    LaunchedEffect(
        state.activeAccountId,
        state.activeAccountIsAuthenticated,
    ) {
        if (state.activeAccountIsAuthenticated) {
            onLoadRedditModeratorCommunities(state.activeAccountId, false)
        }
    }
    // LOWUI8_ACCOUNT_RESETS_ONLY_ON_A_CHANGE_V1 (OCB-109): the inbox tab is
    // reset by its rememberSaveable(state.activeAccountId) owner in
    // SubshellApp; an effect here also ran on every recreation and threw
    // the restored tab away.

    LaunchedEffect(
        pendingAuthenticatedLandingAccountId,
        state.activeAccountId,
        state.activeAccountIsAuthenticated,
        state.destination,
        state.currentNavigationRoute,
        activeCustomFeedPath,
        redditSubscriptionsState.accountStableId,
        redditSubscriptionsState.loading,
        redditSubscriptionsState.loaded,
        redditSubscriptionsState.fromCache,
        redditSubscriptionsState.failureKind,
        redditSubscriptionsState.contentRevision,
        redditSubscriptionsState.communities.size,
    ) {
        val pendingAccountId =
            pendingAuthenticatedLandingAccountId
                ?: return@LaunchedEffect

        if (
            !state.activeAccountIsAuthenticated ||
            pendingAccountId != state.activeAccountId
        ) {
            pendingAuthenticatedLandingAccountId = null
            return@LaunchedEffect
        }

        if (
            !authenticatedMainFeedLandingRouteEligible(
                currentRoute = state.currentNavigationRoute,
                activeCustomFeedPath = activeCustomFeedPath,
            )
        ) {
            uiDebugLog(
                "SubshellAuthLanding",
                "phase=context_route_deferred",
            )
            return@LaunchedEffect
        }

        when (
            resolveAuthenticatedLandingFeed(
                accountStableId = pendingAccountId,
                subscriptions = redditSubscriptionsState,
            )
        ) {
            AuthenticatedMainFeedLandingDecision.WAIT -> Unit
            AuthenticatedMainFeedLandingDecision.ABORT -> {
                pendingAuthenticatedLandingAccountId = null
            }
            AuthenticatedMainFeedLandingDecision.HOME -> {
                pendingAuthenticatedLandingAccountId = null
                state.applyAuthenticatedMainFeedLanding(
                    FeedSource.HOME,
                )
            }
            AuthenticatedMainFeedLandingDecision.POPULAR -> {
                pendingAuthenticatedLandingAccountId = null
                state.applyAuthenticatedMainFeedLanding(
                    FeedSource.POPULAR,
                )
            }
        }
    }
}
