package app.subshell.prototype.ui.components

import app.subshell.prototype.model.CommunityModerator
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A6PresentationPolicyTest {
    @Test
    fun editGateRequiresExplicitAllActorNonSelfAndSafeTargetPermissions() {
        val owner = CommunityModerator("owner", listOf("all"))
        val target = CommunityModerator("target", listOf("posts", "mail"))

        assertTrue(
            moderatorTeamCanEditPermissions(
                moderators = listOf(owner, target),
                activeUsername = "owner",
                target = target,
            ),
        )
        assertFalse(
            moderatorTeamCanEditPermissions(
                moderators = listOf(owner, target),
                activeUsername = "owner",
                target = owner,
            ),
        )
        assertFalse(
            moderatorTeamCanEditPermissions(
                moderators = listOf(
                    CommunityModerator("owner", listOf("posts")),
                    target,
                ),
                activeUsername = "owner",
                target = target,
            ),
        )
    }

    @Test
    fun missingOrUnknownPermissionDataIsNeverWrittenBackBlindly() {
        assertFalse(moderatorTeamPermissionSetIsEditable(emptyList()))
        assertFalse(moderatorTeamPermissionSetIsEditable(listOf("posts", "future_permission")))
        assertTrue(moderatorTeamPermissionSetIsEditable(listOf("posts", "mail")))
        assertTrue(moderatorTeamPermissionSetIsEditable(listOf("all")))
    }

    @Test
    fun editorStaysInsideModeratorWorkspaceAndKeepsA5A7OutOfScope() {
        val team = File("src/main/java/app/subshell/prototype/ui/components/ModeratorTeam.kt")
            .readText()
        val app = File("src/main/java/app/subshell/prototype/ui/SubshellApp.kt").readText()

        assertTrue(team.contains("MODERATION02A5A6_PERMISSION_EDITOR_V1"))
        assertTrue(team.contains("Edit permissions"))
        assertTrue(team.contains("Back to moderators"))
        assertTrue(team.contains("Save permissions"))
        assertTrue(team.contains("This does not remove the moderator."))
        assertTrue(team.contains("Moderator permissions"))
        assertTrue(team.contains("Permissions are reported by Reddit."))
        assertTrue(team.contains("Eligible moderators can be edited individually."))
        assertFalse(team.contains("Permissions · Read-only"))
        assertFalse(team.contains("Permissions reported by Reddit are read-only here."))
        if (team.contains("MODERATION02A5A7_REMOVE_MODERATOR_V1")) {
            assertTrue(team.contains("Remove moderator"))
        } else {
            assertFalse(team.contains("Remove moderator"))
        }
        assertFalse(team.contains("ModalBottomSheet("))
        assertTrue(app.contains("onOpenModeratorPermissionUpdate"))
    }
    @Test
    fun moderatorTeamRuntimeUsesOneTopLevelSubshellAppParameter() {
        val app = File("src/main/java/app/subshell/prototype/ui/SubshellApp.kt").readText()
        // The moderator bundles are built in MainActivityEntryPoints.kt.
        val main = File("src/main/java/app/subshell/prototype/MainActivity.kt").readText() +
            File("src/main/java/app/subshell/prototype/MainActivityEntryPoints.kt").readText()

        assertTrue(app.contains("MODERATION02A5A6_MODERATOR_TEAM_RUNTIME_BUNDLE_V1"))
        assertTrue(app.contains("data class ModeratorTeamRuntimeEntryPoints("))
        assertTrue(app.contains("moderatorTeamRuntime: ModeratorTeamRuntimeEntryPoints"))
        assertTrue(main.contains("ModeratorTeamRuntimeEntryPoints("))
        assertFalse(
            app.contains(
                "    moderatorInviteState: app.subshell.prototype.data.account.",
            ),
        )
        assertFalse(app.contains("    moderatorPermissionUpdateState:"))
    }

    @Test
    fun editPermissionActionIsAttachedToItsModeratorCard() {
        val team = File("src/main/java/app/subshell/prototype/ui/components/ModeratorTeam.kt")
            .readText()
        val action = team
            .substringAfter("MODERATION02A5A6_PERMISSION_ACTION_CARD_ATTACHMENT_V1")
            .substringBefore("// MODERATION02A5A5_INVITE_EDITOR_V1")

        assertTrue(action.contains("Surface("))
        assertTrue(action.contains("imageVector = Icons.Rounded.Edit"))
        assertTrue(action.contains(".align(Alignment.Start)"))
        assertFalse(action.contains("modifier = Modifier.align(Alignment.End)"))
    }

}
