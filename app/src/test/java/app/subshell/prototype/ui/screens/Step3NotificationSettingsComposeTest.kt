package app.subshell.prototype.ui.screens

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.inbox.INBOX_BACKGROUND_SYNC_UNIQUE_WORK
import app.subshell.prototype.inbox.INBOX_NOTIFICATION_CHANNEL_ID
import app.subshell.prototype.inbox.InboxBackgroundEventStore
import app.subshell.prototype.inbox.InboxNotificationCenter
import app.subshell.prototype.inbox.inboxBackgroundEventKey
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * STEP3 (notify01a1, notify01a1r1, notify01a1r2): Comments & notifications has
 * one "Inbox notifications" switch and a "Notification types" row whose
 * summary counts the chosen types; the types live on their own page, each row
 * one switch, greyed out while notifications are off; turning notifications
 * on retakes the background baseline, creates the channel and schedules the
 * 15-minute check, asks for the Android permission when it is missing and
 * keeps the answer only for the account that asked; turning them off clears
 * what is shown; a local account cannot turn them on; search finds each type.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3NotificationSettingsComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val me = AccountSessionSummary(
        "reddit:me",
        "me_user",
        AccountSessionSource.AUTHENTICATED,
        AccountSessionState.CONNECTED,
    )
    private val other = AccountSessionSummary(
        "reddit:other",
        "other_user",
        AccountSessionSource.AUTHENTICATED,
        AccountSessionState.CONNECTED,
    )
    private val local = AccountSessionSummary(
        "local:fixture",
        "local",
        AccountSessionSource.LOCAL_FIXTURE,
        AccountSessionState.CONNECTED,
    )
    private val permissionAnswers = mutableListOf<(Boolean) -> Unit>()

    @Before
    fun setUp() {
        ensureWorkManagerInitialized(context)
        context.getSharedPreferences("subshell_inbox_notifications", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun signedIn(
        accounts: List<AccountSessionSummary> = listOf(me),
        active: String = me.stableId,
    ) = SubshellState(
        initialAccountSessions = accounts,
        initialActiveAccountId = active,
        initialInteractionSideConfigured = true,
    )

    private fun show(state: SubshellState) {
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            SubshellTheme {
                SettingsScreen(
                    state = state,
                    backupRepository = repository,
                    onBack = {},
                    initialDestinationName = SettingsDestination.COMMENTS.name,
                    onRequestInboxNotificationPermission = { answer -> permissionAnswers += answer },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
        composeRule.waitForIdle()
    }

    private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun switchRow(title: String) = hasText(title) and role(Role.Switch)

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun toggleState(title: String): ToggleableState? =
        composeRule.onAllNodes(switchRow(title)).fetchSemanticsNodes().single()
            .config.getOrNull(SemanticsProperties.ToggleableState)

    private fun isEnabled(title: String): Boolean =
        composeRule.onAllNodes(switchRow(title)).fetchSemanticsNodes().single()
            .config.getOrNull(SemanticsProperties.Disabled) == null

    private fun openTypes() {
        scrollTo("Notification types")
        click(hasText("Notification types"))
    }

    @Test
    fun commentsHasOneNotificationSwitchAndATypesRowWithItsCount() {
        val state = signedIn()
        state.updateInboxNotificationsEnabled(true)
        show(state)
        scrollTo("Inbox notifications")

        assertEquals(1, composeRule.onAllNodes(switchRow("Inbox notifications")).fetchSemanticsNodes().size)
        assertEquals(1, count("Check Reddit every 15 minutes for new notifications"))
        scrollTo("Notification types")
        assertEquals(1, count("5 types enabled"))
        // The types are chosen on their own page, not on this one.
        assertEquals(0, count("Mentions"))
    }

    @Test
    fun theTypesSummarySaysWhatIsOnAndWhetherNotificationsAre() {
        val state = signedIn()
        state.updateInboxNotificationsEnabled(false)
        show(state)
        scrollTo("Notification types")
        assertEquals(1, count("Notifications off · 5 selected"))

        composeRule.runOnIdle {
            state.updateInboxNotificationsEnabled(true)
            state.updateNotifyMentions(false)
            state.updateNotifyPrivateMessages(false)
            state.updateNotifyRedditNotices(false)
            state.updateNotifyModmail(false)
        }
        composeRule.waitForIdle()
        assertEquals(1, count("1 type enabled"))

        composeRule.runOnIdle { state.updateNotifyReplies(false) }
        composeRule.waitForIdle()
        assertEquals(1, count("No types enabled"))

        composeRule.runOnIdle { state.updateInboxNotificationsEnabled(false) }
        composeRule.waitForIdle()
        assertEquals(1, count("Notifications off · no types selected"))
    }

    @Test
    fun theTypesPageHasOneSwitchPerTypeAndBackShowsTheNewCount() {
        val state = signedIn()
        state.updateInboxNotificationsEnabled(true)
        show(state)
        openTypes()

        assertEquals(1, count("Choose what you want to be notified about."))
        for (title in listOf("Replies", "Mentions", "Private messages", "Reddit notices", "Modmail")) {
            assertEquals(title, ToggleableState.On, toggleState(title))
        }

        click(switchRow("Mentions"))
        assertEquals(ToggleableState.Off, toggleState("Mentions"))
        assertFalse(state.notifyMentions)

        click(hasContentDescription("Back"))
        scrollTo("Notification types")
        assertEquals(1, count("4 types enabled"))
        assertEquals(
            SettingsDestination.COMMENTS,
            settingsDestinationBackTarget(SettingsDestination.NOTIFICATION_TYPES, SettingsDestination.COMMENTS),
        )
    }

    @Test
    fun withNotificationsOffTheTypesCannotBeChanged() {
        val state = signedIn()
        state.updateInboxNotificationsEnabled(false)
        show(state)
        openTypes()

        for (title in listOf("Replies", "Mentions", "Private messages", "Reddit notices", "Modmail")) {
            assertFalse(title, isEnabled(title))
        }
        // A semantics OnClick bypasses `enabled` (a reader cannot tap a disabled
        // row), so the disabled state itself is what this test proves.
        assertTrue(state.notifyReplies)
    }

    @Test
    fun turningNotificationsOnRetakesTheBaselineCreatesTheChannelAndSchedulesTheCheck() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val store = InboxBackgroundEventStore(context)
        store.reconcile("reddit:me", listOf("a"))
        val state = signedIn()
        state.updateInboxNotificationsEnabled(false)
        show(state)
        scrollTo("Inbox notifications")

        click(switchRow("Inbox notifications"))

        assertTrue(state.inboxNotificationsEnabled)
        // A fresh baseline: the next check takes what is there without notifying.
        assertFalse(store.reconcile("reddit:me", listOf("a", "b")).initializedBefore)
        assertNotNull(
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(INBOX_NOTIFICATION_CHANNEL_ID),
        )
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(INBOX_BACKGROUND_SYNC_UNIQUE_WORK).get()
        assertEquals(1, work.count { !it.state.isFinished })
        assertEquals(emptyList<(Boolean) -> Unit>(), permissionAnswers)
    }

    @Test
    fun turningNotificationsOffClearsWhatIsShown() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, true).commit()
        val reply = InboxItem(
            id = "t1_reply",
            kind = ActivityKind.REPLY,
            title = "Reply from u/replier",
            sender = "u/replier",
            age = "now",
            preview = "the reply text",
            unread = true,
        )
        InboxNotificationCenter.postNewEvents(
            context = context,
            accountStableId = "reddit:me",
            items = listOf(reply),
            locallySeenIds = emptySet(),
            newEventKeys = listOf(reply.inboxBackgroundEventKey()),
        )
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertEquals(1, manager.allNotifications.size)
        val state = signedIn()
        state.updateInboxNotificationsEnabled(true)
        show(state)
        scrollTo("Inbox notifications")

        click(switchRow("Inbox notifications"))

        assertFalse(state.inboxNotificationsEnabled)
        // COMMIT01: the clean-up runs on IO.
        composeRule.waitUntil(5_000) { manager.allNotifications.isEmpty() }
        composeRule.waitUntil(5_000) {
            !context.getSharedPreferences("subshell_inbox_notifications", Context.MODE_PRIVATE)
                .contains("posted_notification_records_v1")
        }
    }

    @Test
    fun withoutThePermissionItIsAskedAndOnlyAYesForTheSameAccountTurnsThemOn() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val state = signedIn(accounts = listOf(me, other))
        state.updateInboxNotificationsEnabled(false)
        show(state)
        scrollTo("Inbox notifications")

        click(switchRow("Inbox notifications"))
        assertEquals(1, permissionAnswers.size)
        composeRule.runOnIdle { permissionAnswers.last()(false) }
        composeRule.waitForIdle()
        assertFalse(state.inboxNotificationsEnabled)

        click(switchRow("Inbox notifications"))
        composeRule.runOnIdle { permissionAnswers.last()(true) }
        composeRule.waitForIdle()
        assertTrue(state.inboxNotificationsEnabled)

        composeRule.runOnIdle { state.updateInboxNotificationsEnabled(false) }
        composeRule.waitForIdle()
        click(switchRow("Inbox notifications"))
        val answer = permissionAnswers.last()
        composeRule.runOnIdle {
            state.switchAccount(other.stableId)
            answer(true)
        }
        composeRule.waitForIdle()
        assertFalse(state.inboxNotificationsEnabled)
        assertEquals(3, permissionAnswers.size)
    }

    @Test
    fun aLocalAccountCannotTurnNotificationsOn() {
        val state = signedIn(accounts = listOf(me, local), active = local.stableId)
        state.updateInboxNotificationsEnabled(false)
        show(state)
        scrollTo("Inbox notifications")

        assertFalse(isEnabled("Inbox notifications"))
    }

    @Test
    fun searchFindsEachNotificationType() {
        assertTrue("notify-modmail" in settingsSearchResult("modmail").itemKeys)
        assertTrue("notify-replies" in settingsSearchResult("replies").itemKeys)
        assertTrue("notification-types" in settingsSearchResult("notification types").itemKeys)
    }

    // RECREATE03_SETTINGS_PERMISSION_ANSWER_SURVIVES_RECREATION_V1 (#241): the
    // switch asks through the activity result registry, so a yes given after a
    // dark-mode switch rebuilt the screen still turns notifications on.
    @Test
    fun aYesGivenAfterARebuildStillTurnsThemOn() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val state = signedIn()
        state.updateInboxNotificationsEnabled(false)
        val launches = mutableListOf<Int>()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                launches += requestCode
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        val repository = SettingsBackupRepository(context, "test")
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                SubshellTheme {
                    SettingsScreen(
                        state = state,
                        backupRepository = repository,
                        onBack = {},
                        initialDestinationName = SettingsDestination.COMMENTS.name,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        scrollTo("Inbox notifications")
        click(switchRow("Inbox notifications"))
        assertEquals(1, launches.size)

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        composeRule.runOnIdle { registry.dispatchResult(launches.single(), true) }
        composeRule.waitForIdle()

        assertTrue(state.inboxNotificationsEnabled)
    }
}
