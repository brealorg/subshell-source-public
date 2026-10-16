package app.subshell.prototype.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoTrackSelectionTest {
    @Test
    fun selectionModesTransitionWithoutRetainingStaleKeys() {
        val video = PlaybackTrackKey(groupIndex = 1, trackIndex = 2)
        val audio = PlaybackTrackKey(groupIndex = 3, trackIndex = 0)
        val text = PlaybackTrackKey(groupIndex = 4, trackIndex = 1)

        val fixed = PlaybackTrackSelectionState()
            .withFixedVideo(video)
            .withFixedAudio(audio)
            .withTextDisabled()
            .withFixedText(text)

        assertEquals(PlaybackTrackSelectionMode.FIXED, fixed.videoMode)
        assertEquals(video, fixed.videoKey)
        assertEquals(PlaybackTrackSelectionMode.FIXED, fixed.audioMode)
        assertEquals(audio, fixed.audioKey)
        assertEquals(PlaybackTrackSelectionMode.FIXED, fixed.textMode)
        assertEquals(text, fixed.textKey)

        val automatic = fixed
            .withAutomaticVideo()
            .withAutomaticAudio()
            .withAutomaticText()

        assertEquals(PlaybackTrackSelectionMode.AUTO, automatic.videoMode)
        assertNull(automatic.videoKey)
        assertEquals(PlaybackTrackSelectionMode.AUTO, automatic.audioMode)
        assertNull(automatic.audioKey)
        assertEquals(PlaybackTrackSelectionMode.AUTO, automatic.textMode)
        assertNull(automatic.textKey)

        val off = automatic.withFixedText(text).withTextDisabled()
        assertEquals(PlaybackTrackSelectionMode.OFF, off.textMode)
        assertNull(off.textKey)
    }

    @Test
    fun trackTitlesPreferLabelsThenLanguageThenDeterministicFallbacks() {
        assertEquals(
            "720p",
            videoTrackChoiceTitle(
                VideoTrackFormatDiagnostics(width = 1280, height = 720),
                ordinal = 1,
            ),
        )
        assertEquals(
            "Director commentary",
            audioTrackChoiceTitle(
                VideoTrackFormatDiagnostics(label = "Director commentary", language = "en"),
                ordinal = 1,
            ),
        )
        assertEquals(
            "EN",
            audioTrackChoiceTitle(
                VideoTrackFormatDiagnostics(language = "en"),
                ordinal = 2,
            ),
        )
        assertEquals(
            "Norsk bokmål",
            textTrackChoiceTitle(
                VideoTrackFormatDiagnostics(label = "Norsk bokmål", language = "nb"),
                ordinal = 1,
            ),
        )
        assertEquals(
            "Subtitles 3",
            textTrackChoiceTitle(VideoTrackFormatDiagnostics(), ordinal = 3),
        )
    }
    @Test
    fun rootPlaybackOptionSummariesExposeCurrentSelectionsWithoutOpeningLists() {
        val video = PlaybackTrackChoice(
            key = PlaybackTrackKey(groupIndex = 0, trackIndex = 1),
            title = "720p",
            summary = "1280×720",
            selected = true,
        )
        val audio = PlaybackTrackChoice(
            key = PlaybackTrackKey(groupIndex = 1, trackIndex = 0),
            title = "Director commentary",
            summary = "EN · 2 ch",
            selected = true,
        )
        val text = PlaybackTrackChoice(
            key = PlaybackTrackKey(groupIndex = 2, trackIndex = 1),
            title = "Norsk bokmål",
            summary = "nb · VTT",
            selected = true,
        )

        val fixed = PlaybackTrackOptions(
            known = true,
            video = listOf(video),
            audio = listOf(audio),
            text = listOf(text),
            selection = PlaybackTrackSelectionState(
                videoMode = PlaybackTrackSelectionMode.FIXED,
                videoKey = video.key,
                audioMode = PlaybackTrackSelectionMode.FIXED,
                audioKey = audio.key,
                textMode = PlaybackTrackSelectionMode.FIXED,
                textKey = text.key,
            ),
        )

        assertEquals("720p", fixed.videoSelectionSummary("High"))
        assertEquals("Director commentary", fixed.audioSelectionSummary())
        assertEquals("Norsk bokmål", fixed.textSelectionSummary())

        val automatic = fixed.copy(selection = PlaybackTrackSelectionState())
        assertEquals("Auto · High ceiling", automatic.videoSelectionSummary("High"))
        assertEquals("Auto · source default", automatic.audioSelectionSummary())
        assertEquals("Auto · forced or source default", automatic.textSelectionSummary())

        val subtitlesOff = automatic.copy(
            selection = automatic.selection.withTextDisabled(),
        )
        assertEquals("Off", subtitlesOff.textSelectionSummary())
    }

}
