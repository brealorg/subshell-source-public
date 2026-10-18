package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.CommunityMute
import app.subshell.prototype.ui.symbols.Symbols

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MutedCommunitiesSheet(
    mutes: List<CommunityMute>,
    nowEpochMillis: Long,
    communityLabel: (String) -> String,
    onDismiss: () -> Unit,
    onChange: (String) -> Unit,
    onUnmute: (String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Muted communities",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Posts stay hidden from Home, Popular and All. Direct visits still work.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )

            if (mutes.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Symbols.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "No muted communities",
                        modifier = Modifier.padding(start = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                ) {
                    items(
                        items = mutes,
                        key = { mute -> mute.communityName.lowercase() },
                    ) { mute ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Icon(
                                    imageVector = Symbols.VolumeOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            content = {
                                Text(communityLabel(mute.communityName))
                            },
                            supportingContent = {
                                Column {
                                    Text(
                                        communityMuteStatusText(
                                            context = context,
                                            mute = mute,
                                            nowEpochMillis = nowEpochMillis,
                                        ),
                                    )
                                    Text(
                                        communityMuteRemainingText(
                                            mute = mute,
                                            nowEpochMillis = nowEpochMillis,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { onChange(mute.communityName) },
                                    ) {
                                        Icon(
                                            imageVector = Symbols.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp),
                                        )
                                        Text(
                                            text = "Change",
                                            modifier = Modifier.padding(start = 4.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { onUnmute(mute.communityName) },
                                    ) {
                                        Icon(
                                            imageVector = Symbols.VolumeUp,
                                            contentDescription =
                                                "Unmute ${communityLabel(mute.communityName)}",
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}
