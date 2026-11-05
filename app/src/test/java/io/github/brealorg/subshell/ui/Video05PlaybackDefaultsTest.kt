package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.releaseWithoutHanging
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.PlaybackTrackSelectionMode
import io.github.brealorg.subshell.media.VIDEO_UNRANKED
import io.github.brealorg.subshell.media.VideoMedia
import io.github.brealorg.subshell.media.VideoMediaSource
import io.github.brealorg.subshell.media.VideoMediaSourceKind
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackDefaults
import io.github.brealorg.subshell.media.applyPlaybackDefaults
import io.github.brealorg.subshell.media.initialSelection
import io.github.brealorg.subshell.ui.screens.SettingsDestination
import io.github.brealorg.subshell.ui.screens.SettingsScreen
import io.github.brealorg.subshell.ui.theme.SubshellTheme
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
 * VIDEO05 (#227): VIDEO05_PLAYBACK_DEFAULTS_IN_SETTINGS_V1, VIDEO05_VIDEO_SETTINGS_GROUP_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Video05PlaybackDefaultsTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun parameters(defaults: VideoPlaybackDefaults): TrackSelectionParameters =
        TrackSelectionParameters.Builder()
            .setPreferredTextLanguage("fr")
            .setPreferredAudioLanguage("fr")
            .applyPlaybackDefaults(defaults)
            .build()

    @Test
    fun subtitleDefaultsSetWhatANewVideoStartsWith() {
        val off = parameters(VideoPlaybackDefaults(subtitles = VideoSubtitleDefault.OFF))
        assertTrue(C.TRACK_TYPE_TEXT in off.disabledTrackTypes)

        val marked = parameters(VideoPlaybackDefaults(subtitles = VideoSubtitleDefault.WHEN_MARKED))
        assertFalse(C.TRACK_TYPE_TEXT in marked.disabledTrackTypes)
        assertTrue(marked.preferredTextLanguages.isEmpty())

        val system = parameters(VideoPlaybackDefaults(subtitles = VideoSubtitleDefault.SYSTEM_CAPTIONS))
        assertFalse(C.TRACK_TYPE_TEXT in system.disabledTrackTypes)

        assertEquals(PlaybackTrackSelectionMode.OFF, VideoPlaybackDefaults(subtitles = VideoSubtitleDefault.OFF).initialSelection().textMode)
        assertEquals(PlaybackTrackSelectionMode.AUTO, VideoPlaybackDefaults().initialSelection().textMode)
    }

    @Test
    fun audioDefaultFollowsTheDeviceLanguageOnlyWhenAsked() {
        val device = parameters(
            VideoPlaybackDefaults(audio = VideoAudioDefault.DEVICE_LANGUAGE, deviceLanguages = listOf("de", "en")),
        )
        assertEquals(listOf("de", "en"), device.preferredAudioLanguages)

        val own = parameters(VideoPlaybackDefaults(audio = VideoAudioDefault.VIDEO_DEFAULT, deviceLanguages = listOf("de")))
        assertTrue(own.preferredAudioLanguages.isEmpty())
    }

    @Test
    fun aNewVideoStartsWithTheDefaultsFromSettings() {
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        try {
            coordinator.updatePlaybackDefaults(VideoPlaybackDefaults(subtitles = VideoSubtitleDefault.OFF))
            coordinator.attach(
                owner = Any(),
                media = VideoMedia(
                    mediaId = "t3_video05",
                    sources = listOf(
                        VideoMediaSource(
                            uri = "content://media/picked/video05",
                            kind = VideoMediaSourceKind.PROGRESSIVE,
                            mimeType = MimeTypes.VIDEO_MP4,
                        ),
                    ),
                ),
                muted = true,
                transitionId = 1L,
                rank = VIDEO_UNRANKED,
                playWhenReady = false,
            )

            assertTrue(C.TRACK_TYPE_TEXT in coordinator.player.trackSelectionParameters.disabledTrackTypes)
            assertEquals(PlaybackTrackSelectionMode.OFF, coordinator.trackOptions.selection.textMode)
        } finally {
            coordinator.releaseWithoutHanging()
        }
    }

    @Test
    fun mediaAndLinksGroupsTheVideoChoicesUnderOneHeading() {
        ensureWorkManagerInitialized(context)
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
        listOf(
            "Video",
            "Subtitles",
            "Audio language",
            "Playback options button",
            "Video quality on mobile data",
            "Gallery captions",
        ).forEach { text ->
            composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
            composeRule.waitForIdle()
            assertTrue(text, composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
        }
        // The old video-quality heading is folded into Video.
        assertTrue(composeRule.onAllNodesWithText("Video quality").fetchSemanticsNodes().isEmpty())
        // VIDEO05_SETTING_ROWS_SHOW_SHORT_VALUES_V1: rows show short values; the
        // long wording squeezed the descriptions to one word per line on the phone.
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Post + full screen"))
        assertTrue(composeRule.onAllNodesWithText("Post and full screen").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Auto · when the video marks them").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Video's own").fetchSemanticsNodes().isNotEmpty())
    }
}
