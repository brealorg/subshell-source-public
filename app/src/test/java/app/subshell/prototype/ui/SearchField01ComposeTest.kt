package app.subshell.prototype.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.components.SubshellSearchField
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SEARCHFIELD01_AN_M3_TEXT_FIELD_V1 (owner 2026-10-17): the search field is an
 * M3 TextField that keeps what Material's deprecated search field gave: the
 * keyboard's Search key with the typed text, and the name "Search".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SearchField01ComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Test
    fun theFieldIsNamedSearchAndSearchesFromTheKeyboard() {
        var query by mutableStateOf("")
        val searched = mutableListOf<String>()
        composeRule.setContent {
            SubshellTheme {
                SubshellSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search Reddit",
                    onSearch = { searched += it },
                )
            }
        }

        val field = composeRule.onNode(hasSetTextAction() and hasContentDescription("Search"))
        assertEquals(ImeAction.Search, field.fetchSemanticsNode().config[SemanticsProperties.ImeAction])
        field.performTextInput("cats")
        field.performImeAction()
        composeRule.waitForIdle()

        assertEquals(listOf("cats"), searched)
    }
}
