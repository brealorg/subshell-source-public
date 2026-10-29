package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.components.PostMedia
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NSFWMEDIA01_SHOW_NSFW_MEDIA_SETTING_V1 (owner 2026-10-27): "Show NSFW media"
 * (off by default, backed up under its own key) shows and plays NSFW media
 * without a tap; a spoiler stays covered whatever the setting says.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class NsfwMedia01ShowNsfwMediaTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun initWorkManager() {
        ensureWorkManagerInitialized(context)
    }

    private val image = SampleData.posts.first { it.kind == PostKind.IMAGE }
    private val nsfwImage = image.copy(id = "nsfw-image", nsfw = true, spoiler = false)
    private val nsfwSpoilerImage = image.copy(id = "nsfw-spoiler-image", nsfw = true, spoiler = true)
    private val nsfwVideo = SampleData.posts.first { it.kind == PostKind.VIDEO }
        .copy(id = "nsfw-video", nsfw = true, spoiler = false)

    @Test
    fun onlyNsfwMediaThatIsNoSpoilerPlaysWithoutATap() {
        assertTrue(sensitiveMediaCanAutoplay(nsfwVideo, emptySet(), showNsfwMedia = true))
        assertFalse(sensitiveMediaCanAutoplay(nsfwVideo, emptySet(), showNsfwMedia = false))
        assertFalse(
            sensitiveMediaCanAutoplay(nsfwVideo.copy(spoiler = true), emptySet(), showNsfwMedia = true),
        )
        val state = SubshellState()
        assertFalse(state.showNsfwMedia)
        assertFalse(state.sensitiveMediaAutoplayAllowed(nsfwVideo))
        state.updateShowNsfwMedia(true)
        assertTrue(state.sensitiveMediaAutoplayAllowed(nsfwVideo))
        assertFalse(state.sensitiveMediaAutoplayAllowed(nsfwVideo.copy(spoiler = true)))
    }

    @Test
    fun theSettingTravelsInABackupAndAnOlderBackupLeavesItOff() {
        val stored = SubshellSettings(showNsfwMedia = true).toStorageMap()
        assertEquals("true", stored[SubshellSettings.SHOW_NSFW_MEDIA])
        assertTrue(SubshellSettings.fromStorageMap(stored).showNsfwMedia)
        val older = stored - SubshellSettings.SHOW_NSFW_MEDIA
        assertFalse(SubshellSettings.fromStorageMap(older).showNsfwMedia)
        // The preview mode keeps its own key and values, so builds without
        // this setting still read the rest of a backup.
        assertEquals(
            SensitiveMediaPreviewMode.BLUR_AND_PRELOAD.name,
            stored[SubshellSettings.SENSITIVE_MEDIA_PREVIEW_MODE],
        )
    }

    private fun showMedia(post: Post, showNsfwMedia: Boolean, mode: SensitiveMediaPreviewMode) {
        val state = SubshellState(
            initialInteractionSideConfigured = true,
            initialShowNsfwPosts = true,
            initialSensitiveMediaPreviewMode = mode,
            initialShowNsfwMedia = showNsfwMedia,
        )
        composeRule.setContent {
            SubshellTheme {
                Box(Modifier.width(400.dp)) { PostMedia(post = post, state = state) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun coverShown(): Boolean = composeRule
        .onAllNodes(hasText("tap to reveal", substring = true), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()

    @Test
    fun withTheSettingOnAnNsfwImageHasNoCover() {
        showMedia(nsfwImage, showNsfwMedia = true, mode = SensitiveMediaPreviewMode.LOAD_ON_TAP)
        assertFalse(coverShown())
    }

    @Test
    fun withTheSettingOffAnNsfwImageKeepsItsCover() {
        showMedia(nsfwImage, showNsfwMedia = false, mode = SensitiveMediaPreviewMode.LOAD_ON_TAP)
        assertTrue(coverShown())
    }

    @Test
    fun aSpoilerStaysCoveredWithTheSettingOn() {
        showMedia(nsfwSpoilerImage, showNsfwMedia = true, mode = SensitiveMediaPreviewMode.LOAD_ON_TAP)
        assertTrue(coverShown())
    }

    @Test
    fun aListThumbnailOfNsfwMediaNeedsNoTapWithTheSettingOn() {
        val state = SubshellState(
            initialInteractionSideConfigured = true,
            initialShowNsfwPosts = true,
            initialShowNsfwMedia = true,
        )
        composeRule.setContent {
            SubshellTheme {
                LazyColumn {
                    item { PostCard(post = nsfwImage, state = state, onOpen = {}, layout = PostLayout.LIST) }
                }
            }
        }
        composeRule.waitForIdle()
        val asksForAReveal = SemanticsMatcher("asks for a reveal") { node ->
            node.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Show sensitive media"
        }
        assertTrue(
            composeRule.onAllNodes(asksForAReveal, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
    }
}
