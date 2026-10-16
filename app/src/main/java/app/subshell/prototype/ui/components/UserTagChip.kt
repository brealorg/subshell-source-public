package app.subshell.prototype.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.UserTag
import app.subshell.prototype.ui.UserTagColor

// ISSUE34_USER_TAGS_V1: fixed tag palette, one container/content pair per
// theme brightness, so a tag reads the same in every theme.
internal data class UserTagChipColors(val container: Color, val content: Color)

internal fun userTagChipColors(color: UserTagColor, dark: Boolean): UserTagChipColors {
    val (light, darkPair) = when (color) {
        UserTagColor.GREY -> (0xFFE0E0E0 to 0xFF303030) to (0xFF484848 to 0xFFE6E6E6)
        UserTagColor.RED -> (0xFFFFDAD6 to 0xFF410002) to (0xFF93000A to 0xFFFFDAD6)
        UserTagColor.ORANGE -> (0xFFFFDBC8 to 0xFF331200) to (0xFF7A3000 to 0xFFFFDBC8)
        UserTagColor.YELLOW -> (0xFFFFF0B3 to 0xFF2E2600) to (0xFF5C4F00 to 0xFFFFF0B3)
        UserTagColor.GREEN -> (0xFFC8F0C0 to 0xFF002204) to (0xFF1F5122 to 0xFFC8F0C0)
        UserTagColor.TEAL -> (0xFFB9EFE8 to 0xFF00201D) to (0xFF00504A to 0xFFB9EFE8)
        UserTagColor.BLUE -> (0xFFD6E3FF to 0xFF001B3E) to (0xFF1E4785 to 0xFFD6E3FF)
        UserTagColor.PURPLE -> (0xFFEADDFF to 0xFF21005D) to (0xFF4F378B to 0xFFEADDFF)
    }
    val (container, content) = if (dark) darkPair else light
    return UserTagChipColors(Color(container), Color(content))
}

@Composable
internal fun userTagChipColors(color: UserTagColor): UserTagChipColors =
    userTagChipColors(color, dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f)

internal fun userTagChipText(tag: UserTag): String = tag.label.ifEmpty { "Note" }

/** The user's own tag next to a username: the label (or "Note" for a note-only tag). */
@Composable
internal fun UserTagChip(
    tag: UserTag,
    modifier: Modifier = Modifier,
) {
    val colors = userTagChipColors(tag.color)
    val text = userTagChipText(tag)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .semantics { contentDescription = "Your tag: $text" }
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.container)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Profile header: the tag and the full note (only you see them), with Add/Edit. */
@Composable
internal fun UserTagProfileSection(
    tag: UserTag?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tag != null) {
                UserTagChip(tag = tag)
            }
            TextButton(onClick = onEdit) {
                Text(if (tag == null) "Add private note" else "Edit private note")
            }
        }
        if (tag != null && tag.note.isNotEmpty()) {
            Text(
                text = tag.note,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * A username with the user's tag after it. The name keeps its width; the
 * chip takes what is left and is not drawn when that is too narrow.
 */
@Composable
internal fun NameWithUserTag(
    tag: UserTag?,
    modifier: Modifier = Modifier,
    name: @Composable () -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        name()
        if (tag != null) {
            Spacer(Modifier.width(6.dp))
            UserTagChip(
                tag = tag,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .hiddenWhenNarrowerThan(AuthorFlairMinVisibleWidth),
            )
        }
    }
}
