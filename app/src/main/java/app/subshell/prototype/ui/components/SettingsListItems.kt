package app.subshell.prototype.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.ScreenMargin
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.isTraversalGroup
import app.subshell.prototype.ui.symbols.Symbols

// M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1 (#225, settings-03): every settings row is
// an M3 ListItem (headline, supporting text, leading icon, trailing control) on
// the screen's own colour, grouped by section headers, instead of a card per row.

/**
 * Settings lists keep the screen margin for headers, cards and previews; a list
 * row spans the margin so its own 16 dp inset lines its text up with the
 * headers and its ripple reaches the edges, as an M3 list does.
 */
internal fun Modifier.settingsRowBleed(margin: Dp = ScreenMargin): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val bleed = margin.roundToPx()
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = constraints.minWidth + bleed * 2,
                maxWidth = constraints.maxWidth + bleed * 2,
            ),
        )
        val width = (placeable.width - bleed * 2).coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, placeable.height) { placeable.place(-bleed, 0) }
    }

@Composable
internal fun SettingsListItem(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = icon?.let { vector -> { Icon(vector, contentDescription = null) } },
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    containerColor: Color = Color.Transparent,
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        content = { Text(title) },
        supportingContent = description?.takeIf { it.isNotEmpty() }?.let { text -> { Text(text) } },
        leadingContent = leading,
        trailingContent = trailing,
        modifier = modifier,
        // M3-15: the material3 1.5 ListItem draws its own disabled state; only
        // the destructive colour is ours.
        enabled = enabled,
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            contentColor = if (destructive) colors.error else Color.Unspecified,
            leadingContentColor = if (destructive) colors.error else Color.Unspecified,
        ),
    )
}

/** LOWUI7_SWITCH_ROWS_ARE_LABELLED_V1: the whole row is the switch. */
@Composable
internal fun SettingsSwitchItem(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingsListItem(
        title = title,
        description = description,
        icon = icon,
        enabled = enabled,
        modifier = modifier
            .settingsRowBleed()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        trailing = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
    )
}

/** A choice kept in a dialog: the row shows the current value. */
@Composable
internal fun SettingsValueItem(
    title: String,
    description: String?,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    SettingsListItem(
        title = title,
        description = description,
        icon = icon,
        modifier = modifier
            .settingsRowBleed()
            .clickable(onClick = onClick),
        trailing = {
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** Opens another settings page. */
@Composable
internal fun SettingsNavigationItem(
    title: String,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = icon?.let { vector -> { Icon(vector, contentDescription = null) } },
    destructive: Boolean = false,
) {
    SettingsListItem(
        title = title,
        description = description,
        leading = leading,
        destructive = destructive,
        modifier = modifier
            .settingsRowBleed()
            .clickable(onClick = onClick),
        trailing = {
            Icon(Symbols.ArrowForward, contentDescription = null)
        },
    )
}

// M3225_SHORT_SINGLE_CHOICE_IS_A_SEGMENTED_ROW_V1 (#225, settings-02, owner): two to
// four short, mutually exclusive options are an M3 segmented button row (one
// radio group for TalkBack), not filter chips, which read as checkboxes.
@Composable
internal fun <T> SettingsSegmentedChoice(
    title: String,
    description: String?,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    // One traversal group: the title, then its choices.
    Column(modifier.fillMaxWidth().semantics { isTraversalGroup = true }) {
        SettingsListItem(
            title = title,
            description = description,
            icon = icon,
            modifier = Modifier.settingsRowBleed(),
        )
        SegmentedChoiceRow(
            options = options,
            selected = selected,
            label = label,
            onSelect = onSelect,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
internal fun <T> SegmentedChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Four short segments on a phone leave no room for the check mark.
    showSelectedIcon: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.weight(1f),
                icon = {
                    if (showSelectedIcon) SegmentedButtonDefaults.Icon(active = option == selected)
                },
                // Long words shrink on one line instead of breaking (#220).
                label = { OneLineAutoSizeLabel(text = label(option)) },
            )
        }
    }
}
