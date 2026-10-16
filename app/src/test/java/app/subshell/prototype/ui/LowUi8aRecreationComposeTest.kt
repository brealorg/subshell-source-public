package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.data.account.ModeratorInviteUiState
import app.subshell.prototype.data.account.ModeratorPermissionUpdateUiState
import app.subshell.prototype.data.account.ModeratorRemovalUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.diagnostics.DiagnosticOperation
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.ui.app.AppSessionStartRecord
import app.subshell.prototype.ui.app.CommunityEntryPoints
import app.subshell.prototype.ui.app.ModerationQueueEntryPoints
import app.subshell.prototype.ui.app.ModeratorToolsEntryPoints
import app.subshell.prototype.ui.app.ModeratorToolsRootEntryPoints
import app.subshell.prototype.ui.app.moderatorToolsRootEntryPoints
import app.subshell.prototype.ui.components.CommunityMuteSheet
import app.subshell.prototype.ui.components.ModeratorTeamContent
import app.subshell.prototype.ui.screens.CommunitiesScreen
import app.subshell.prototype.ui.screens.DiagnosticsScreen
import app.subshell.prototype.ui.screens.InboxPrimaryTab
import app.subshell.prototype.ui.screens.InboxScreen
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
 * LOWUI8a: effects that ran on every recreation or re-entry instead of once.
 * LOWUI8_ACCOUNT_RESETS_ONLY_ON_A_CHANGE_V1 (OCB-109/115),
 * LOWUI8_ONE_SESSION_START_PER_PROCESS_V1 (OCB-116),
 * LOWUI8_DIAGNOSTICS_OPENED_ONCE_PER_VISIT_V1 (OCB-213),
 * LOWUI8_SAVED_MOD_TAB_WAITS_FOR_THE_DIRECTORY_V1 (OCB-219),
 * LOWUI8_FAVORITES_DEFAULT_APPLIED_ONCE_V1 (OCB-182),
 * LOWUI8_OPEN_FEED_CLOSED_ONLY_BY_A_LOADED_LIST_V1 (OCB-186),
 * LOWUI8_MUTE_EXPIRY_COUNTS_FROM_THE_TAP_V1 (OCB-112/129),
 * LOWUI8_DISMISSED_MODERATOR_RESULTS_STAY_DISMISSED_V1 (OCB-148).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LowUi8aRecreationComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val other = account.copy(stableId = "reddit:other", username = "other-user")
    private val feed = CustomFeedSummary(
        path = "/user/connected-user/m/tech",
        displayName = "Tech",
        description = "",
        visibility = "private",
        subredditNames = listOf("android"),
    )

    private fun signedIn(vararg accounts: AccountSessionSummary = arrayOf(account)) = SubshellState(
        initialAccountSessions = accounts.toList(),
        initialActiveAccountId = accounts.first().stableId,
        initialInteractionSideConfigured = true,
    )

    private fun SemanticsNodeInteractionsProvider.selectedCount(label: String): Int =
        onAllNodes(hasText(label, substring = true) and isSelected()).fetchSemanticsNodes().size

    private fun count(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun diagnosticCount(operation: DiagnosticOperation): Int =
        DiagnosticEventStore(context).snapshot().events.count { event -> event.operation == operation }

    /** No further event of [operation] arrives within [millis] (the writes run on IO). */
    private fun assertStaysAt(operation: DiagnosticOperation, expected: Int, millis: Long = 1_500L) {
        val until = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < until) {
            composeRule.waitForIdle()
            assertEquals(expected, diagnosticCount(operation))
            Thread.sleep(50)
        }
    }

    @Test
    fun theInboxTabSurvivesRecreationAndResetsOnAnAccountSwitch() {
        ensureWorkManagerInitialized(context)
        val state = signedIn(account, other)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.navigate(MainDestination.INBOX) }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Messages", substring = true) and isSelected().not()).performClick()
        composeRule.waitForIdle()
        assertEquals(1, composeRule.selectedCount("Messages"))
        // Recreated while another screen shows (an open inbox wrote its tab
        // back and hid the reset), then back to the inbox.
        composeRule.runOnIdle { state.navigate(MainDestination.HOME) }
        composeRule.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.navigate(MainDestination.INBOX) }
        composeRule.waitForIdle()
        assertEquals(1, composeRule.selectedCount("Messages"))

        composeRule.runOnIdle { state.switchAccount(other.stableId) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.navigate(MainDestination.INBOX) }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.selectedCount("Messages"))
        assertEquals(1, composeRule.selectedCount("Activity"))
    }

    // STEP3 (next01a3c): the chosen Mod section is kept per account across
    // recreation and starts over (Queue) after an account switch.
    @Test
    fun theInboxModSectionSurvivesRecreationAndResetsOnAnAccountSwitch() {
        ensureWorkManagerInitialized(context)
        val state = signedIn(account, other)
        var moderation by mutableStateOf(
            RedditModerationUiState(
                accountStableId = account.stableId,
                moderatedCommunities = setOf("subshell"),
                directoryLoaded = true,
            ),
        )
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditModeration = ModerationQueueEntryPoints(redditModerationState = moderation),
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.navigate(MainDestination.INBOX) }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Mod") and isSelected().not()).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Modmail") and isSelected().not()).performClick()
        composeRule.waitForIdle()
        assertEquals(1, composeRule.selectedCount("Modmail"))
        composeRule.runOnIdle { state.navigate(MainDestination.HOME) }
        composeRule.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.navigate(MainDestination.INBOX) }
        composeRule.waitForIdle()
        assertEquals(1, composeRule.selectedCount("Modmail"))

        composeRule.runOnIdle {
            state.switchAccount(other.stableId)
            moderation = moderation.copy(accountStableId = other.stableId)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.navigate(MainDestination.INBOX) }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Mod") and isSelected().not()).performClick()
        composeRule.waitForIdle()
        assertEquals(0, composeRule.selectedCount("Modmail"))
        assertEquals(1, composeRule.selectedCount("Queue"))
    }

    @Test
    fun theModeratorWorkspaceSurvivesRecreation() {
        val accounts = localFixtureAccountSummaries(listOf("root", "packetmoss"))
        val state = SubshellState(initialAccountSessions = accounts, initialActiveAccountId = accounts.first().stableId)
        lateinit var root: ModeratorToolsRootEntryPoints
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            root = moderatorToolsRootEntryPoints(
                state = state,
                redditCommunity = CommunityEntryPoints(),
                redditModeration = ModerationQueueEntryPoints(),
                moderatorTools = ModeratorToolsEntryPoints(),
                moderatorTeamRuntime = ModeratorTeamRuntimeEntryPoints(),
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { root.navigation.openRoot("kotlin") }
        composeRule.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals("kotlin", root.workspaceCommunity)
    }

    @Test
    fun theSessionStartIsRecordedOncePerProcess() {
        ensureWorkManagerInitialized(context)
        AppSessionStartRecord.resetForTest()
        val before = diagnosticCount(DiagnosticOperation.APP_SESSION_STARTED)
        val state = signedIn()
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitUntil(10_000) { diagnosticCount(DiagnosticOperation.APP_SESSION_STARTED) == before + 1 }

        restoration.emulateSavedInstanceStateRestore()

        assertStaysAt(DiagnosticOperation.APP_SESSION_STARTED, before + 1)
    }

    @Test
    fun diagnosticsOpenedIsRecordedOncePerVisit() {
        val before = diagnosticCount(DiagnosticOperation.DIAGNOSTICS_OPENED)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { DiagnosticsScreen(onBack = {}) }
        composeRule.waitUntil(10_000) { diagnosticCount(DiagnosticOperation.DIAGNOSTICS_OPENED) == before + 1 }

        restoration.emulateSavedInstanceStateRestore()

        assertStaysAt(DiagnosticOperation.DIAGNOSTICS_OPENED, before + 1)
    }

    @Test
    fun aRequestedModTabIsNotWrittenBackBeforeTheDirectoryLoads() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val reported = mutableListOf<InboxPrimaryTab>()
        var moderation by mutableStateOf(RedditModerationUiState(accountStableId = account.stableId))
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditModerationState = moderation,
                selectedPrimaryTab = InboxPrimaryTab.MOD,
                onPrimaryTabChanged = { tab -> reported += tab },
            )
        }
        composeRule.waitForIdle()
        assertFalse(InboxPrimaryTab.ACTIVITY in reported)

        composeRule.runOnIdle {
            moderation = moderation.copy(moderatedCommunities = setOf("subshell"), directoryLoaded = true)
        }
        composeRule.waitForIdle()
        assertFalse(InboxPrimaryTab.ACTIVITY in reported)
        assertEquals(1, composeRule.selectedCount("Mod"))
    }

    @Test
    fun aRequestedModTabFallsBackOnceTheDirectorySaysNone() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val reported = mutableListOf<InboxPrimaryTab>()
        composeRule.setContent {
            InboxScreen(
                state = state,
                redditModerationState = RedditModerationUiState(
                    accountStableId = account.stableId,
                    directoryLoaded = true,
                ),
                selectedPrimaryTab = InboxPrimaryTab.MOD,
                onPrimaryTabChanged = { tab -> reported += tab },
            )
        }
        composeRule.waitForIdle()

        assertTrue(InboxPrimaryTab.ACTIVITY in reported)
    }

    @Test
    fun theJoinedChoiceIsNotOverriddenByTheFavoritesDefault() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        state.toggleFavoriteCustomFeed(feed.path)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            CommunitiesScreen(
                state = state,
                redditSubscriptionsState = RedditSubscriptionsUiState(
                    accountStableId = account.stableId,
                    loaded = true,
                ),
                customFeedsState = CustomFeedsUiState(accountStableId = account.stableId, feeds = listOf(feed)),
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, composeRule.selectedCount("Favorites"))
        composeRule.onNode(hasText("Joined") and isSelected().not()).performClick()
        composeRule.waitForIdle()
        assertEquals(1, composeRule.selectedCount("Joined"))

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals(1, composeRule.selectedCount("Joined"))
    }

    @Test
    fun aRestoredOpenFeedStaysOpenUntilTheListHasLoaded() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val loaded = CustomFeedsUiState(accountStableId = account.stableId, feeds = listOf(feed), contentRevision = 1L)
        var feeds by mutableStateOf(loaded)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            CommunitiesScreen(
                state = state,
                redditSubscriptionsState = RedditSubscriptionsUiState(
                    accountStableId = account.stableId,
                    loaded = true,
                ),
                customFeedsState = feeds,
            )
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Feeds")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tech").performClick()
        composeRule.waitForIdle()
        assertEquals(0, count("Custom feeds"))

        // After process death the coordinator starts empty.
        composeRule.runOnIdle { feeds = CustomFeedsUiState() }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.runOnIdle { feeds = loaded.copy(contentRevision = 2L) }
        composeRule.waitForIdle()

        assertEquals(0, count("Custom feeds"))
    }

    @Test
    fun aMutePresetCountsFromTheTap() {
        var now = 1_000L
        var muted: Long? = null
        composeRule.setContent {
            CommunityMuteSheet(
                communityLabel = "r/subshell",
                nowEpochMillis = now,
                onDismiss = {},
                onMute = { expiry -> muted = expiry },
                clock = { now },
            )
        }
        composeRule.waitForIdle()
        // The sheet sat open for an hour; nothing recomposed it.
        now += 60L * 60L * 1_000L

        composeRule.onNodeWithText(CommunityMutePreset.ONE_DAY.label).performClick()
        composeRule.waitForIdle()

        assertEquals(CommunityMutePreset.ONE_DAY.expiryFrom(now), muted)
    }

    @Test
    fun aDismissedInviteResultDoesNotComeBackOrReload() {
        val loads = mutableListOf<Boolean>()
        composeRule.setContent {
            ModeratorTeamContent(
                accountStableId = account.stableId,
                communityName = "subshell",
                activeUsername = "connected-user",
                canRead = true,
                uiState = RedditCommunityInfoUiState(
                    accountStableId = account.stableId,
                    communityName = "subshell",
                    snapshot = CommunityInfoSnapshot(
                        communityName = "subshell",
                        moderators = listOf(CommunityModerator("connected-user", listOf("all"))),
                    ),
                    loaded = true,
                ),
                avatarImageUri = { null },
                onEnsureIdentity = {},
                onLoad = { force -> loads += force },
                inviteState = ModeratorInviteUiState(
                    visible = false,
                    accountStableId = account.stableId,
                    communityName = "subshell",
                    lastInvitedUsername = "new_mod",
                    completionRevision = 1L,
                ),
                onOpenInviteContext = {},
                onInvite = {},
                onAuthorizeInvite = {},
                onDismissInvite = {},
                permissionUpdateState = ModeratorPermissionUpdateUiState(),
                onOpenPermissionUpdateContext = {},
                onUpdatePermissions = {},
                onAuthorizePermissionUpdate = {},
                onDismissPermissionUpdate = {},
                removalState = ModeratorRemovalUiState(),
                onOpenRemovalContext = {},
                onRemoveModerator = {},
                onAuthorizeRemoval = {},
                onDismissRemoval = {},
                onBack = {},
            )
        }
        composeRule.waitForIdle()

        assertEquals(0, count("Invitation sent"))
        assertFalse(true in loads)
    }
}
