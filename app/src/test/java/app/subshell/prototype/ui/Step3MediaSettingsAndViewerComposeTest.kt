package app.subshell.prototype.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import android.net.Uri
import android.view.View
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LEGACY_VIDEO_DOWNLOAD_DIRECTORY
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoMediaDataSource
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.media.videoMediaCacheSummary
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.screens.MediaViewerScreen
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.SettingsScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (media-audio-r27 NAV-02 and MEDIA04C/MEDIA05): the full-screen viewer
 * lets the system bars come back only by a swipe and gives the window back as
 * it was; Settings shows the media cache and clears it only after asking; the
 * downloads an older build left can be deleted after asking, and the playback
 * cache is a separate thing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3MediaSettingsAndViewerComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val video: Post = SampleData.posts.first().copy(
        id = "t3_step3_viewer",
        kind = PostKind.VIDEO,
        localMediaUri = "content://media/picked/step3_viewer",
        localMediaMimeType = "video/mp4",
        remoteVideoUri = null,
        remoteVideoHlsUri = null,
        remoteVideoFallbackUri = null,
        mediaAsset = null,
    )

    @Test
    fun theFullScreenViewerShowsBarsOnlyBySwipeAndGivesTheWindowBack() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState()
        state.openMedia(video)
        val coordinator = testVideoPlaybackCoordinator(context).apply { release() }
        lateinit var activity: ComponentActivity
        lateinit var view: View
        var shown by mutableStateOf(true)
        var before: Int? = null
        composeRule.setContent {
            activity = LocalContext.current as ComponentActivity
            view = LocalView.current
            if (before == null) {
                before = WindowCompat.getInsetsController(activity.window, view).systemBarsBehavior
            }
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                if (shown) MediaViewerScreen(post = video, state = state, onBack = {})
            }
        }
        composeRule.waitForIdle()
        val controller = WindowCompat.getInsetsController(activity.window, view)
        assertEquals(
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
            controller.systemBarsBehavior,
        )

        composeRule.runOnIdle { shown = false }
        composeRule.waitForIdle()

        assertTrue(before != WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
        assertEquals("the window's own behaviour comes back", before, controller.systemBarsBehavior)
    }

    @Test
    fun theMediaCacheIsClearedOnlyAfterAsking() {
        ensureWorkManagerInitialized(context)
        val server = cacheSomeMedia()
        try {
            showMediaSettings()
            scrollTo("Media cache")
            val cached = VideoMediaDataSource.cacheStatus(context)
            assertTrue("the clip is in the media cache", cached.bytesUsed > 0L)
            // The row shows what the cache holds.
            composeRule.waitUntil(10_000) { exists(videoMediaCacheSummary(cached)) }
            assertTrue(VideoMediaDataSource.cacheStatus(context).bytesUsed > 0L)

            clickFirst(hasText("Clear") and hasClickAction())
            // The question names what is and is not touched.
            assertTrue(exists("Clear media cache?"))
            assertTrue(
                exists("Cached media will be removed. Settings and saved setups are not affected.", substring = true),
            )
            assertTrue("nothing removed before the answer", VideoMediaDataSource.cacheStatus(context).bytesUsed > 0L)

            // Cancel keeps the cache.
            clickFirst(hasText("Cancel") and hasClickAction())
            assertFalse(exists("Clear media cache?"))
            assertTrue(VideoMediaDataSource.cacheStatus(context).bytesUsed > 0L)

            clickFirst(hasText("Clear") and hasClickAction())
            // The dialog's own Clear is the last one drawn.
            val clears = composeRule.onAllNodes(hasText("Clear") and hasClickAction())
            clears[clears.fetchSemanticsNodes().size - 1].performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitUntil(10_000) { exists("Cache cleared", substring = true) }

            assertEquals(0L, VideoMediaDataSource.cacheStatus(context).bytesUsed)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun downloadsAnOlderBuildLeftAreDeletedOnlyAfterAsking() {
        ensureWorkManagerInitialized(context)
        val folder = File(context.noBackupFilesDir, LEGACY_VIDEO_DOWNLOAD_DIRECTORY).apply { mkdirs() }
        File(folder, "old.media").writeBytes(ByteArray(2_048))

        showMediaSettings()
        composeRule.waitUntil(10_000) { scrollToOrNull("Downloaded videos") }

        clickFirst(hasText("Delete all") and hasClickAction())
        assertTrue(exists("Delete all downloaded videos?"))
        assertTrue(exists("The playback cache is separate and will not be cleared.", substring = true))
        assertTrue("nothing deleted before the answer", folder.exists())

        val deletes = composeRule.onAllNodes(hasText("Delete all") and hasClickAction())
        deletes[deletes.fetchSemanticsNodes().size - 1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(10_000) { exists("Deleted 1 videos", substring = true) }

        assertFalse(folder.exists())
    }

    private fun showMediaSettings() {
        val state = SubshellState(initialInteractionSideConfigured = true)
        composeRule.setContent {
            SubshellTheme {
                SettingsScreen(
                    state = state,
                    backupRepository = SettingsBackupRepository(context, "test"),
                    onBack = {},
                    initialDestinationName = SettingsDestination.MEDIA.name,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
        composeRule.waitForIdle()
    }

    private fun scrollToOrNull(text: String): Boolean =
        runCatching { scrollTo(text) }.isSuccess && exists(text)

    private fun exists(text: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun clickFirst(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /**
     * Reads one small clip through the app's own cached data source from a
     * local server, so the real media cache holds something to clear.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun cacheSomeMedia(): HttpServer {
        val body = ByteArray(64 * 1024) { it.toByte() }
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/clip.mp4") { exchange ->
            exchange.responseHeaders.add("Content-Type", "video/mp4")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val source = VideoMediaDataSource.factory(context).createDataSource()
        val uri = Uri.parse("http://127.0.0.1:${server.address.port}/clip.mp4")
        source.open(DataSpec(uri))
        val buffer = ByteArray(8 * 1024)
        while (source.read(buffer, 0, buffer.size) != -1) {
            // read the whole clip into the cache
        }
        source.close()
        return server
    }
}
