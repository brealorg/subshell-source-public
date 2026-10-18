package app.subshell.prototype.ui.screens

import app.subshell.prototype.ui.components.settingsRowBleed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.CommentSwipeAction
import app.subshell.prototype.ui.PostSwipeAction
import app.subshell.prototype.ui.symbols.Symbols

// ISSUE16_INLINE_EXPANDABLE_SWIPE_SETTINGS_V1
// ISSUE16_M3_RADIO_SINGLE_CHOICE_SETTINGS_V1
internal enum class SwipeActionSettingsTarget {
    POSTS,
    COMMENTS,
}

private enum class SwipeActionChoiceTarget {
    POST_LEFT,
    POST_RIGHT,
    COMMENT_LEFT,
    COMMENT_RIGHT,
}

@Composable
internal fun SwipeActionsSettingsSummary(
    enabled: Boolean,
    expandedTarget: SwipeActionSettingsTarget?,
    postLeftAction: PostSwipeAction,
    postRightAction: PostSwipeAction,
    commentLeftAction: CommentSwipeAction,
    commentRightAction: CommentSwipeAction,
    onEnabledChange: (Boolean) -> Unit,
    onExpandedTargetChange: (SwipeActionSettingsTarget?) -> Unit,
    onPostLeftActionChange: (PostSwipeAction) -> Unit,
    onPostRightActionChange: (PostSwipeAction) -> Unit,
    onCommentLeftActionChange: (CommentSwipeAction) -> Unit,
    onCommentRightActionChange: (CommentSwipeAction) -> Unit,
) {
    var choiceTargetName by rememberSaveable { mutableStateOf<String?>(null) }
    val choiceTarget = choiceTargetName?.let { stored ->
        SwipeActionChoiceTarget.entries.firstOrNull { it.name == stored }
    }

    // M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1: a list row like the other settings, not a card.
    Box(Modifier.fillMaxWidth().settingsRowBleed()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            // ISSUE219_SWIPE_MASTER_ROW_IS_THE_SWITCH_V1: as ToggleRow (LOWUI7).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                    .defaultMinSize(minHeight = 46.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Symbols.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Swipe actions",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (enabled) {
                            "Horizontal shortcuts · side menu opens from the menu button"
                        } else {
                            "Horizontal shortcuts for posts and comments"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = null,
                )
            }

            HorizontalDivider()

            SwipeSummaryRow(
                label = "Posts",
                value = "← ${postLeftAction.label} · ${postRightAction.label} →",
                expanded = expandedTarget == SwipeActionSettingsTarget.POSTS,
                onClick = {
                    onExpandedTargetChange(
                        if (expandedTarget == SwipeActionSettingsTarget.POSTS) {
                            null
                        } else {
                            SwipeActionSettingsTarget.POSTS
                        },
                    )
                },
            )

            if (expandedTarget == SwipeActionSettingsTarget.POSTS) {
                InlineSwipeActionRows(
                    leftValue = postLeftAction.label,
                    rightValue = postRightAction.label,
                    onLeftClick = {
                        choiceTargetName = SwipeActionChoiceTarget.POST_LEFT.name
                    },
                    onRightClick = {
                        choiceTargetName = SwipeActionChoiceTarget.POST_RIGHT.name
                    },
                )
            }

            HorizontalDivider()

            SwipeSummaryRow(
                label = "Comments",
                value = "← ${commentLeftAction.label} · ${commentRightAction.label} →",
                expanded = expandedTarget == SwipeActionSettingsTarget.COMMENTS,
                onClick = {
                    onExpandedTargetChange(
                        if (expandedTarget == SwipeActionSettingsTarget.COMMENTS) {
                            null
                        } else {
                            SwipeActionSettingsTarget.COMMENTS
                        },
                    )
                },
            )

            if (expandedTarget == SwipeActionSettingsTarget.COMMENTS) {
                InlineSwipeActionRows(
                    leftValue = commentLeftAction.label,
                    rightValue = commentRightAction.label,
                    onLeftClick = {
                        choiceTargetName = SwipeActionChoiceTarget.COMMENT_LEFT.name
                    },
                    onRightClick = {
                        choiceTargetName = SwipeActionChoiceTarget.COMMENT_RIGHT.name
                    },
                )
            }
        }
    }

    when (choiceTarget) {
        SwipeActionChoiceTarget.POST_LEFT -> SwipeActionRadioDialog(
            title = "Post · swipe left",
            options = PostSwipeAction.entries,
            selected = postLeftAction,
            label = PostSwipeAction::label,
            onSelect = { action ->
                onPostLeftActionChange(action)
                choiceTargetName = null
            },
            onDismiss = { choiceTargetName = null },
        )

        SwipeActionChoiceTarget.POST_RIGHT -> SwipeActionRadioDialog(
            title = "Post · swipe right",
            options = PostSwipeAction.entries,
            selected = postRightAction,
            label = PostSwipeAction::label,
            onSelect = { action ->
                onPostRightActionChange(action)
                choiceTargetName = null
            },
            onDismiss = { choiceTargetName = null },
        )

        SwipeActionChoiceTarget.COMMENT_LEFT -> SwipeActionRadioDialog(
            title = "Comment · swipe left",
            options = CommentSwipeAction.entries,
            selected = commentLeftAction,
            label = CommentSwipeAction::label,
            onSelect = { action ->
                onCommentLeftActionChange(action)
                choiceTargetName = null
            },
            onDismiss = { choiceTargetName = null },
        )

        SwipeActionChoiceTarget.COMMENT_RIGHT -> SwipeActionRadioDialog(
            title = "Comment · swipe right",
            options = CommentSwipeAction.entries,
            selected = commentRightAction,
            label = CommentSwipeAction::label,
            onSelect = { action ->
                onCommentRightActionChange(action)
                choiceTargetName = null
            },
            onDismiss = { choiceTargetName = null },
        )

        null -> Unit
    }
}

@Composable
private fun SwipeSummaryRow(
    label: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // ISSUE219_EXPANDABLE_ROWS_SAY_THEIR_STATE_V1: the state is spoken, not
            // inferred from the icon's label.
            .clickable(
                onClickLabel = if (expanded) "Collapse" else "Expand",
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .defaultMinSize(minHeight = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = if (expanded) {
                Symbols.ExpandLess
            } else {
                Symbols.ExpandMore
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InlineSwipeActionRows(
    leftValue: String,
    rightValue: String,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 18.dp),
    ) {
        HorizontalDivider()
        SwipeActionValueRow(
            title = "Swipe left",
            value = leftValue,
            onClick = onLeftClick,
        )
        HorizontalDivider()
        SwipeActionValueRow(
            title = "Swipe right",
            value = rightValue,
            onClick = onRightClick,
        )
    }
}

@Composable
private fun SwipeActionValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Symbols.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun <T> SwipeActionRadioDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
