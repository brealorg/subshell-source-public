package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.shortcuts.LauncherShortcutEntry
import app.subshell.prototype.shortcuts.LauncherShortcutPlan
import app.subshell.prototype.shortcuts.LauncherShortcutPublisher
import app.subshell.prototype.shortcuts.LauncherShortcutsEnabledStore
import app.subshell.prototype.shortcuts.PinnedLauncherShortcut
import app.subshell.prototype.ui.app.LauncherShortcutsEffect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE37_LAUNCHER_SHORTCUTS_V1: the launcher follows the setting and the
 * active account's favourite and hidden communities.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LauncherShortcutsEffectComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()

    private class RecordingPublisher : LauncherShortcutPublisher {
        val plans = mutableListOf<LauncherShortcutPlan>()

        override fun pinnedShortcuts(): List<PinnedLauncherShortcut> = emptyList()

        override fun apply(plan: LauncherShortcutPlan) {
            synchronized(plans) { plans += plan }
        }

        override fun isPinSupported(): Boolean = true

        override fun requestPin(entry: LauncherShortcutEntry): Boolean = true

        fun lastDynamic(): List<String> = synchronized(plans) {
            plans.last().dynamic.map(LauncherShortcutEntry::label)
        }
    }

    private fun state(): SubshellState {
        val stored = mutableMapOf(
            "local:root" to setOf("android", "kotlin"),
            "local:packetmoss" to setOf("linux"),
        )
        return SubshellState(
            initialActiveAccountUsername = "root",
            loadFavoriteCommunityNames = { accountId -> stored[accountId].orEmpty() },
            persistFavoriteCommunityNames = { accountId, names -> stored[accountId] = names },
        )
    }

    private fun host(
        state: SubshellState,
        publisher: RecordingPublisher,
        customFeedsState: CustomFeedsUiState = CustomFeedsUiState(),
    ) {
        composeRule.setContent {
            LauncherShortcutsEffect(
                state = state,
                customFeedsState = customFeedsState,
                publisher = publisher,
            )
        }
    }

    private fun awaitDynamic(publisher: RecordingPublisher, expected: List<String>) {
        // The change recomposes on the next idle; the launcher call then runs on IO.
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            synchronized(publisher.plans) { publisher.plans.isNotEmpty() } &&
                publisher.lastDynamic() == expected
        }
        assertEquals(expected, publisher.lastDynamic())
    }

    @Test
    fun offByDefaultPublishesNothingThenFollowsTheSetting() {
        val state = state()
        val publisher = RecordingPublisher()
        host(state, publisher)

        awaitDynamic(publisher, emptyList())

        LauncherShortcutsEnabledStore.write(application, true)
        awaitDynamic(publisher, listOf("r/android", "r/kotlin"))

        LauncherShortcutsEnabledStore.write(application, false)
        awaitDynamic(publisher, emptyList())
    }

    @Test
    fun hidingAFavouriteRemovesItsShortcut() {
        LauncherShortcutsEnabledStore.write(application, true)
        val state = state()
        val publisher = RecordingPublisher()
        host(state, publisher)
        awaitDynamic(publisher, listOf("r/android", "r/kotlin"))

        composeRule.runOnIdle { state.toggleHiddenCommunity("kotlin") }

        awaitDynamic(publisher, listOf("r/android"))
    }

    @Test
    fun anAccountSwitchPublishesTheNewAccountsFavourites() {
        LauncherShortcutsEnabledStore.write(application, true)
        val state = state()
        val publisher = RecordingPublisher()
        host(state, publisher)
        awaitDynamic(publisher, listOf("r/android", "r/kotlin"))

        composeRule.runOnIdle { state.switchAccount("packetmoss") }

        awaitDynamic(publisher, listOf("r/linux"))
    }

    @Test
    fun aSignedInAccountGetsInboxFirstAndItsFavouriteFeedsByLoadedName() {
        LauncherShortcutsEnabledStore.write(application, true)
        val alice = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(alice),
            initialActiveAccountId = alice.stableId,
            loadFavoriteCommunityNames = { setOf("zig") },
            loadFavoriteCustomFeedPaths = { setOf("/user/alice/m/cats") },
        )
        val publisher = RecordingPublisher()
        host(
            state,
            publisher,
            customFeedsState = CustomFeedsUiState(
                accountStableId = alice.stableId,
                feeds = listOf(
                    CustomFeedSummary(
                        path = "/user/alice/m/cats",
                        displayName = "Cute Cats",
                        description = "",
                        visibility = "private",
                        subredditNames = emptyList(),
                    ),
                ),
                contentRevision = 1L,
            ),
        )

        awaitDynamic(publisher, listOf("Inbox", "Cute Cats", "r/zig"))
    }
}
