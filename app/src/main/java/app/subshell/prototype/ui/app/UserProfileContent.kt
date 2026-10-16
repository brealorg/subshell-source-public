package app.subshell.prototype.ui.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.RedditAccountHistoryUiState
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.account.RedditSavedHistoryUiState
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.screens.UserProfileScreen

/**
 * The two canonical UserProfileScreen hosts every profile surface composes:
 * the signed-in account's You profile (NEXT01A4A) and a public user's
 * profile (NEXT01A6). Both are content lambdas the route hosts receive, so
 * each surface supplies its own modifier, padding and selection callbacks
 * while the profile data and loaders stay wired in one place.
 *
 * Moved out of SubshellApp verbatim. This function is composable so the
 * two lambdas are still remembered by the Compose compiler, as they were as
 * locals of SubshellApp.
 */
@Composable
internal fun userProfileContents(
    state: SubshellState,
    redditFeedState: RedditFeedUiState,
    onRefreshRedditUserSubmitted: (String, String) -> Unit,
    onAppendRedditUserSubmitted: (String, String) -> Unit,
    redditAccountHistoryState: RedditAccountHistoryUiState,
    onRefreshRedditAccountHistory: (String, String, RedditAccountHistoryKind) -> Unit,
    onAppendRedditAccountHistory: (String, String, RedditAccountHistoryKind) -> Unit,
    redditSavedHistoryState: RedditSavedHistoryUiState,
    onRefreshRedditSavedHistory: (String, String) -> Unit,
    onAppendRedditSavedHistory: (String, String) -> Unit,
    onResolveRedditProfileCommentTarget: (String, String, String, (Post?) -> Unit) -> Unit,
    onResolveRedditSavedCommentTarget: (String, String, String, (Post?) -> Unit) -> Unit,
    onAuthorizeRedditProfileHistory: (String, String) -> Unit,
    contentSafetyState: ContentSafetyUiState,
    onRefreshBlockedAccounts: (String) -> Unit,
    onAppendBlockedAccounts: (String) -> Unit,
    onAuthorizeBlockedAccounts: (String) -> Unit = {},
): Pair<
    @Composable (
        String,
        Modifier,
        PaddingValues,
        ((Post, String?) -> Unit)?,
        ((Int) -> Unit)?,
        ((String) -> Unit)?,
    ) -> Unit,
    @Composable (
        String,
        Modifier,
        PaddingValues,
        (() -> Unit)?,
        Boolean,
        ((Post, String?) -> Unit)?,
        ((Int) -> Unit)?,
    ) -> Unit,
    > {
    // NEXT01A4A_SHARED_MAIN_YOU_PROFILE_HOST_V1
    val mainYouProfileContent:
        @Composable (
            String,
            Modifier,
            PaddingValues,
            ((app.subshell.prototype.model.Post, String?) -> Unit)?,
            ((Int) -> Unit)?,
            ((String) -> Unit)?,
        ) -> Unit =
        { profileUsername, profileModifier, profileContentPadding,
            profileOpenContentSelection, profileTabSelected,
            profileOpenBlockedAccount ->
            UserProfileScreen(
                username = profileUsername,
                state = state,
                modifier = profileModifier,
                redditFeedState = redditFeedState,
                onRefreshRedditUserSubmitted =
                    onRefreshRedditUserSubmitted,
                onAppendRedditUserSubmitted =
                    onAppendRedditUserSubmitted,
                redditAccountHistoryState =
                    redditAccountHistoryState,
                onRefreshRedditAccountHistory =
                    onRefreshRedditAccountHistory,
                onAppendRedditAccountHistory =
                    onAppendRedditAccountHistory,
                redditSavedHistoryState = redditSavedHistoryState,
                onRefreshRedditSavedHistory = onRefreshRedditSavedHistory,
                onAppendRedditSavedHistory = onAppendRedditSavedHistory,
                onResolveRedditProfileCommentTarget =
                    onResolveRedditProfileCommentTarget,
                onResolveRedditSavedCommentTarget =
                    onResolveRedditSavedCommentTarget,
                onAuthorizeRedditProfileHistory =
                    onAuthorizeRedditProfileHistory,
                blockedAccountsState = contentSafetyState.blockedAccounts,
                onRefreshBlockedAccounts = onRefreshBlockedAccounts,
                onAppendBlockedAccounts = onAppendBlockedAccounts,
                onAuthorizeBlockedAccounts = onAuthorizeBlockedAccounts,
                onOpenContentSelection = profileOpenContentSelection,
                onProfileTabSelected = profileTabSelected,
                onOpenBlockedAccount = profileOpenBlockedAccount,
                contentPadding = profileContentPadding,
            )
        }
    // NEXT01A4A_SHARED_MAIN_YOU_PROFILE_HOST_END_V1

    // NEXT01A6_SHARED_PUBLIC_USER_PROFILE_HOST_V1
    val publicUserProfileContent:
        @Composable (
            String,
            Modifier,
            PaddingValues,
            (() -> Unit)?,
            Boolean,
            ((app.subshell.prototype.model.Post, String?) -> Unit)?,
            ((Int) -> Unit)?,
        ) -> Unit =
        {
                profileUsername,
                profileModifier,
                profileContentPadding,
                profileOnBack,
                profileShowBackButton,
                profileOpenContentSelection,
                profileTabSelected,
            ->
            UserProfileScreen(
                username = profileUsername,
                state = state,
                modifier = profileModifier,
                onBack = profileOnBack,
                showBackButton = profileShowBackButton,
                redditFeedState = redditFeedState,
                onRefreshRedditUserSubmitted =
                    onRefreshRedditUserSubmitted,
                onAppendRedditUserSubmitted =
                    onAppendRedditUserSubmitted,
                redditAccountHistoryState =
                    redditAccountHistoryState,
                onRefreshRedditAccountHistory =
                    onRefreshRedditAccountHistory,
                onAppendRedditAccountHistory =
                    onAppendRedditAccountHistory,
                redditSavedHistoryState =
                    redditSavedHistoryState,
                onRefreshRedditSavedHistory =
                    onRefreshRedditSavedHistory,
                onAppendRedditSavedHistory =
                    onAppendRedditSavedHistory,
                onResolveRedditProfileCommentTarget =
                    onResolveRedditProfileCommentTarget,
                onResolveRedditSavedCommentTarget =
                    onResolveRedditSavedCommentTarget,
                onAuthorizeRedditProfileHistory =
                    onAuthorizeRedditProfileHistory,
                blockedAccountsState =
                    contentSafetyState.blockedAccounts,
                onRefreshBlockedAccounts =
                    onRefreshBlockedAccounts,
                onAppendBlockedAccounts =
                    onAppendBlockedAccounts,
                onAuthorizeBlockedAccounts =
                    onAuthorizeBlockedAccounts,
                onOpenContentSelection =
                    profileOpenContentSelection,
                onProfileTabSelected =
                    profileTabSelected,
                contentPadding =
                    profileContentPadding,
            )
        }
    // NEXT01A6_SHARED_PUBLIC_USER_PROFILE_HOST_END_V1

    return mainYouProfileContent to publicUserProfileContent
}
