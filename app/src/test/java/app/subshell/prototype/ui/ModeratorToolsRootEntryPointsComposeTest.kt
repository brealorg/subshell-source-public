package app.subshell.prototype.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.ui.app.CommunityEntryPoints
import app.subshell.prototype.ui.app.ModerationQueueEntryPoints
import app.subshell.prototype.ui.app.ModeratorToolsEntryPoints
import app.subshell.prototype.ui.app.ModeratorToolsRootEntryPoints
import app.subshell.prototype.ui.app.moderatorToolsRootEntryPoints
import app.subshell.prototype.ui.components.ModeratorToolsWorkspaceDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The moderator tools root owners in their own file, hosted alone: the
 * single-sheet workspace navigation opens a root, moves to one child and
 * back, is reset by an account switch, the team editors are dismissed
 * when the workspace leaves Moderators, the team runtimes hear each
 * account, and the entry points are gated and wired through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ModeratorToolsRootEntryPointsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val accounts = localFixtureAccountSummaries(listOf("root", "packetmoss"))
    private val events = mutableListOf<String>()
    private lateinit var root: ModeratorToolsRootEntryPoints

    private fun host(state: SubshellState) {
        composeRule.setContent {
            root = moderatorToolsRootEntryPoints(
                state = state,
                redditCommunity = CommunityEntryPoints(
                    onLoadRedditCommunityDiscovery = { account, query, sort, nsfw, force ->
                        events += "discovery:$account:$query:$sort:$nsfw:$force"
                    },
                ),
                redditModeration = ModerationQueueEntryPoints(),
                moderatorTools = ModeratorToolsEntryPoints(
                    onOpenModeratorLog = { account, community -> events += "log:$account:$community" },
                ),
                moderatorTeamRuntime = ModeratorTeamRuntimeEntryPoints(
                    onDismissModeratorInvite = { events += "dismissInvite" },
                    onDismissModeratorPermissionUpdate = { events += "dismissPermission" },
                    onDismissModeratorRemoval = { events += "dismissRemoval" },
                    onModeratorInviteAccountChanged = { events += "inviteAccount:$it" },
                    onModeratorPermissionUpdateAccountChanged = { events += "permissionAccount:$it" },
                    onModeratorRemovalAccountChanged = { events += "removalAccount:$it" },
                ),
            )
        }
        composeRule.waitForIdle()
    }

    private fun state() = SubshellState(
        initialAccountSessions = accounts,
        initialActiveAccountId = accounts.first().stableId,
    )

    @Test
    fun theWorkspaceOpensARootMovesToOneChildAndBackAndDismisses() {
        host(state())
        val navigation = root.navigation

        composeRule.runOnIdle { navigation.openRoot("kotlin") }
        composeRule.waitForIdle()
        assertEquals("kotlin", root.workspaceCommunity)
        assertTrue(root.navigation.isRootVisible("kotlin"))
        assertFalse(root.navigation.isVisible("android"))

        composeRule.runOnIdle {
            root.navigation.openChild("kotlin", ModeratorToolsWorkspaceDestination.MOD_LOG)
        }
        composeRule.waitForIdle()
        assertEquals(ModeratorToolsWorkspaceDestination.MOD_LOG, root.navigation.destinationFor("kotlin"))
        // Only from the root: a child cannot open another child.
        composeRule.runOnIdle {
            root.navigation.openChild("kotlin", ModeratorToolsWorkspaceDestination.USER_MANAGEMENT)
        }
        composeRule.waitForIdle()
        assertEquals(ModeratorToolsWorkspaceDestination.MOD_LOG, root.navigation.destinationFor("kotlin"))

        composeRule.runOnIdle {
            root.navigation.returnToRoot("kotlin", ModeratorToolsWorkspaceDestination.MOD_LOG)
        }
        composeRule.waitForIdle()
        assertTrue(root.navigation.isRootVisible("kotlin"))

        composeRule.runOnIdle { root.navigation.dismiss("kotlin") }
        composeRule.waitForIdle()
        assertNull(root.workspaceCommunity)
    }

    @Test
    fun anAccountSwitchResetsTheWorkspaceAndReachesTheTeamRuntimes() {
        val state = state()
        host(state)
        assertTrue(events.contains("inviteAccount:${accounts[0].stableId}"))
        assertTrue(events.contains("permissionAccount:${accounts[0].stableId}"))
        assertTrue(events.contains("removalAccount:${accounts[0].stableId}"))

        composeRule.runOnIdle { root.navigation.openRoot("kotlin") }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.switchAccount(accounts[1].stableId) }
        composeRule.waitForIdle()

        assertNull(root.workspaceCommunity)
        assertTrue(events.contains("inviteAccount:${accounts[1].stableId}"))
        assertTrue(events.contains("permissionAccount:${accounts[1].stableId}"))
        assertTrue(events.contains("removalAccount:${accounts[1].stableId}"))
    }

    @Test
    fun leavingModeratorsDismissesTheTeamEditors() {
        host(state())
        events.clear()

        composeRule.runOnIdle { root.navigation.openRoot("kotlin") }
        composeRule.waitForIdle()

        assertTrue(events.contains("dismissInvite"))
        // STEP3 (moderation02a5a6, a5a7): the permission and removal editors close too.
        assertTrue(events.contains("dismissPermission"))
        assertTrue(events.contains("dismissRemoval"))
    }

    @Test
    fun aLocalAccountHasNoModeratorAccessAndTheEntryPointsAreWiredThrough() {
        val state = state()
        host(state)

        assertFalse(root.reports.hasModeratorAccess)
        assertFalse(root.reports.canInspect("kotlin"))
        assertFalse(root.log.canInspect("kotlin"))

        root.log.open("kotlin")
        root.userManagement.searchPeople("alice")

        assertTrue(events.contains("log:${state.activeAccountId}:kotlin"))
        assertTrue(events.contains("discovery:${state.activeAccountId}:alice:null:null:false"))
    }
}
