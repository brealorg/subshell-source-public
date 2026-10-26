package app.subshell.prototype.ui.components

import app.subshell.prototype.model.CommunityModerator
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A5PresentationPolicyTest {
    @Test
    fun onlyExplicitAllPermissionEnablesClientSideInviteGate() {
        val own = CommunityModerator("owner", listOf("all"))
        val partial = CommunityModerator("owner", listOf("posts", "mail"))
        val omitted = CommunityModerator("owner", emptyList())

        assertTrue(moderatorTeamCanInvite(listOf(own), "owner"))
        assertFalse(moderatorTeamCanInvite(listOf(partial), "owner"))
        assertFalse(moderatorTeamCanInvite(listOf(omitted), "owner"))
        assertFalse(moderatorTeamCanInvite(listOf(own), "someone_else"))
    }

    @Test
    fun inviteEditorReusesPeopleSearchAndExistingSingleSheetBackOwnership() {
        val team = File("src/main/java/app/subshell/prototype/ui/components/ModeratorTeam.kt").readText()
        val tools = File("src/main/java/app/subshell/prototype/ui/components/ModeratorToolsSheet.kt").readText()

        assertTrue(team.contains("LocalModeratorUserManagement.current"))
        assertTrue(team.contains("Search Reddit users"))
        assertTrue(team.contains("ModeratorInviteAvatar"))
        // ISSUE249_IN_SCREEN_PAGES_FOLLOW_THE_BACK_GESTURE_V1: the invite editor
        // owns Back as a predictive page (swallowed while it writes).
        assertTrue(
            team.contains("BackHandler(enabled = showInviteEditor)") ||
                (team.contains("PredictiveBackPage(") && team.contains("enabled = !inviteBusy")),
        )
        assertTrue(team.contains("Back to moderators"))
        assertTrue(team.contains("Invite u/\${pending.username}?"))
        assertTrue(team.contains("Send invitation"))
        assertFalse(team.contains("ModalBottomSheet("))
        // ISSUE54: the team gets the canonical community name.
        assertTrue(
            tools.contains("workspaceContent.moderators(communityName)") ||
                (
                    tools.contains("ISSUE54_CANONICAL_MODERATED_COMMUNITY_V1") &&
                        tools.contains("workspaceContent.moderators(moderatedCommunityName)")
                    ),
        )
    }

    @Test
    fun invitationCopyDoesNotProjectPendingInviteAsActiveModerator() {
        val team = File("src/main/java/app/subshell/prototype/ui/components/ModeratorTeam.kt").readText()
        assertTrue(team.contains("becomes a moderator after accepting the invitation on Reddit"))
        assertTrue(team.contains("Authorization updated. Review the target and permissions"))
        assertTrue(team.contains("state.failureDetails"))
        assertTrue(team.contains("toSafeTechnicalDetail()"))
        assertFalse(team.contains("failureApiError"))
        assertFalse(team.contains("apiError?.message"))
        assertTrue(team.contains("Do not send it again until you verify"))
        assertTrue(team.contains(if (team.contains("MODERATION02A5A6_PERMISSION_EDITOR_V1")) "Moderator permissions" else "Permissions · Read-only"))
        assertTrue(team.contains("Permission details not provided"))
    }
}
