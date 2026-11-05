package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.applock.AppLockEntryPoints
import io.github.brealorg.subshell.applock.AppLockSettings
import io.github.brealorg.subshell.applock.LocalAppLock
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.CustomFeedSummary
import io.github.brealorg.subshell.data.account.CustomFeedsUiState
import io.github.brealorg.subshell.data.account.DirectChatStartUiState
import io.github.brealorg.subshell.data.account.PostEditPhase
import io.github.brealorg.subshell.data.account.PostEditUiState
import io.github.brealorg.subshell.data.account.PostManagePhase
import io.github.brealorg.subshell.data.account.PostManageTarget
import io.github.brealorg.subshell.data.account.PostManageUiState
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiState
import io.github.brealorg.subshell.data.account.normalizePostEditFullname
import io.github.brealorg.subshell.data.account.normalizePostManageCommunity
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.share.ShareIntoSubshellLaunch
import io.github.brealorg.subshell.ui.app.CustomFeedLinkEffect
import io.github.brealorg.subshell.ui.app.ShareIntoSubshellEffect
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import io.github.brealorg.subshell.ui.screens.CommunitiesScreen
import io.github.brealorg.subshell.ui.screens.NewMessageScreen
import io.github.brealorg.subshell.ui.screens.PostEditScreen
import io.github.brealorg.subshell.ui.screens.PostManageScreen
import io.github.brealorg.subshell.ui.screens.SettingsDestination
import io.github.brealorg.subshell.ui.screens.SettingsScreen
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOWUI8d: LOWUI8_OWN_FEED_LINK_MATCHES_AFTER_THE_LIST_LOADS_V1 (OCB-113),
 * LOWUI8_SHARE_WAITS_FOR_THE_POST_BEING_SENT_V1 (OCB-122),
 * LOWUI8_COMPOSE_LINK_FILLS_AN_OPEN_NEW_MESSAGE_V1 (OCB-229),
 * LOWUI8_BACK_WAITS_FOR_THE_EDIT_V1 (OCB-231),
 * LOWUI8_MANAGE_ENTRY_KEEPS_A_CHANGE_WAITING_FOR_AUTHORIZATION_V1 (OCB-232),
 * LOWUI8_HIDDEN_UNLOCK_NEEDS_A_SCREEN_LOCK_V1 (OCB-273),
 * LOWUI8_FEED_EDITOR_STAYS_AN_EDITOR_V1 (OCB-185).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LowUi8dComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun feed(path: String, name: String) = CustomFeedSummary(
        path = path,
        displayName = name,
        description = "",
        visibility = "private",
        subredditNames = listOf("android"),
    )

    @Test
    fun anOwnFeedLinkOpenedBeforeTheListLoadsBecomesTheOwnFeed() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val activePath = mutableStateOf<String?>(null)
        val linked = mutableStateOf<CustomFeedSummary?>(null)
        var feeds by mutableStateOf(CustomFeedsUiState(accountStableId = account.stableId))
        composeRule.setContent {
            CustomFeedLinkEffect(
                state = state,
                customFeedsState = feeds,
                activeCustomFeedPathState = activePath,
                linkedCustomFeedState = linked,
                onResolveCustomFeed = { _, _, _ -> },
            )
        }
        composeRule.runOnIdle { state.openRedditTarget(RedditTarget.CustomFeed("Me", "Pics")) }
        composeRule.waitForIdle()

        // The list arrives later, with the path in Reddit's own case.
        composeRule.runOnIdle {
            feeds = CustomFeedsUiState(accountStableId = account.stableId, feeds = listOf(feed("/user/me/m/pics/", "Pics")))
        }
        composeRule.waitForIdle()

        assertEquals("/user/me/m/pics/", activePath.value)
    }

    @Test
    fun aShareDuringASendWaitsUntilTheSendIsDone() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        var pending by mutableStateOf<ShareIntoSubshellLaunch?>(
            ShareIntoSubshellLaunch(1L, SharedPostContent(ComposerPostType.LINK, title = "Shared", linkUrl = "https://example.com/s")),
        )
        var sending by mutableStateOf(true)
        composeRule.setContent {
            ShareIntoSubshellEffect(
                state = state,
                launch = pending,
                onConsumed = { pending = null },
                noticeHost = ViewportErrorHostState(),
                postWriteInProgress = sending,
            )
        }
        composeRule.waitForIdle()
        assertTrue(pending != null)
        assertTrue(state.postComposerDraft.title != "Shared")

        composeRule.runOnIdle { sending = false }
        composeRule.waitForIdle()

        assertNull(pending)
        assertEquals("Shared", state.postComposerDraft.title)
    }

    @Test
    fun aComposeLinkFillsANewMessageAlreadyOpen() {
        ensureWorkManagerInitialized(context)
        var pending by mutableStateOf<NewMessagePrefill?>(null)
        composeRule.setContent {
            SubshellTheme {
                NewMessageScreen(
                    accountStableId = account.stableId,
                    authenticated = true,
                    peopleSearchQuery = "",
                    peopleSearchItems = emptyList(),
                    peopleSearchLoading = false,
                    peopleSearchLoaded = false,
                    directChatStartState = DirectChatStartUiState(),
                    onSearchPeople = {},
                    onStartDirectChat = {},
                    onResetDirectChatStartState = {},
                    onRefreshInbox = {},
                    onBack = {},
                    consumePrefill = { pending.also { pending = null } },
                    prefillPending = pending != null,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { pending = NewMessagePrefill(recipientUsername = "alice", text = "hello there") }
        composeRule.waitForIdle()

        assertNull(pending)
        assertTrue(composeRule.onAllNodesWithText("hello there", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun backWhileTheEditIsSentDoesNotClose() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val post = SampleData.posts.first().copy(id = "t3_e231", author = "me", kind = PostKind.TEXT, body = "old")
        var closed = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SubshellTheme {
                PostEditScreen(
                    post = post,
                    state = state,
                    editState = PostEditUiState(
                        phase = PostEditPhase.SENDING,
                        accountStableId = account.stableId,
                        postFullname = normalizePostEditFullname(post.id),
                    ),
                    onSubmitEdit = {},
                    onAuthorizeEdit = {},
                    onCancelAuthorization = {},
                    onResetEditState = {},
                    onSaved = {},
                    onClose = { closed += 1 },
                )
            }
        }
        composeRule.waitForIdle()

        var fellThrough = false
        composeRule.runOnUiThread {
            dispatcher.onBackPressed()
            // Nothing in the screen took it: the activity would have closed the post.
            fellThrough = !dispatcher.hasEnabledCallbacks()
        }
        composeRule.waitForIdle()

        assertEquals(0, closed)
        assertEquals(false, fellThrough)
    }

    @Test
    fun reenteringManagePostKeepsAChangeWaitingForAuthorization() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val post = SampleData.posts.first().copy(id = "t3_m232", author = "me")
        val target = PostManageTarget(
            accountStableId = account.stableId,
            postFullname = normalizePostEditFullname(post.id),
            postAuthor = post.author,
            community = normalizePostManageCommunity(post.community),
        )
        var resets = 0
        composeRule.setContent {
            SubshellTheme {
                PostManageScreen(
                    post = post,
                    state = state,
                    manageState = PostManageUiState(phase = PostManagePhase.AUTHORIZATION_REQUIRED, target = target),
                    onLoad = {},
                    onMutate = {},
                    onAuthorize = {},
                    onCancelAuthorization = {},
                    onReset = { resets += 1 },
                    onClose = {},
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(0, resets)
    }

    @Test
    fun theHiddenCommunitiesUnlockCannotBeTurnedOnWithoutAScreenLock() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppLock provides AppLockEntryPoints(
                    settings = AppLockSettings(),
                    deviceUnlockAvailable = false,
                    onChange = {},
                    onRequestUnlock = { onResult -> onResult(false) },
                ),
            ) {
                SubshellTheme {
                    SettingsScreen(
                        state = state,
                        backupRepository = repository,
                        onBack = {},
                        initialDestinationName = SettingsDestination.FEEDS.name,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val toggle = isToggleable() and hasText("Require device unlock to view hidden communities", substring = true)
        composeRule.onNode(hasScrollAction()).performScrollToNode(toggle)

        composeRule.onNode(toggle).assertIsNotEnabled()
        assertEquals(false, state.requireDeviceUnlockForHiddenSubscriptions)
    }

    @Test
    fun aFeedGoneFromTheListIsNotCreatedFromItsEditor() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val tech = feed("/user/me/m/tech", "Tech")
        var feeds by mutableStateOf(CustomFeedsUiState(accountStableId = account.stableId, feeds = listOf(tech), contentRevision = 1L))
        val created = mutableListOf<String>()
        composeRule.setContent {
            CommunitiesScreen(
                state = state,
                redditSubscriptionsState = RedditSubscriptionsUiState(accountStableId = account.stableId, loaded = true),
                customFeedsState = feeds,
                onCreateCustomFeed = { request -> created += request.displayName },
            )
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Feeds")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Manage Tech")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit feed").performClick()
        composeRule.waitForIdle()

        // Deleted on another device: the next list no longer has it.
        composeRule.runOnIdle { feeds = feeds.copy(feeds = emptyList(), contentRevision = 2L) }
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithText("Create custom feed").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("This feed no longer exists.").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        assertTrue(created.isEmpty())
    }
}
