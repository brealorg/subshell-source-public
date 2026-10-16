package app.subshell.prototype.ui.components

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.subshell.prototype.data.account.ModeratorInviteFailureKind
import app.subshell.prototype.data.account.ModeratorInviteUiState
import app.subshell.prototype.data.account.ModeratorPermissionUpdateUiState
import app.subshell.prototype.data.account.ModeratorRemovalUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditModeratorInviteRequest
import app.subshell.prototype.data.account.RedditModeratorPermission
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.data.network.reddit.moderatorInviteFailureDetails
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.model.RedditPeopleSearchItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (moderation02a5a5): only a moderator Reddit reports with All
 * permissions may invite; the editor searches people (at most six results,
 * never the reader or a current moderator), needs at least one permission,
 * and sends only after "Invite u/x?" is answered with Send invitation (Cancel
 * sends nothing); Back closes it; an acknowledged invitation reloads the list
 * from Reddit and does not add the invitee to it; a refusal shows Reddit's
 * reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ModeratorInviteComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = "reddit:me"
    private val community = "subshell"
    private val loads = mutableListOf<Boolean>()
    private val invites = mutableListOf<RedditModeratorInviteRequest>()
    private val searches = mutableListOf<String>()
    private var dismissedInvites = 0
    private var invite by mutableStateOf(ModeratorInviteUiState())
    private var people = RedditPeopleSearchUiState()
    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun show(myPermissions: List<String> = listOf("all")) {
        val search = ModeratorUserManagementEntryPoints(
            peopleSearchState = { people },
            searchPeople = { searches += it },
        )
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            CompositionLocalProvider(LocalModeratorUserManagement provides search) {
                ModeratorTeamContent(
                    accountStableId = account,
                    communityName = community,
                    activeUsername = "me_user",
                    canRead = true,
                    uiState = RedditCommunityInfoUiState(
                        accountStableId = account,
                        communityName = community,
                        snapshot = CommunityInfoSnapshot(
                            communityName = community,
                            moderators = listOf(
                                CommunityModerator("me_user", myPermissions),
                                CommunityModerator("other_mod", listOf("posts")),
                            ),
                        ),
                        loaded = true,
                    ),
                    avatarImageUri = { null },
                    onEnsureIdentity = {},
                    onLoad = { force -> loads += force },
                    inviteState = invite,
                    onOpenInviteContext = {},
                    onInvite = { invites += it },
                    onAuthorizeInvite = {},
                    onDismissInvite = { dismissedInvites += 1 },
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
        }
        composeRule.waitForIdle()
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun scrollTo(matcher: SemanticsMatcher) {
        runCatching { composeRule.onNode(hasScrollAction()).performScrollToNode(matcher) }
        composeRule.waitForIdle()
    }

    private fun click(matcher: SemanticsMatcher) {
        scrollTo(matcher)
        composeRule.onNode(matcher).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private val inviteButton = hasText("Invite moderator") and hasClickAction()

    private fun openEditor() = click(inviteButton)

    private fun type(username: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(username)
        composeRule.waitForIdle()
    }

    @Test
    fun onlyAModeratorWithAllPermissionsMayInvite() {
        show(myPermissions = listOf("posts", "wiki"))

        composeRule.onNode(inviteButton).assertIsNotEnabled()
        assertEquals(1, shown("This account is not reported with All permissions."))
    }

    @Test
    fun theInvitationGoesOutOnlyAfterTheReaderConfirmsIt() {
        show()
        openEditor()
        type("new_mod")

        scrollTo(inviteButton)
        composeRule.onNode(inviteButton).assertIsNotEnabled()
        assertEquals(1, shown("Choose at least one permission or All permissions."))
        click(hasText("Wiki") and hasClickAction())
        click(hasText("Posts") and hasClickAction())

        click(inviteButton)
        assertEquals(1, shown("Invite u/new_mod?"))
        click(hasText("Cancel") and hasClickAction())
        assertEquals(emptyList<RedditModeratorInviteRequest>(), invites)

        click(inviteButton)
        click(hasText("Send invitation") and hasClickAction())
        assertEquals(1, invites.size)
        assertEquals("new_mod", invites.single().username)
        assertEquals(false, invites.single().allPermissions)
        assertEquals(setOf(RedditModeratorPermission.POSTS, RedditModeratorPermission.WIKI), invites.single().permissions)
    }

    @Test
    fun theSearchShowsAtMostSixPeopleAndNeverTheReaderOrACurrentModerator() {
        people = RedditPeopleSearchUiState(
            accountStableId = account,
            searchQuery = "mo",
            loaded = true,
            items = listOf("me_user", "other_mod", "mod_a", "mod_b", "mod_c", "mod_d", "mod_e", "mod_f", "mod_g")
                .map { RedditPeopleSearchItem(username = it, title = it, description = "") },
        )
        show()
        openEditor()
        type("mo")
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()

        assertEquals(listOf("mo"), searches)
        assertEquals(0, shown("u/me_user"))
        assertEquals(0, shown("u/other_mod"))
        assertEquals(6, shown("u/mod_"))
    }

    @Test
    fun backClosesTheEditor() {
        show()
        openEditor()

        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(1, dismissedInvites)
        assertEquals(0, shown("Search Reddit users"))
    }

    @Test
    fun anAcknowledgedInvitationReloadsTheListAndDoesNotAddTheInvitee() {
        show()
        openEditor()
        val forcedBefore = loads.count { it }

        composeRule.runOnIdle {
            invite = ModeratorInviteUiState(
                visible = true,
                accountStableId = account,
                communityName = community,
                completionRevision = 1L,
                lastInvitedUsername = "new_mod",
            )
        }
        composeRule.waitForIdle()

        assertEquals(forcedBefore + 1, loads.count { it })
        assertEquals(1, shown("u/new_mod becomes a moderator after accepting the invitation on Reddit."))
        assertEquals(0, composeRule.onAllNodesWithText("u/new_mod").fetchSemanticsNodes().size)
    }

    @Test
    fun aRefusedInvitationShowsRedditsReason() {
        invite = ModeratorInviteUiState(
            visible = true,
            accountStableId = account,
            communityName = community,
            failureKind = ModeratorInviteFailureKind.HTTP,
            failureDetails = moderatorInviteFailureDetails(
                listOf(JsonArray(listOf(JsonPrimitive("USER_REQUIRED"), JsonPrimitive("please enter a username"), JsonPrimitive("name")))),
            ),
        )
        show()
        openEditor()

        scrollTo(hasText("Reddit rejected the invitation.", substring = true))
        assertEquals(1, shown("please enter a username"))
    }

    // STEP3 (moderation02a5-final-ux-material): a name Reddit's search does not
    // find can still be invited by its exact spelling; the reader's own name
    // and a current moderator's name say why they cannot be invited.
    @Test
    fun aNameTheSearchDoesNotFindCanStillBeInvitedExactly() {
        people = RedditPeopleSearchUiState(accountStableId = account, searchQuery = "new_mod", loaded = true, items = emptyList())
        show()
        openEditor()
        type("new_mod")
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()

        scrollTo(hasText("No profile match", substring = true))
        assertEquals(1, shown("No profile match"))
        click(hasText("Wiki") and hasClickAction())
        click(inviteButton)
        click(hasText("Send invitation") and hasClickAction())

        assertEquals("new_mod", invites.single().username)
    }

    @Test
    fun theReadersOwnNameAndACurrentModeratorCannotBeInvited() {
        show()
        openEditor()
        type("me_user")
        // The reader is on the moderator list here, which is checked first.
        assertEquals(1, shown("You cannot invite your own account") + shown("This account is already in the active moderator list"))
        click(hasText("Wiki") and hasClickAction())
        scrollTo(inviteButton)
        composeRule.onNode(inviteButton).assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("other_mod")
        composeRule.waitForIdle()
        assertEquals(1, shown("This account is already in the active moderator list"))
        scrollTo(inviteButton)
        composeRule.onNode(inviteButton).assertIsNotEnabled()
    }
}
