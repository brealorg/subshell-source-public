package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A5AuthorizationPolicyTest {
    @Test
    fun moderatorInvitesUseDedicatedModothersOwnerWithoutExpandingDailyDriver() {
        assertEquals("moderation_team", AuthCapabilityAuthorizationOwner.MODERATION_TEAM.wireKey)
        assertEquals(
            AuthCapabilityAuthorizationOwner.MODERATION_TEAM,
            AuthCapabilityAuthorizationOwner.fromWireKey("moderation_team"),
        )
        assertTrue(
            RedditOAuth.scopesFor(RedditOAuthCapability.MODERATION_TEAM).contains("modothers"),
        )
        assertFalse(RedditOAuth.dailyDriverScopes.contains("modothers"))

        val oauth = File("src/main/java/app/subshell/prototype/data/auth/reddit/RedditOAuth.kt").readText()
        val owner = File("src/main/java/app/subshell/prototype/data/auth/AuthCapabilityAuthorizer.kt").readText()
        // The moderation parts are built in ModerationRuntimeParts since R7.
        val runtime = File("src/main/java/app/subshell/prototype/auth/AuthApplicationRuntime.kt").readText() +
            File("src/main/java/app/subshell/prototype/auth/ModerationRuntimeParts.kt").readText()
        val vm = File("src/main/java/app/subshell/prototype/auth/AuthConnectViewModel.kt").readText()
        val writer = File("src/main/java/app/subshell/prototype/data/auth/reddit/RedditModeratorInvitationWriter.kt").readText()

        assertTrue(oauth.contains("liveModeratorTeamScopes = coreReadScopes + \"modothers\""))
        assertTrue(oauth.contains("MODERATION_TEAM -> liveModeratorTeamScopes"))
        assertTrue(oauth.contains("liveModeratorUserManagementScopes = coreReadScopes + \"modcontributors\""))
        val daily = oauth.substringAfter("val dailyDriverScopes = setOf(").substringBefore("    )")
        assertFalse(daily.contains("modothers"))
        assertTrue(owner.contains("MODERATION_TEAM(\"moderation_team\")"))
        assertTrue(runtime.contains("owner = AuthCapabilityAuthorizationOwner.MODERATION_TEAM"))
        assertTrue(runtime.contains("SubshellNetworkTransport.nonRetryingWriteCallFactory"))
        assertTrue(vm.contains("AuthCapabilityAuthorizationOwner.MODERATION_TEAM ->"))
        assertTrue(writer.contains("AuthenticatedRedditModeratorInviteResult.AuthorizationRequired"))
    }
}
