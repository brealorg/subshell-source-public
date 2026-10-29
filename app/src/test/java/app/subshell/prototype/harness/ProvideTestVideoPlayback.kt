package app.subshell.prototype.harness

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator

/**
 * A feed screen rendered on its own needs the video coordinator that the app
 * (SubshellApp) always provides; every post feed sets up its videos
 * (ISSUE275_CUSTOM_FEED_VIDEOS_PLAY_IN_THE_FEED_V1).
 */
@Composable
internal fun ProvideTestVideoPlayback(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val coordinator = remember(context) { testVideoPlaybackCoordinator(context) }
    CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator, content = content)
}
