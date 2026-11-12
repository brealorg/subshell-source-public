package io.github.brealorg.subshell.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.media.RemoteImageLoader
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.offline.OfflineImageStore
import io.github.brealorg.subshell.ui.PostLayout
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * LINKLABEL01_TALKBACK_READS_THE_DOMAIN_NOT_THE_ADDRESS_V1: a link post's preview
 * picture (Large) and thumbnail (List) were described by the post's whole
 * address, so TalkBack read a 2 000-character tracked link aloud. They are
 * described as "Link preview, <domain>".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LinkLabel01TalkBackReadsTheDomainComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val directory: File = Files.createTempDirectory("linklabel01").toFile()
    private val queryPart = "6c9828ee89d28c5fbd69638f9d80401e"
    private val whole = "https://fukurami.github.io/genurl/urlinfo.htm?" + queryPart.repeat(60)
    private val preview = "https://external-preview.redd.it/linklabel01.png"

    private val linkPost = Post(
        id = "link1",
        community = "subshell",
        author = "brealorg",
        age = "0m",
        title = "url test again",
        score = 1,
        comments = 0,
        kind = PostKind.LINK,
        mediaLabel = whole,
        domain = "fukurami.github.io",
        remoteImageUri = preview,
    )

    @Before
    fun setUp() {
        ensureWorkManagerInitialized(context)
        val png = ByteArrayOutputStream().also { out ->
            Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out)
        }.toByteArray()
        RemoteImageLoader.offlineImageStore = OfflineImageStore(directory).also { it.write(preview, png) }
    }

    @After
    fun tearDown() {
        RemoteImageLoader.offlineImageStore = null
        directory.deleteRecursively()
    }

    private fun show(layout: PostLayout) {
        val state = SubshellState(initialInteractionSideConfigured = true)
        composeRule.setContent {
            SubshellTheme {
                LazyColumn { item { PostCard(post = linkPost, state = state, onOpen = {}, layout = layout) } }
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasContentDescription("Link preview, fukurami.github.io"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun nothingReadsTheWholeAddress() {
        val spoken = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.ContentDescription] }
        assertFalse(spoken.toString(), spoken.any { it.contains(queryPart) })
    }

    @Test
    fun theLargePreviewPictureIsDescribedByItsDomain() {
        show(PostLayout.LARGE)
        nothingReadsTheWholeAddress()
    }

    @Test
    fun theListThumbnailIsDescribedByItsDomain() {
        show(PostLayout.LIST)
        nothingReadsTheWholeAddress()
    }

    @Test
    fun otherPostsKeepTheirLabelAndALinkWithoutADomainNamesTheShortAddress() {
        assertEquals("Link preview, fukurami.github.io", postMediaDescription(linkPost))
        val noDomain = postMediaDescription(linkPost.copy(domain = null))
        assertFalse(noDomain, noDomain.contains(queryPart))
        assertEquals("Link preview, https://example.org/a", postMediaDescription(linkPost.copy(domain = " ", mediaLabel = "https://example.org/a")))
        assertEquals("url test again", postMediaDescription(linkPost.copy(domain = null, mediaLabel = "")))
        assertEquals("Image", postMediaDescription(linkPost.copy(kind = PostKind.IMAGE, mediaLabel = "Image")))
        assertEquals("url test again", postMediaDescription(linkPost.copy(kind = PostKind.TEXT, mediaLabel = "")))
    }
}
