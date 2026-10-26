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

// ISSUE257_COLD_START_LANDS_ON_CACHED_SUBSCRIPTIONS_V1 (#257, owner "1a"): at a
// cold start of the account that was already active, a cached list that is
// not empty is enough to choose Home, so Home shows its cached posts while
// Reddit refreshes (the wait for Reddit's answer cost ~0.9 s on every start).
// Only a missing or empty cache still waits: the cache never decides that an
// account has zero communities. A sign-in or account switch waits as before.
internal fun resolveAuthenticatedLandingFeed(
    accountStableId: String,
    subscriptions: RedditSubscriptionsUiState,
    coldStart: Boolean = false,
): AuthenticatedMainFeedLandingDecision = when {
    !subscriptions.matches(accountStableId) ->
        AuthenticatedMainFeedLandingDecision.WAIT

    coldStart &&
        subscriptions.loaded &&
        subscriptions.fromCache &&
        subscriptions.failureKind == null &&
        subscriptions.communities.isNotEmpty() ->
        AuthenticatedMainFeedLandingDecision.HOME

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
