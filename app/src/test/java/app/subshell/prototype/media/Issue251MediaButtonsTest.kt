package app.subshell.prototype.media

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ISSUE251_MEDIA_BUTTONS_CONTROL_THE_VIDEO_V1 (#251): media buttons reach the
 * video only through SubshellState's play/pause, only while a presented video
 * plays with sound, and the system never learns the post or its URL.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue251MediaButtonsTest {
    /** A player with one post video, as the shared ExoPlayer would hold it. */
    private class PostVideoPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        var requestedPlay = false
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(
                listOf(
                    MediaItemData.Builder("t3_post")
                        .setMediaItem(
                            MediaItem.Builder()
                                .setMediaId("t3_post")
                                .setUri("https://v.redd.it/secret/DASH_720.mp4")
                                .setMediaMetadata(MediaMetadata.Builder().setTitle("A hidden post title").build())
                                .build(),
                        )
                        .build(),
                ),
            )
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(requestedPlay, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            requestedPlay = playWhenReady
            return Futures.immediateVoidFuture()
        }
    }

    private val played = mutableListOf<String>()
    private val paused = mutableListOf<String>()

    private fun buttons(videoId: () -> String?) = MediaButtonVideoPlayer(
        player = PostVideoPlayer(),
        buttonVideoId = videoId,
        onPlay = { played += it },
        onPause = { paused += it },
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun playAndPauseGoThroughTheAppForThePresentedVideo() {
        val player = buttons { "t3_post" }
        idle()
        assertTrue(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))

        player.play()
        idle()
        player.pause()
        idle()

        assertEquals(listOf("t3_post"), played)
        assertEquals(listOf("t3_post"), paused)
        player.release()
    }

    @Test
    fun withoutAVideoWithSoundTheButtonsAreLeftToOtherApps() {
        val player = buttons { null }
        idle()
        assertFalse(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))

        player.play()
        idle()

        assertTrue(played.isEmpty())
        player.release()
    }

    @Test
    fun theSystemSeesANeutralItemWithoutThePostOrItsUrl() {
        val player = buttons { "t3_post" }
        idle()
        val item = player.currentMediaItem!!

        assertEquals(MediaButtonVideoPlayer.NEUTRAL_ITEM_ID, item.mediaId)
        assertNull("no URL", item.localConfiguration)
        assertEquals("subshell video", player.mediaMetadata.title.toString())
        player.release()
    }

    @Test
    fun aControllerOfTheSessionReachesTheAppAndSeesOnlyTheNeutralItem() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val player = buttons { "t3_post" }
        val session = videoMediaSession(context, player)
        val future = androidx.media3.session.MediaController.Builder(context, session.token).buildAsync()
        idle()
        val controller = future.get()

        assertEquals(MediaButtonVideoPlayer.NEUTRAL_ITEM_ID, controller.currentMediaItem?.mediaId)
        controller.play()
        idle()

        assertEquals(listOf("t3_post"), played)
        controller.release()
        session.release()
        player.release()
    }

    // VIDEOSESSIONID01_EACH_SESSION_HAS_ITS_OWN_ID_V1: a second MainActivity
    // makes its session while the first one's is still open.
    @Test
    fun aSecondSessionWhileTheFirstIsOpenDoesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val firstPlayer = buttons { "t3_post" }
        val secondPlayer = buttons { "t3_post" }
        val first = videoMediaSession(context, firstPlayer)
        val second = videoMediaSession(context, secondPlayer)

        assertNotEquals(first.id, second.id)
        second.release()
        first.release()
        secondPlayer.release()
        firstPlayer.release()
    }
}
