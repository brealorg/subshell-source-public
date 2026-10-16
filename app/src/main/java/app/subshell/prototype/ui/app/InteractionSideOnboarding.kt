package app.subshell.prototype.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.CommentActionPreference
import app.subshell.prototype.ui.InteractionSide
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.resolve

@Composable
internal fun InteractionSideOnboarding(state: SubshellState) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Rounded.TouchApp, contentDescription = null) },
        title = { Text("Which side should subshell favor?") },
        text = {
            // LOWUI7_CHOICE_ROWS_SAY_WHICH_IS_SELECTED_V1 (OCB-114)
            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "This sets the default side for the menu, comment actions, " +
                        "floating actions and media controls, plus composer actions. " +
                        "Menu and comment placement can be overridden later.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InteractionSide.entries.forEach { side ->
                    val selected = state.interactionSide == side
                    Surface(
                        selected = selected,
                        onClick = { state.previewInteractionSide(side) },
                        modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(side.label, style = MaterialTheme.typography.titleMedium)
                            InteractionSidePreview(side)
                            CommentActionPreview(
                                CommentActionPreference.FOLLOW.resolve(side),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // M3223_DIALOG_ACTIONS_ARE_TEXT_BUTTONS_V1 (#223): Material dialog action.
            TextButton(onClick = state::confirmInteractionSide) {
                Text("Use ${state.interactionSide.label}")
            }
        },
    )
}

@Composable
private fun InteractionSidePreview(side: InteractionSide) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val drawer: @Composable () -> Unit = {
            Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        val account: @Composable () -> Unit = {
            Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        when (side) {
            InteractionSide.LEFT -> {
                drawer()
                Spacer(Modifier.weight(1f))
                account()
            }
            InteractionSide.RIGHT -> {
                account()
                Spacer(Modifier.weight(1f))
                drawer()
            }
        }
    }
}

@Composable
private fun CommentActionPreview(placement: CommentActionPlacement) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val mainActions: @Composable () -> Unit = {
            Icon(Icons.Rounded.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.ThumbDown, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        val more: @Composable () -> Unit = {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
        }

        when (placement) {
            CommentActionPlacement.LEFT -> {
                mainActions()
                more()
                Spacer(Modifier.weight(1f))
            }
            CommentActionPlacement.BALANCED -> {
                mainActions()
                Spacer(Modifier.weight(1f))
                more()
            }
            CommentActionPlacement.RIGHT -> {
                Spacer(Modifier.weight(1f))
                mainActions()
                more()
            }
        }
    }
}
