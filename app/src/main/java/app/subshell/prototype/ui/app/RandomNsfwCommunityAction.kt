package app.subshell.prototype.ui.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.subshell.prototype.model.RandomNsfwCommunityPick
import app.subshell.prototype.ui.RandomNsfwCommunityPlacement
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.symbols.Symbols
import kotlinx.coroutines.launch
import app.subshell.prototype.ui.components.InlineProgressIndicator

// ISSUE211_RANDOM_NSFW_COMMUNITY_V1 (#211): one pick action for every
// placement the reader can choose (Search, Communities, navigation menu). A
// tap shows "Finding a community…" at once (the owner could not tell a tap
// had registered), a second tap while it runs does nothing, and the pick
// opens only for the account that asked.

@Stable
internal class RandomNsfwCommunityAction(private val onStart: () -> Unit) {
    var inProgress by mutableStateOf(false)
        internal set

    fun start() {
        if (!inProgress) onStart()
    }
}

internal val LocalRandomNsfwCommunityAction =
    staticCompositionLocalOf<RandomNsfwCommunityAction?> { null }

internal const val RANDOM_NSFW_COMMUNITY_SCOPE = "random-nsfw-community"

@Composable
internal fun rememberRandomNsfwCommunityAction(
    state: SubshellState,
    pick: (suspend (String) -> RandomNsfwCommunityPick)?,
    noticeHost: ViewportErrorHostState,
): RandomNsfwCommunityAction? {
    val scope = rememberCoroutineScope()
    val currentPick by rememberUpdatedState(pick)
    if (pick == null) return null
    return remember(state, noticeHost) {
        lateinit var action: RandomNsfwCommunityAction
        action = RandomNsfwCommunityAction {
            val picker = currentPick ?: return@RandomNsfwCommunityAction
            val account = state.activeAccountId
            action.inProgress = true
            noticeHost.clear(RANDOM_NSFW_COMMUNITY_SCOPE)
            scope.launch {
                try {
                    val pick = picker(account)
                    if (state.activeAccountId != account) return@launch
                    when (pick) {
                        is RandomNsfwCommunityPick.Found -> state.openSubreddit(pick.name)
                        // ISSUE263_NO_NSFW_COMMUNITY_OFFERED_IS_NOT_A_FAILURE_V1:
                        // Reddit's own answer is not one a retry changes.
                        is RandomNsfwCommunityPick.NoneOffered -> noticeHost.present(
                            scopeKey = RANDOM_NSFW_COMMUNITY_SCOPE,
                            presentation = ErrorPresentation(
                                kind = ErrorPresentationKind.DEGRADED,
                                title = "Reddit offered no NSFW communities",
                                // ISSUE263_NSFW_SEARCH_PREFERENCE_NOTICE_V1: the usual cause.
                                message = "Reddit returned none for this account; trying again won't change " +
                                    "that. Check that \"Include mature content in search results\" is on " +
                                    "at old.reddit.com/prefs.",
                            ),
                            failureDetails = null,
                            onRetry = null,
                        )
                        RandomNsfwCommunityPick.Failed -> noticeHost.present(
                            scopeKey = RANDOM_NSFW_COMMUNITY_SCOPE,
                            presentation = ErrorPresentation(
                                kind = ErrorPresentationKind.DEGRADED,
                                title = "Couldn't find an NSFW community",
                                message = "Couldn't reach Reddit. Try again in a moment.",
                            ),
                            failureDetails = null,
                            onRetry = null,
                        )
                    }
                } finally {
                    action.inProgress = false
                }
            }
        }
        action
    }
}

/** The button at [placement], shown only when the reader chose it and NSFW is on. */
@Composable
internal fun RandomNsfwCommunityButton(
    state: SubshellState,
    placement: RandomNsfwCommunityPlacement,
    modifier: Modifier = Modifier,
) {
    val action = LocalRandomNsfwCommunityAction.current ?: return
    if (!state.offersRandomNsfwCommunity(placement)) return
    AssistChip(
        modifier = modifier,
        onClick = action::start,
        enabled = !action.inProgress,
        label = { Text(if (action.inProgress) "Finding a community…" else "Random NSFW community") },
        leadingIcon = {
            Row {
                if (action.inProgress) {
                    InlineProgressIndicator()
                } else {
                    Icon(Symbols.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        },
    )
}
