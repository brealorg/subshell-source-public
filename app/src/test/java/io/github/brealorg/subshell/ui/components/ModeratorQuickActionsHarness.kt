package io.github.brealorg.subshell.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.brealorg.subshell.data.account.RedditModeratorActionRequest
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditRemovalReasonUiState
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState

/**
 * ISSUE295_QUEUE_USES_THE_SHEET_V1: the queue writes through the Moderate
 * sheet's entry points and ModeratorQuickActionsHost, as SubshellApp wires
 * them; tests of the queue render their content inside this harness so a tap
 * on Approve, Remove or the sheet goes the app's own way.
 */
@Composable
internal fun ModeratorQuickActionsHarness(
    accountStableId: String,
    actionState: RedditModeratorActionUiState,
    onModerate: (RedditModeratorActionRequest) -> Unit,
    notices: ViewportErrorHostState?,
    removalReasonState: RedditRemovalReasonUiState = RedditRemovalReasonUiState(),
    onLoadRemovalReasons: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onAuthorizeRemovalReasons: () -> Unit = {},
    onAuthorize: () -> Unit = {},
    onRetry: () -> Unit = {},
    savedResponses: SavedResponseActions = SavedResponseActions(
        responses = emptyList(),
        onSave = { _, _ -> null },
        onDelete = {},
    ),
    quickActions: ModeratorQuickActionsState = remember { ModeratorQuickActionsState() },
    canModerate: (String) -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    val entries = ModeratorQuickActionsEntryPoints(
        canModerate = canModerate,
        moderate = { target, action ->
            moderatorQuickActionRequest(accountStableId, target, action)?.let { request ->
                quickActions.awaiting = request
                onModerate(request)
            }
        },
        requestRemoval = { target -> quickActions.pendingRemoval = target },
        openSheet = { target, changeFlair ->
            quickActions.sheetChangeFlair = changeFlair
            quickActions.sheetTarget = target
        },
    )
    CompositionLocalProvider(LocalModeratorQuickActions provides entries) {
        content()
        ModeratorQuickActionsHost(
            quickActions = quickActions,
            accountStableId = accountStableId,
            actionState = actionState,
            removalReasonState = removalReasonState,
            onLoadRemovalReasons = onLoadRemovalReasons,
            onAuthorizeRemovalReasons = onAuthorizeRemovalReasons,
            onModerate = onModerate,
            onAuthorize = onAuthorize,
            savedResponses = savedResponses,
            notices = notices,
            onRetry = onRetry,
        )
    }
}
