package app.subshell.prototype.ui.components

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.CommunityMute
import app.subshell.prototype.ui.CommunityMutePreset
import java.util.Calendar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import java.util.TimeZone
import android.content.Context

internal fun communityMuteStatusText(
    context: Context,
    mute: CommunityMute,
    nowEpochMillis: Long,
): String {
    val expiry = mute.expiresAtEpochMillis ?: return "Muted indefinitely"
    if (expiry <= nowEpochMillis) return "Mute expired"
    val formatted = DateUtils.formatDateTime(
        context,
        expiry,
        DateUtils.FORMAT_SHOW_WEEKDAY or
            DateUtils.FORMAT_SHOW_DATE or
            DateUtils.FORMAT_SHOW_TIME or
            DateUtils.FORMAT_ABBREV_MONTH,
    )
    return "Muted until $formatted"
}

internal fun communityMuteRemainingText(
    mute: CommunityMute,
    nowEpochMillis: Long,
): String {
    val expiry = mute.expiresAtEpochMillis ?: return "Until you unmute it"
    val remainingMillis = (expiry - nowEpochMillis).coerceAtLeast(0L)
    val totalMinutes = ((remainingMillis + 59_999L) / 60_000L).coerceAtLeast(1L)
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes % (24L * 60L)) / 60L
    val minutes = totalMinutes % 60L
    return when {
        days > 0L && hours > 0L -> "${days}d ${hours}h remaining"
        days > 0L -> "${days}d remaining"
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m remaining"
        hours > 0L -> "${hours}h remaining"
        else -> "${minutes}m remaining"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityMuteSheet(
    communityLabel: String,
    nowEpochMillis: Long,
    onDismiss: () -> Unit,
    onMute: (Long?) -> Unit,
    currentMute: CommunityMute? = null,
    onUnmute: (() -> Unit)? = null,
    // LOWUI8_MUTE_EXPIRY_COUNTS_FROM_THE_TAP_V1 (OCB-112/129): nowEpochMillis
    // is read when the caller composed the sheet, which may have sat open for
    // a while; a chosen duration counts from the tap.
    clock: () -> Long = System::currentTimeMillis,
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    // M3225_MUTE_UNTIL_USES_M3_PICKERS_V1 (#225, components-04, owner): Until…
    // replaces the sheet with the M3 date and time pickers (app theme, dynamic
    // colour, pure black), not Android's View pickers.
    var untilPickerFromMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    untilPickerFromMillis?.let { from ->
        CommunityMuteUntilPicker(
            nowEpochMillis = from,
            clock = clock,
            onSelected = { until ->
                onMute(until)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        return
    }

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
                text = if (currentMute == null) {
                    "Mute $communityLabel"
                } else {
                    "Mute settings"
                },
                style = MaterialTheme.typography.headlineSmall,
            )

            if (currentMute != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = communityLabel,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = communityMuteStatusText(
                                    context = context,
                                    mute = currentMute,
                                    nowEpochMillis = nowEpochMillis,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Text(
                text = "Hide posts from Home, Popular and All. Direct visits and saved posts still work.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
            )

            if (currentMute != null) {
                Text(
                    text = "Change duration",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            CommunityMutePreset.entries
                .filterNot { preset -> preset == CommunityMutePreset.INDEFINITE }
                .forEach { preset ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        content = { Text(preset.label) },
                        supportingContent = {
                            Text("Automatically unmute after ${preset.label.lowercase()}")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onMute(preset.expiryFrom(clock()))
                                onDismiss()
                            },
                    )
                }

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = { Text("Until…") },
                supportingContent = { Text("Choose an exact date and time") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { untilPickerFromMillis = clock() },
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = { Text(CommunityMutePreset.INDEFINITE.label) },
                supportingContent = { Text("Keep muted until you unmute it") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onMute(null)
                        onDismiss()
                    },
            )

            if (currentMute != null && onUnmute != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                        )
                    },
                    content = { Text("Unmute community") },
                    supportingContent = {
                        Text("Show posts from $communityLabel in mixed feeds again")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUnmute()
                            onDismiss()
                        },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityMuteUntilPicker(
    nowEpochMillis: Long,
    clock: () -> Long,
    onSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // The M3 date picker speaks in UTC midnights; the chosen day is kept as one.
    var chosenDayUtc by rememberSaveable { mutableStateOf<Long?>(null) }
    val day = chosenDayUtc
    if (day == null) {
        val todayUtc = localDayAsUtcMidnight(nowEpochMillis)
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = todayUtc + DAY_MILLIS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = { chosenDayUtc = dateState.selectedDateMillis },
                ) {
                    Text("Next")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState)
        }
        return
    }
    val utcDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day }
    val year = utcDay.get(Calendar.YEAR)
    val month = utcDay.get(Calendar.MONTH)
    val dayOfMonth = utcDay.get(Calendar.DAY_OF_MONTH)
    // LOWUX2_MUTE_UNTIL_TIME_STARTS_IN_THE_FUTURE_V1 (OCB-130): today starts an
    // hour ahead, and a past time keeps the time picker open with the reason
    // instead of ending the flow.
    val start = remember(day) {
        communityMuteUntilTimeStart(
            nowEpochMillis = clock(),
            year = year,
            month = month,
            dayOfMonth = dayOfMonth,
        )
    }
    val timeState = rememberTimePickerState(
        initialHour = start.get(Calendar.HOUR_OF_DAY),
        initialMinute = start.get(Calendar.MINUTE),
        is24Hour = DateFormat.is24HourFormat(context),
    )
    var refusedPastTime by remember { mutableStateOf(false) }
    LaunchedEffect(timeState.hour, timeState.minute) { refusedPastTime = false }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mute until") },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth, timeState.hour, timeState.minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (selected > clock()) onSelected(selected) else refusedPastTime = true
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = { chosenDayUtc = null }) { Text("Back") }
        },
    ) {
        TimePicker(state = timeState)
        if (refusedPastTime) {
            Text(
                "Choose a time in the future",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L

/** The local calendar day of [epochMillis] as the UTC midnight the M3 date picker uses. */
internal fun localDayAsUtcMidnight(epochMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Where the Until… time picker starts: an hour from now on today, else this time of day. */
internal fun communityMuteUntilTimeStart(nowEpochMillis: Long, year: Int, month: Int, dayOfMonth: Int): Calendar {
    val now = Calendar.getInstance().apply { timeInMillis = nowEpochMillis }
    val today = now.get(Calendar.YEAR) == year &&
        now.get(Calendar.MONTH) == month &&
        now.get(Calendar.DAY_OF_MONTH) == dayOfMonth
    return Calendar.getInstance().apply {
        timeInMillis = if (today) nowEpochMillis + 60L * 60L * 1_000L else nowEpochMillis
    }
}
