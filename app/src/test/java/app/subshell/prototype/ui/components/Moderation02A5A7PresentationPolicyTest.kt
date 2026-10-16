package app.subshell.prototype.ui.components

import app.subshell.prototype.model.CommunityModerator
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A7PresentationPolicyTest {
    @Test
    fun removalGateRequiresExplicitAllActorAndNeverOffersSelfRemoval() {
        val owner = CommunityModerator("owner", listOf("all"))
        val target = CommunityModerator("target", listOf("posts", "mail"))

        assertTrue(
            moderatorTeamCanRemove(
                moderators = listOf(owner, target),
                activeUsername = "owner",
                target = target,
            ),
        )
        assertFalse(
            moderatorTeamCanRemove(
                moderators = listOf(owner, target),
                activeUsername = "owner",
                target = owner,
            ),
        )
        assertFalse(
            moderatorTeamCanRemove(
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
    fun unknownOrMissingTargetPermissionDetailsDoNotBlockRelationshipRemoval() {
        val owner = CommunityModerator("owner", listOf("all"))
        val omitted = CommunityModerator("omitted", emptyList())
        val future = CommunityModerator("future", listOf("posts", "future_permission"))

        assertTrue(moderatorTeamCanRemove(listOf(owner, omitted), "owner", omitted))
        assertTrue(moderatorTeamCanRemove(listOf(owner, future), "owner", future))
    }

    @Test
    fun removalStaysInModeratorWorkspaceAndRequiresDestructiveConfirmation() {
        val team = File("src/main/java/app/subshell/prototype/ui/components/ModeratorTeam.kt")
            .readText()

        assertTrue(team.contains("MODERATION02A5A7_REMOVE_MODERATOR_V1"))
        assertTrue(team.contains("Remove moderator"))
        assertTrue(team.contains("Remove u/${'$'}{pending.username}?"))
        assertTrue(team.contains("Re-adding them requires a new invitation."))
        assertTrue(team.contains("Reddit decides whether "))
        assertTrue(team.contains("this moderator can be removed based on the moderator hierarchy."))
        assertTrue(team.contains("Back to moderators"))
        assertTrue(team.contains("Authorization updated. Review the target"))
        assertFalse(team.contains("ModalBottomSheet("))
    }

    @Test
    fun removalUsesExistingModeratorTeamRuntimeBundle() {
        val app = File("src/main/java/app/subshell/prototype/ui/SubshellApp.kt").readText()
        // The moderator bundles are built in MainActivityEntryPoints.kt.
        val main = File("src/main/java/app/subshell/prototype/MainActivity.kt").readText() +
            File("src/main/java/app/subshell/prototype/MainActivityEntryPoints.kt").readText()

        assertTrue(app.contains("data class ModeratorTeamRuntimeEntryPoints("))
        assertTrue(app.contains("moderatorRemovalState:"))
        assertTrue(app.contains("onRemoveModerator:"))
        assertTrue(main.contains("moderatorRemovalState = moderatorRemovalState"))
        assertFalse(
            app.contains(
                "    moderatorRemovalState: app.subshell.prototype.data.account." +
                    "ModeratorRemovalUiState",
            ),
        )
    }

    @Test
    fun acknowledgedRemovalRefreshesInsteadOfOptimisticallyFilteringTheRoster() {
        val team = File("src/main/java/app/subshell/prototype/ui/components/ModeratorTeam.kt")
            .readText()
        val completionSlice = team
            .substringAfter("LaunchedEffect(removalState.completionRevision, removedUsername)")
            .substringBefore("if (showInviteEditor)")
        val successSlice = team
            .substringAfter("removedUsername?.let { username ->")
            .substringBefore("if (projection.ready && projection.moderators.isNotEmpty())")
        val removalActionSlice = team
            .substringAfter("MODERATION02A5A7_REMOVE_MODERATOR_V1")
            .substringBefore("// MODERATION02A5A5_INVITE_EDITOR_V1")

        assertTrue(completionSlice.contains("removalEditorUsername = null"))
        assertTrue(completionSlice.contains("onLoad(true)"))
        assertTrue(successSlice.contains("Moderator removed"))
        assertTrue(successSlice.contains("Reddit acknowledged removal"))
        assertFalse(removalActionSlice.contains("filterNot"))
        assertFalse(team.contains("projection.moderators.filterNot"))
    }
}
