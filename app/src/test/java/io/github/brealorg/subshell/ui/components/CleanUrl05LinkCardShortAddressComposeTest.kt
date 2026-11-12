package io.github.brealorg.subshell.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.model.UrlCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CLEANURL05_LINK_CARD_SHOWS_SHORT_ADDRESS_V1: a link post's card printed its whole address (a
 * 2 000-character one filled the screen). The card shows the domain and a short address; the
 * post keeps the whole address and a tap still opens it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CleanUrl05LinkCardShortAddressComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val whole = "https://fukurami.github.io/genurl/urlinfo.htm?" + "6c9828ee89d28c5fbd69638f9d80401e".repeat(60)

    private fun post(label: String) = Post(
        id = "p1",
        community = "subshell",
        author = "brealorg",
        age = "0m",
        title = "url test again",
        score = 1,
        comments = 0,
        kind = PostKind.LINK,
        mediaLabel = label,
        domain = "fukurami.github.io",
    )

    private fun shownTexts(): List<String> =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text] }
            .map { text -> text.text }

    @Test
    fun theCardShowsTheDomainAndAShortAddressNotTheWholeOne() {
        composeRule.setContent { MaterialTheme { LinkPanel(post(whole), onOpenLink = {}) } }
        val shown = shownTexts()
        assertTrue(shown.toString(), "fukurami.github.io" in shown)
        val address = shown.single { it.startsWith("fukurami.github.io/") }
        assertTrue(address, address.endsWith("…"))
        assertTrue(address, address.length <= 60)
        assertFalse(shown.toString(), shown.any { it.contains("6c9828ee89d28c5f") })
    }

    @Test
    fun aTapOnTheCardStillOpensTheLink() {
        var opened = 0
        composeRule.setContent { MaterialTheme { LinkPanel(post(whole), onOpenLink = { opened += 1 }) } }
        composeRule.onNodeWithText("fukurami.github.io").performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
    }

    @Test
    fun aShortAddressAndAPlainLabelAreShownAsTheyAre() {
        assertEquals("https://example.org/a", UrlCleaner.cardLabel("https://example.org/a"))
        assertEquals("3 items", UrlCleaner.cardLabel("3 items"))
        assertEquals("", UrlCleaner.cardLabel(""))
    }
}
