package app.subshell.prototype.media

import android.content.Context
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import app.subshell.prototype.ui.SubshellState
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * ISSUE251_MEDIA_BUTTONS_CONTROL_THE_VIDEO_V1 (#251, owner "do it"): headset,
 * Bluetooth and system media buttons play and pause the video, while the app
 * is open (no background service, so no lock-screen controls).
 *
 * The session sees the shared player through [MediaButtonVideoPlayer]:
 * - play/pause go through SubshellState, as the in-app button and the PiP
 *   action do, so the selection and the player agree and a video no surface
 *   presents is never started;
 * - the buttons are offered only while a presented video plays with sound and
 *   the app is not locked; otherwise other apps keep them;
 * - the system learns nothing about the post: one neutral item, no URL, no
 *   title (car displays and watches show it; NSFW, hidden communities, app lock).
 */
@AndroidxOptIn(UnstableApi::class)
internal class MediaButtonVideoPlayer(
    player: Player,
    private val buttonVideoId: () -> String?,
    private val onPlay: (String) -> Unit,
    private val onPause: (String) -> Unit,
) : ForwardingSimpleBasePlayer(player) {
    override fun getState(): State {
        val state = super.getState()
        val offered = buttonVideoId() != null && !state.playlist.isEmpty()
        val commands = if (offered) {
            Player.Commands.Builder()
                .addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_METADATA)
                .build()
        } else {
            Player.Commands.EMPTY
        }
        val builder = state.buildUpon().setAvailableCommands(commands)
        if (state.playlist.isEmpty()) return builder.build()
        val current = state.playlist[state.currentMediaItemIndex.coerceIn(0, state.playlist.lastIndex)]
        return builder
            .setPlaylist(
                listOf(
                    current.buildUpon()
                        .setUid(NEUTRAL_ITEM_ID)
                        .setMediaItem(NEUTRAL_ITEM)
                        .setMediaMetadata(NEUTRAL_ITEM.mediaMetadata)
                        .setManifest(null)
                        .build(),
                ),
            )
            .setCurrentMediaItemIndex(0)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val mediaId = buttonVideoId() ?: return Futures.immediateVoidFuture()
        if (playWhenReady) onPlay(mediaId) else onPause(mediaId)
        return Futures.immediateVoidFuture()
    }

    /** Lets the session re-read [getState] after a change the player does not report. */
    fun refresh() = invalidateState()

    companion object {
        const val NEUTRAL_ITEM_ID = "subshell-video"
        val NEUTRAL_ITEM: MediaItem = MediaItem.Builder()
            .setMediaId(NEUTRAL_ITEM_ID)
            .setMediaMetadata(MediaMetadata.Builder().setTitle("subshell video").build())
            .build()
    }
}

// VIDEOSESSIONID01_EACH_SESSION_HAS_ITS_OWN_ID_V1 (owner 2026-10-27): Media3
// refuses a second session with an ID still in use, and a new MainActivity
// makes its session before the old activity has released its own (a link or
// notification opening the app while another screen of it is on top), so one
// fixed ID crashed the new activity.
private val videoMediaSessionNumber = AtomicInteger()

@AndroidxOptIn(UnstableApi::class)
internal fun videoMediaSession(context: Context, player: MediaButtonVideoPlayer): MediaSession =
    MediaSession.Builder(context, player)
        .setId("subshell-video-${videoMediaSessionNumber.incrementAndGet()}")
        .build()

@Composable
internal fun VideoMediaSessionEffect(
    state: SubshellState,
    coordinator: VideoPlaybackCoordinator,
    appLocked: Boolean,
) {
    val context = LocalContext.current
    val locked by rememberUpdatedState(appLocked)
    val buttonsPlayer = remember(coordinator, state) {
        MediaButtonVideoPlayer(
            player = coordinator.player,
            buttonVideoId = { coordinator.mediaButtonVideoId().takeIf { !locked } },
            onPlay = state::playVideo,
            onPause = state::stopVideo,
        )
    }
    DisposableEffect(context, buttonsPlayer) {
        val session = videoMediaSession(context, buttonsPlayer)
        onDispose { session.release() }
    }
    // A lock, or another video taking the surface, changes what the buttons
    // may do without a player event.
    LaunchedEffect(buttonsPlayer, appLocked, state.activeAnimatedMediaId, coordinator.renderedMediaId) {
        buttonsPlayer.refresh()
    }
}
