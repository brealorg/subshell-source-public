package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.ModeratorLogUiState
import io.github.brealorg.subshell.data.account.RedditInboxUiState
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.components.ModeratorLogDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1 (96d): the inbox and the mod log
 * offer "Cancel authorization" while they wait for the browser (they used to
 * show only a disabled or missing action).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue96dCancelAuthorizationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun theInboxCanCancelAnAuthorizationThatWaitsForTheBrowser() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        var cancels = 0
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditInboxState = RedditInboxUiState(
                    accountStableId = account.stableId,
                    authorizing = true,
                    authorizationRequired = true,
                ),
                onCancelRedditInboxAuthorization = { cancels += 1 },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel authorization").performClick()

        assertEquals(1, cancels)
    }

    @Test
    fun theModLogCanCancelAnAuthorizationThatWaitsForTheBrowser() {
        var cancels = 0
        var authorizes = 0
        composeRule.setContent {
            ModeratorLogDialog(
                accountStableId = "reddit:me",
                state = ModeratorLogUiState(
                    visible = true,
                    accountStableId = "reddit:me",
                    communityName = "subshell",
                    authorizing = true,
                    authorizationRequired = true,
                ),
                onRefresh = {},
                onAppend = {},
                onAuthorize = { authorizes += 1 },
                onDismiss = {},
                embeddedInModToolsWorkspace = true,
                onCancelAuthorization = { cancels += 1 },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel authorization").performClick()

        assertEquals(1, cancels)
        assertEquals(0, authorizes)
    }
}
