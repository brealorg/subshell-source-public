package app.subshell.prototype.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup04A2CommunityMutesOptInPolicyTest {
    private val repositoryRoot = File(requireNotNull(System.getProperty("user.dir"))).let { appDir ->
        appDir.parentFile ?: appDir
    }

    @Test
    fun repositoryOwnsIndependentDefaultOffCommunityMuteOptIn() {
        val source = repository(
            "app/src/main/java/app/subshell/prototype/backup/SettingsBackupRepository.kt",
        ).readText()

        assertTrue(source.contains("BACKUP04A2_COMMUNITY_MUTES_DEVICE_LOCAL_PRIVACY_OPT_IN_V1"))
        assertTrue(source.contains("preferences.getBoolean(KEY_COMMUNITY_MUTES_BACKUP, false)"))
        assertTrue(source.contains("communityMutesByAccount.takeIf { communityMutesBackupEnabled() }"))
        assertTrue(source.contains("currentCommunityMutesByAccount.takeIf"))
        assertFalse(source.contains("communityMutesByAccount.takeIf { hiddenCommunitiesBackupEnabled() }"))
    }

    @Test
    fun uiExposesSeparatePrivacySwitchAndRestoreDisclosure() {
        val screen = repository(
            "app/src/main/java/app/subshell/prototype/ui/screens/BackupRestoreScreen.kt",
        ).readText()

        assertTrue(screen.contains("includeHiddenCommunities"))
        assertTrue(screen.contains("includeCommunityMutes"))
        assertTrue(screen.contains("title = \"Muted communities\""))
        assertTrue(screen.contains("Timed mutes keep their end time"))
        assertTrue(screen.contains("Muted communities · privacy-sensitive"))
        assertTrue(screen.contains("target.communityMutesByAccount.takeIf"))
        assertTrue(screen.contains("includeCommunityMutes"))
    }

    @Test
    fun policyFreezesSchemaSixAbsoluteExpiryAndIndependentOptIn() {
        val policy = repository("docs/backup/subshell-backup03-portability-policy-v1.md").readText()

        assertTrue(policy.contains("BACKUP04A2_COMMUNITY_MUTES_SCHEMA_V6_OPT_IN_V1"))
        assertTrue(policy.contains("SCHEMA_V6_STATUS=RESOLVED_BACKUP04A2"))
        assertTrue(policy.contains("absolute wall-clock epoch milliseconds"))
        assertTrue(policy.contains("drops timed entries already expired"))
        assertTrue(policy.contains("performs no clock-skew compensation"))
        assertTrue(policy.contains("enabling hidden-community backup does not enable muted-community backup"))
        assertTrue(policy.contains("muted_community_direct_view_mode"))
    }

    @Test
    fun stateKeepsMuteRestoreInsideSingleDurableTransactionBeforeProjection() {
        // The persistence wiring lives in RememberSubshellState.kt.
        val state = repository(
            "app/src/main/java/app/subshell/prototype/ui/SubshellState.kt",
        ).readText() + repository(
            "app/src/main/java/app/subshell/prototype/ui/RememberSubshellState.kt",
        ).readText()

        assertTrue(state.contains("BACKUP04A2_PRIVACY_SENSITIVE_SIXTH_MODULE_POST_COMMIT_V1"))
        assertTrue(state.contains("communityMuteEntries?.let { restored ->"))
        assertTrue(state.contains("check(editor.commit())"))
        val restoreStart = state.indexOf("fun restoreBackupState(")
        val transactionCall = state.indexOf("persistBackupRestoreState(", restoreStart)
        val projectionCall = state.indexOf("applyRestoredCommunityMutesToLiveState(", restoreStart)
        assertTrue(restoreStart >= 0)
        assertTrue(transactionCall > restoreStart)
        assertTrue(projectionCall > transactionCall)
    }

    private fun repository(relative: String): File =
        File(repositoryRoot, relative).also { file ->
            check(file.exists()) { "Missing repository fixture: ${file.path}" }
        }
}
