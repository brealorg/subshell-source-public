package io.github.brealorg.subshell.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LINKMENU01_LONG_PRESS_A_LINK_V1: a long press on a web link, in text or on a link post's card,
 * opens the link menu; a long press anywhere else does not, and a plain tap still opens the link.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LinkMenu01LongPressLinkComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val original =
        "https://www.etsy.com/no-en/listing/4320800571/dog-show-bow-line-pet-hair-bow-yorkie" +
            "?ls=s&ga_order=most_relevant&ref=sr_gallery-1-4&utm_source=x&variation0=5495919706"
    private val cleaned =
        "https://www.etsy.com/no-en/listing/4320800571/dog-show-bow-line-pet-hair-bow-yorkie?variation0=5495919706"

    private fun clipboardText(): String? {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    }

    private fun showText(text: String, cleanLinks: Boolean = true, selectable: Boolean = false) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalCleanLinks provides cleanLinks) {
                    MarkdownText(text = text, selectable = selectable)
                }
            }
        }
    }

    private fun longPressTheText() {
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .performTouchInput { longClick() }
        settle()
    }

    /** The sheet animates in; Robolectric needs the clock moved before its items count as shown. */
    private fun settle() {
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun clickMenuItem(label: String) {
        composeRule.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    @Test
    fun aLongPressOnALinkInTextOpensTheMenuWithFourActions() {
        showText(original)
        longPressTheText()
        composeRule.onNodeWithText("Share link").assertExists()
        composeRule.onNodeWithText("Copy link").assertExists()
        composeRule.onNodeWithText("Open in browser").assertExists()
        composeRule.onNodeWithText("Copy original link").assertExists()
        // The menu starts with the link post's own card: the domain says what the menu is about.
        composeRule.onNodeWithText("etsy.com").assertExists()
    }

    @Test
    fun copyLinkCopiesTheCleanedAddressAndCopyOriginalTheOneAsWritten() {
        showText(original)
        longPressTheText()
        clickMenuItem("Copy link")
        assertEquals(cleaned, clipboardText())

        longPressTheText()
        clickMenuItem("Copy original link")
        assertEquals(original, clipboardText())
    }

    @Test
    fun withCleaningOffThereIsNoSeparateOriginalAndTheLinkIsAsWritten() {
        showText(original, cleanLinks = false)
        longPressTheText()
        composeRule.onNodeWithText("Copy link").assertExists()
        composeRule.onNodeWithText("Copy original link").assertDoesNotExist()
        clickMenuItem("Copy link")
        assertEquals(original, clipboardText())
    }

    @Test
    fun aLongPressOnTextThatIsNotALinkOpensNothing() {
        showText("just some words, no link")
        longPressTheText()
        composeRule.onNodeWithText("Share link").assertDoesNotExist()
    }

    @Test
    fun aLongPressOnALinkWinsInTextThatCanBeSelected() {
        showText(original, selectable = true)
        longPressTheText()
        composeRule.onNodeWithText("Share link").assertExists()
    }

    @Test
    fun aLongPressOnALinkPostCardOpensTheMenuAndATapStillOpensTheLink() {
        var opened = 0
        val post = Post(
            id = "p1",
            community = "subshell",
            author = "brealorg",
            age = "0m",
            title = "link",
            score = 1,
            comments = 0,
            kind = PostKind.LINK,
            mediaLabel = original,
            domain = "etsy.com",
            externalUrl = original,
        )
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalCleanLinks provides true) {
                    LinkPanel(post, onOpenLink = { opened += 1 })
                }
            }
        }
        composeRule.onNodeWithText("etsy.com").performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
        composeRule.onNodeWithText("Share link").assertDoesNotExist()

        composeRule.onNodeWithText("etsy.com").performTouchInput { longClick() }
        settle()
        composeRule.onNodeWithText("Copy original link").assertExists()
        assertEquals(1, opened)
    }

    @Test
    fun theTargetIsOnlyMadeForWebAddresses() {
        assertNull(linkActionTarget("/r/kotlin", cleanLinks = true))
        assertNull(linkActionTarget("mailto:a@b.c", cleanLinks = true))
        val target = linkActionTarget(original, cleanLinks = true)!!
        assertEquals(cleaned, target.link)
        assertTrue(target.hasCleanedLink)
        assertEquals(false, linkActionTarget(original, cleanLinks = false)!!.hasCleanedLink)
    }
}
