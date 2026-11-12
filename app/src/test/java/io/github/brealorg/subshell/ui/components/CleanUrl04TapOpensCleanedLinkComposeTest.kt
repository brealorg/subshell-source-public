package io.github.brealorg.subshell.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CLEANURL04_TAP_OPENS_CLEANED_LINK_V1: the whole path of a long tracked link in a
 * post or comment: shown short, tapped, and what Android is asked to open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CleanUrl04TapOpensCleanedLinkComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val tracked =
        "https://www.etsy.com/no-en/listing/4320800571/dog-show-bow-line-pet-hair-bow-yorkie" +
            "?ls=s&ga_order=most_relevant&ga_search_type=all&ref=sr_gallery-1-4" +
            "&organic_search_click=1&logging_key=52b0b778%3ALTe6997&utm_source=x&variation0=5495919706"

    private val opened = mutableListOf<String>()

    private fun show(cleanLinks: Boolean) {
        val handler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalUriHandler provides handler,
                LocalCleanLinks provides cleanLinks,
            ) {
                MarkdownText(text = tracked)
            }
        }
    }

    private fun shownText(): String =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text] }
            .joinToString("") { text -> text.text }

    private fun tapLink() {
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun withCleaningOnTheLinkShowsShortAndOpensWithoutTracking() {
        show(cleanLinks = true)
        val shown = shownText()
        assertFalse(shown, shown.contains("utm_source"))
        assertFalse(shown, shown.contains("https://"))
        tapLink()
        assertEquals(
            listOf(
                "https://www.etsy.com/no-en/listing/4320800571/dog-show-bow-line-pet-hair-bow-yorkie" +
                    "?variation0=5495919706",
            ),
            opened,
        )
    }

    @Test
    fun withCleaningOffTheSameTapOpensTheOriginalAddress() {
        show(cleanLinks = false)
        tapLink()
        assertEquals(listOf(tracked), opened)
    }
}
