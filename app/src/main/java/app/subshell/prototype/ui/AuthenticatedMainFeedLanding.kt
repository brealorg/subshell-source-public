package app.subshell.prototype.ui

import app.subshell.prototype.data.account.RedditSubscriptionsUiState

internal enum class AuthenticatedMainFeedLandingDecision {
    WAIT,
    HOME,
    POPULAR,
    ABORT,
}

internal fun authenticatedMainFeedLandingRouteEligible(
    currentRoute: SubshellRoute,
    activeCustomFeedPath: String?,
): Boolean =
    currentRoute is SubshellRoute.Main &&
        currentRoute.destination == MainDestination.HOME &&
        activeCustomFeedPath == null

internal fun resolveAuthenticatedLandingFeed(
    accountStableId: String,
    subscriptions: RedditSubscriptionsUiState,
): AuthenticatedMainFeedLandingDecision = when {
    !subscriptions.matches(accountStableId) ->
        AuthenticatedMainFeedLandingDecision.WAIT

    subscriptions.loading ->
        AuthenticatedMainFeedLandingDecision.WAIT

    subscriptions.failureKind != null ->
        AuthenticatedMainFeedLandingDecision.ABORT

    !subscriptions.loaded || subscriptions.fromCache ->
        AuthenticatedMainFeedLandingDecision.WAIT

    subscriptions.communities.isEmpty() ->
        AuthenticatedMainFeedLandingDecision.POPULAR

    else ->
        AuthenticatedMainFeedLandingDecision.HOME
}

internal fun authoritativeZeroSubscriptionHome(
    accountStableId: String,
    source: FeedSource,
    subscriptions: RedditSubscriptionsUiState,
): Boolean =
    source == FeedSource.HOME &&
        subscriptions.matches(accountStableId) &&
        subscriptions.loaded &&
        !subscriptions.fromCache &&
        !subscriptions.loading &&
        subscriptions.failureKind == null &&
        subscriptions.communities.isEmpty()
