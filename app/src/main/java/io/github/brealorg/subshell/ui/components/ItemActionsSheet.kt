package io.github.brealorg.subshell.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One row of an [ItemActionsSheet]. */
internal data class SheetAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * ISSUE295_ONE_MODERATE_ENTRY_OPENS_A_SHEET_V1: a group of actions on one post
 * or comment, opened from a single ⋮ entry so the menu stays short (Material:
 * a long list of actions on one object is a bottom sheet). Groups are split
 * by a divider; a row closes the sheet before it acts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemActionsSheet(
    title: String,
    groups: List<List<SheetAction>>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        groups.filter { it.isNotEmpty() }.forEachIndexed { index, group ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            group.forEach { action ->
                val color = if (action.destructive) MaterialTheme.colorScheme.error else Color.Unspecified
                ListItem(
                    content = { Text(action.label, color = color) },
                    leadingContent = {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            tint = if (action.destructive) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(role = Role.Button) {
                        onDismiss()
                        action.onClick()
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
