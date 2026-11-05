package io.github.brealorg.subshell.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import io.github.brealorg.subshell.harness.ProvideTestVideoPlayback
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.ui.components.PostCard
import io.github.brealorg.subshell.ui.screens.settingsSearchResult
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
 * ISSUE284_POST_TEXT_COLLAPSES_LIKE_A_COMMENT_V1 (#284): with "Collapse post
 * text" on, a tap on the open post's title hides its text and "Text hidden"
 * brings it back; with it off (the default) and in feeds nothing changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue284CollapsePostTextComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val post = Post(
        id = "t3_long284",
        community = "subshell",
        author = "writer",
        age = "2h",
        title = "A long post",
        body = "The first paragraph of a long self post.",
        score = 10,
        comments = 3,
        kind = PostKind.TEXT,
    )

    private fun show(state: SubshellState, inPostDetail: Boolean, onOpen: () -> Unit = {}) {
        composeRule.setContent {
            SubshellTheme {
                ProvideTestVideoPlayback {
                    PostCard(
                        post = post,
                        state = state,
                        onOpen = onOpen,
                        bodyMaxLines = if (inPostDetail) Int.MAX_VALUE else 4,
                        inPostDetail = inPostDetail,
                        suppressWhenHiddenStateDiffers = false,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun shows(text: String) =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun tap(text: String) {
        composeRule.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun theTitleHidesAndShowsThePostText() {
        val state = SubshellState(initialCollapsiblePostText = true)
        show(state, inPostDetail = true)
        assertTrue(shows("The first paragraph"))

        tap("A long post")
        assertFalse(shows("The first paragraph"))
        assertTrue(shows("Text hidden"))

        tap("Text hidden")
        assertTrue(shows("The first paragraph"))
        tap("A long post")
        tap("A long post")
        assertTrue(shows("The first paragraph"))
    }

    @Test
    fun offByDefaultTheTitleDoesNothing() {
        val state = SubshellState()
        assertFalse(state.collapsiblePostText)
        show(state, inPostDetail = true)

        // The title is no control of its own; a tap on it is a tap on the card.
        composeRule.onNode(hasText("A long post")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(shows("The first paragraph"))
        assertFalse(shows("Text hidden"))
    }

    @Test
    fun aFeedCardStillOpensThePost() {
        var opened = 0
        show(SubshellState(initialCollapsiblePostText = true), inPostDetail = false, onOpen = { opened++ })

        composeRule.onNode(hasText("A long post")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(shows("The first paragraph"))
        assertFalse(shows("Text hidden"))
        assertEquals(1, opened)
    }

    @Test
    fun theSettingIsStoredBackedUpAndFound() {
        val persisted = mutableMapOf<String, String>()
        val state = SubshellState(persistPreference = { key, value -> persisted[key] = value })
        state.updateCollapsiblePostText(true)
        assertEquals("true", persisted[SubshellSettings.COLLAPSIBLE_POST_TEXT])

        assertFalse(SubshellSettings().collapsiblePostText)
        val restored = SubshellSettings.fromStorageMap(SubshellSettings(collapsiblePostText = true).toStorageMap())
        assertTrue(restored.collapsiblePostText)
        // A backup from before #284 has no such key.
        val older = SubshellSettings().toStorageMap() - SubshellSettings.COLLAPSIBLE_POST_TEXT
        assertFalse(SubshellSettings.fromStorageMap(older).collapsiblePostText)

        assertTrue("collapsible-post-text" in settingsSearchResult("collapse post text").itemKeys)
    }
}
