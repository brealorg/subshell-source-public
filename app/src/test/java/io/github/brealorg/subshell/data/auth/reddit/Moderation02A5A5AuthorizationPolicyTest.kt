package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.auth.AuthCapabilityAuthorizationOwner
import io.github.brealorg.subshell.harness.SUBSHELL_PACKAGE_DIR
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A5AuthorizationPolicyTest {
    @Test
    fun moderatorInvitesUseDedicatedModothersOwnerAndSignInAsksForIt() {
        assertEquals("moderation_team", AuthCapabilityAuthorizationOwner.MODERATION_TEAM.wireKey)
        assertEquals(
            AuthCapabilityAuthorizationOwner.MODERATION_TEAM,
            AuthCapabilityAuthorizationOwner.fromWireKey("moderation_team"),
        )
        assertTrue(
            RedditOAuth.scopesFor(RedditOAuthCapability.MODERATION_TEAM).contains("modothers"),
        )
        // ISSUE237: sign-in asks for it too; the capability owner stays the fallback.
        assertTrue(RedditOAuth.dailyDriverScopes.contains("modothers"))

        val oauth = File("src/main/java/${SUBSHELL_PACKAGE_DIR}/data/auth/reddit/RedditOAuth.kt").readText()
        val owner = File("src/main/java/${SUBSHELL_PACKAGE_DIR}/data/auth/AuthCapabilityAuthorizer.kt").readText()
        // The moderation parts are built in ModerationRuntimeParts since R7.
        val runtime = File("src/main/java/${SUBSHELL_PACKAGE_DIR}/auth/AuthApplicationRuntime.kt").readText() +
            File("src/main/java/${SUBSHELL_PACKAGE_DIR}/auth/ModerationRuntimeParts.kt").readText()
        val vm = File("src/main/java/${SUBSHELL_PACKAGE_DIR}/auth/AuthConnectViewModel.kt").readText()
        val writer = File("src/main/java/${SUBSHELL_PACKAGE_DIR}/data/auth/reddit/RedditModeratorInvitationWriter.kt").readText()

        assertTrue(oauth.contains("liveModeratorTeamScopes = coreReadScopes + \"modothers\""))
        assertTrue(oauth.contains("MODERATION_TEAM -> liveModeratorTeamScopes"))
        assertTrue(oauth.contains("liveModeratorUserManagementScopes = coreReadScopes + \"modcontributors\""))
        val daily = oauth.substringAfter("val dailyDriverScopes = setOf(").substringBefore("    )")
        assertTrue(daily.contains("\"modothers\""))
        assertTrue(owner.contains("MODERATION_TEAM(\"moderation_team\")"))
        assertTrue(runtime.contains("owner = AuthCapabilityAuthorizationOwner.MODERATION_TEAM"))
        assertTrue(runtime.contains("SubshellNetworkTransport.nonRetryingWriteCallFactory"))
        assertTrue(vm.contains("AuthCapabilityAuthorizationOwner.MODERATION_TEAM ->"))
        assertTrue(writer.contains("AuthenticatedRedditModeratorInviteResult.AuthorizationRequired"))
    }
}
