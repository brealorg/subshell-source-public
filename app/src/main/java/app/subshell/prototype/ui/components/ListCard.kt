package app.subshell.prototype.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow

// M3225_LIST_ROWS_ARE_LIST_ITEMS_V1 (#225, reading-04, modinbox-09,
// components-15): a tappable row in a card list is an M3 ListItem (headline,
// supporting text, leading avatar/icon, trailing control) in a rounded card
// whose click is clipped to the card and says it is a button, instead of a
// hand-laid Row with its own paddings and an unclipped ripple.
@Composable
internal fun ListCard(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape = MaterialTheme.shapes.medium,
    headlineMaxLines: Int = 1,
    supportingMaxLines: Int = 1,
) {
    // ListItem merges its own semantics, so the click sits on the ListItem
    // (inside the card, which clips its ripple) and carries the row's text.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = color,
    ) {
        ListItem(
            content = {
                Text(headline, maxLines = headlineMaxLines, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = supporting?.takeIf { it.isNotBlank() }?.let { text ->
                { Text(text, maxLines = supportingMaxLines, overflow = TextOverflow.Ellipsis) }
            },
            leadingContent = leading,
            trailingContent = trailing,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = if (onClick != null) {
                Modifier.clickable(
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = Role.Button,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )
    }
}
