package io.github.brealorg.subshell.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.BuildConfig
import io.github.brealorg.subshell.about.SUBSHELL_LICENCE_NOTICE
import io.github.brealorg.subshell.about.SUBSHELL_SOURCE_URL
import io.github.brealorg.subshell.ui.ScreenMargin
import io.github.brealorg.subshell.ui.components.RouteTopBar
import io.github.brealorg.subshell.ui.components.rememberRouteTopBarScrollBehavior
import io.github.brealorg.subshell.ui.symbols.Symbols
import io.github.brealorg.subshell.update.rememberBundledChangelog
import androidx.compose.material3.TextButton
import io.github.brealorg.subshell.update.updateReleaseViewIntent
import io.github.brealorg.subshell.update.UpdateStateStore
import io.github.brealorg.subshell.update.UpdateRuntimePolicy
import io.github.brealorg.subshell.update.UPDATE04_GITHUB_RELEASES_URL
import android.content.ActivityNotFoundException
import io.github.brealorg.subshell.update.UpdateChangelogReleaseItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var cursorRevealed by remember { mutableStateOf(false) }
    var terminalVisible by remember { mutableStateOf(false) }
    var licencesVisible by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val appContext = LocalContext.current.applicationContext
    val activityContext = LocalContext.current
    val changelog = rememberBundledChangelog(appContext)
    // UPDATE04_UPDATE_CHECK_HEALTH_V1: say so when update checks have read
    // no usable manifest for three days (release builds only).
    val updateCheckFailing = remember(appContext) {
        UpdateRuntimePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG) &&
            UpdateStateStore(appContext).updateCheckFailing(System.currentTimeMillis())
    }

    if (terminalVisible) {
        EasterEggTerminalScreen(
            modifier = modifier,
            onBack = { terminalVisible = false },
        )
        return
    }

    if (licencesVisible) {
        OpenSourceLicencesDialog(onDismissRequest = { licencesVisible = false })
    }

    val density = LocalDensity.current
    val unlockPullPx = with(density) { EASTER_EGG_UNLOCK_PULL.toPx() }
    val unlockStartZonePx = with(density) { EASTER_EGG_UNLOCK_START_ZONE.toPx() }

    LaunchedEffect(cursorRevealed) {
        if (cursorRevealed) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = { Text("Help & about") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, contentDescription = "Back") }
            },
            scrollBehavior = routeTopBarScroll,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .easterEggOverscrollHold(
                    startZonePx = unlockStartZonePx,
                    pullThresholdPx = unlockPullPx,
                    enabled = !cursorRevealed,
                    onUnlocked = { cursorRevealed = true },
                ),
        ) {
            // UPDATE01A1B_HELP_ABOUT_INLINE_CHANGELOG_V3
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenMargin,
                    top = 20.dp,
                    end = ScreenMargin,
                    bottom = 48.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item("app-summary") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "subshell",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Independent Reddit client for Android. Use Settings to configure " +
                                "appearance, navigation, media, Reddit connection, notifications " +
                                "and backups.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                // ISSUE47_HELP_SAYS_WHAT_REDDITS_2027_CHANGE_MEANS_V1 (#47): the same plain
                // note as the FAQ, so a reader without the website knows too.
                item("reddit-api-2027") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Reddit's API change",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            REDDIT_API_2027_NOTE,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ISSUE39_LICENCES_AND_SOURCE_IN_THE_APP_V1 (#39)
                item("licence") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Licence", style = MaterialTheme.typography.titleSmall)
                        Text(
                            SUBSHELL_LICENCE_NOTICE,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row {
                            TextButton(
                                onClick = {
                                    try {
                                        activityContext.startActivity(updateReleaseViewIntent(SUBSHELL_SOURCE_URL))
                                    } catch (_: ActivityNotFoundException) {
                                        // Nothing can open a web page; the licence screen still works.
                                    }
                                },
                            ) { Text("Source code") }
                            TextButton(onClick = { licencesVisible = true }) { Text("Open-source licences") }
                        }
                    }
                }

                if (updateCheckFailing) {
                    item("update-check-failing") {
                        UpdateCheckFailingNotice(
                            onOpenReleases = {
                                try {
                                    // OCB-391: from the activity, so the app lock counts the trip.
                                    activityContext.startActivity(
                                        updateReleaseViewIntent(UPDATE04_GITHUB_RELEASES_URL),
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    // Nothing can open a web page; the text still says where to look.
                                }
                            },
                        )
                    }
                }

                item("changelog-heading") {
                    Text(
                        "Changelog",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                if (changelog.releases.isEmpty()) {
                    item("changelog-empty") {
                        Text(
                            "No bundled changelog is available in this build.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(
                        items = changelog.releases,
                        key = { release -> release.versionCode },
                    ) { release ->
                        UpdateChangelogReleaseItem(
                            release = release,
                        )
                    }
                }
            }

            if (cursorRevealed) {
                EasterEggCursor(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp),
                    onClick = { terminalVisible = true },
                )
            }
        }
    }
}

@Composable
private fun EasterEggCursor(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "easter egg cursor")
    val alpha by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "easter egg cursor alpha",
    )
    Text(
        text = "_",
        modifier = modifier
            .alpha(alpha)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.primary,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.titleLarge,
    )
}

private fun Modifier.easterEggOverscrollHold(
    startZonePx: Float,
    pullThresholdPx: Float,
    enabled: Boolean,
    onUnlocked: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(startZonePx, pullThresholdPx) {
        coroutineScope {
            val unlockScope = this
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val startsInUnlockZone = easterEggPullStartsInUnlockZone(
                    downY = down.position.y,
                    viewportHeight = size.height.toFloat(),
                    startZonePx = startZonePx,
                )
                if (!startsInUnlockZone) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.none { it.pressed }) break
                    }
                    return@awaitEachGesture
                }

                var upwardPullPx = 0f
                var unlockJob: Job? = null
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        unlockJob?.cancel()
                        break
                    }

                    val deltaY = change.position.y - change.previousPosition.y
                    upwardPullPx = if (deltaY < 0f) {
                        upwardPullPx + -deltaY
                    } else {
                        (upwardPullPx - deltaY).coerceAtLeast(0f)
                    }

                    if (
                        easterEggPullIsArmed(upwardPullPx, pullThresholdPx) &&
                        unlockJob == null
                    ) {
                        unlockJob = unlockScope.launch {
                            delay(EASTER_EGG_UNLOCK_HOLD_MS)
                            onUnlocked()
                        }
                    } else if (upwardPullPx < pullThresholdPx * 0.65f) {
                        unlockJob?.cancel()
                        unlockJob = null
                    }
                }
            }
        }
    }
}

internal const val REDDIT_API_2027_NOTE =
    "Reddit plans to end API access for unregistered apps on 12 January 2027. " +
        "subshell may stop loading new content then; what is stored stays readable."

@Composable
private fun UpdateCheckFailingNotice(onOpenReleases: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Update check isn't working",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            "subshell hasn't been able to read its update information for a few days. " +
                "New releases are published on GitHub.",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onOpenReleases) {
            Text("Open GitHub releases")
        }
    }
}
