package app.subshell.prototype.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup04A1HiddenCommunitiesOptInPolicyTest {
    private val repositoryRoot: File by lazy {
        val userDir = System.getProperty("user.dir") ?: error("user.dir unavailable")
        generateSequence(File(userDir).canonicalFile) { it.parentFile }
            .first { candidate ->
                File(candidate, "app/src/main/java").isDirectory &&
                    File(candidate, "tools").isDirectory
            }
    }

    @Test
    fun deviceLocalOptInDefaultsOffAndIsNotPartOfSnapshotModel() {
        val repository = source("backup/SettingsBackupRepository.kt")
        val model = source("backup/SettingsBackup.kt")

        assertTrue(repository.contains("BACKUP04A1_DEVICE_LOCAL_PRIVACY_OPT_IN_V1"))
        assertTrue(
            repository.contains(
                "preferences.getBoolean(KEY_HIDDEN_COMMUNITIES_BACKUP, false)",
            ),
        )
        assertTrue(repository.contains("setHiddenCommunitiesBackupEnabled"))
        assertTrue(repository.contains("privacy_hidden_communities_backup"))
        assertFalse(model.contains("privacy_hidden_communities_backup"))
    }

    @Test
    fun repositoryOwnsNewSnapshotSerializationFence() {
        val repository = source("backup/SettingsBackupRepository.kt")

        assertTrue(repository.contains("hiddenCommunitiesBackupEnabled()"))
        assertTrue(
            repository.contains(
                "hiddenCommunityNamesByAccount.takeIf { hiddenCommunitiesBackupEnabled() }",
            ),
        )
        assertTrue(
            repository.contains(
                "currentHiddenCommunityNamesByAccount.takeIf {",
            ),
        )
    }

    @Test
    fun restoreRequiresSameOptInAndPreviewDisclosesPrivacySensitivity() {
        val screen = source("ui/screens/BackupRestoreScreen.kt")

        assertTrue(screen.contains("Include hidden communities in backup & restore"))
        assertTrue(screen.contains("Hidden communities · privacy-sensitive"))
        assertTrue(screen.contains("includeHiddenCommunities"))
        assertTrue(
            screen.contains(
                "target.hiddenCommunityNamesByAccount.takeIf {",
            ),
        )
        assertTrue(
            screen.contains(
                "privacy-sensitive backup & restore is disabled on this device",
            ),
        )
    }

    @Test
    fun normalDeviceUnlockPreferenceDoesNotEnableBackupOptIn() {
        val state = source("ui/SubshellState.kt")
        val screen = source("ui/screens/BackupRestoreScreen.kt")

        assertTrue(state.contains("REQUIRE_DEVICE_UNLOCK_HIDDEN_SUBSCRIPTIONS"))
        assertTrue(state.contains("captureHiddenCommunityNamesByAccount"))
        assertFalse(
            screen.contains(
                "requireDeviceUnlockForHiddenSubscriptions && includeHiddenCommunities",
            ),
        )
    }

    @Test
    fun policyFreezesSchemaFiveAsHiddenOnly() {
        val policy = repository("docs/backup/subshell-backup03-portability-policy-v1.md").readText()

        assertTrue(policy.contains("BACKUP04A1_HIDDEN_COMMUNITIES_SCHEMA_V5_OPT_IN_V1"))
        assertTrue(policy.contains("SCHEMA_V5_STATUS=RESOLVED_BACKUP04A1"))
        assertTrue(policy.contains("device-local Backup & Restore preference defaults off"))
        assertTrue(policy.contains("stable account IDs remain opaque native ownership keys"))
        assertTrue(policy.contains("Timed community mutes, drafts, post hidden IDs"))
    }

    private fun source(relative: String): String =
        repository("app/src/main/java/app/subshell/prototype/$relative").readText()

    private fun repository(relative: String): File =
        File(repositoryRoot, relative).also { file ->
            check(file.exists()) { "Missing repository fixture: ${file.path}" }
        }
}
