package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.about.OPEN_SOURCE_LIBRARIES
import io.github.brealorg.subshell.about.OpenSourceLicence
import io.github.brealorg.subshell.about.SUBSHELL_LICENCE_NOTICE
import io.github.brealorg.subshell.about.readLicenceText
import io.github.brealorg.subshell.about.reflowLicenceText
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE39_LICENCES_AND_SOURCE_IN_THE_APP_V1 (#39): Help & about says subshell
 * is GPL-3.0-or-later with no warranty, links the source, and lists the
 * libraries with their bundled licence texts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue39LicencesComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun shows(text: String) =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun tap(text: String) =
        composeRule.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun helpShowsTheLicenceTheSourceAndEveryLibrary() {
        ensureWorkManagerInitialized(context)
        composeRule.setContent { SubshellTheme { HelpScreen(onBack = {}) } }
        composeRule.waitForIdle()
        assertTrue(shows(SUBSHELL_LICENCE_NOTICE))
        assertTrue(composeRule.onAllNodes(hasText("Source code") and hasClickAction()).fetchSemanticsNodes().isNotEmpty())

        tap("Open-source licences")
        composeRule.waitForIdle()
        // A lazy list: the first rows are composed; the full list is checked below.
        assertTrue(shows("AndroidX Activity"))
        assertTrue(shows("Apache License 2.0"))

        tap("subshell")
        composeRule.waitUntil(5_000) { shows("GNU GENERAL PUBLIC LICENSE") }
    }

    @Test
    fun everyLicenceTextIsBundledAndEveryLibraryHasOne() = runBlocking {
        for (licence in OpenSourceLicence.entries) {
            assertTrue(licence.name, readLicenceText(context, licence).length > 900)
        }
        assertTrue(readLicenceText(context, OpenSourceLicence.MIT_CHECKER_QUAL).contains("Checker Framework"))
        // Reflowed for a phone: a paragraph is one line, blank lines separate them.
        assertEquals(
            "Version 3, 29 June 2007\n\nEveryone is permitted to copy and distribute verbatim copies",
            reflowLicenceText(
                "                       Version 3, 29 June 2007\n\n Everyone is permitted to copy and\n distribute verbatim copies\n",
            ),
        )
        assertEquals(
            OPEN_SOURCE_LIBRARIES.size,
            OPEN_SOURCE_LIBRARIES.map { it.artifact }.toSet().size,
        )
        assertTrue(OPEN_SOURCE_LIBRARIES.size >= 50)
    }
}
