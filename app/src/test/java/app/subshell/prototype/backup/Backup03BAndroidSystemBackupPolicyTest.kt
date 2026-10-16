package app.subshell.prototype.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup03BAndroidSystemBackupPolicyTest {
    private val repositoryRoot: File by lazy {
        val userDir = System.getProperty("user.dir")
            ?: error("user.dir is unavailable")
        val start = File(userDir).canonicalFile
        generateSequence(start) { current -> current.parentFile }
            .firstOrNull { candidate ->
                File(candidate, "app/src/main/java").isDirectory &&
                    File(candidate, "tools").isDirectory
            }
            ?: error("Could not resolve repository root from ${start.path}")
    }

    @Test
    fun manifestDisablesPlatformBackupButKeepsExplicitRuleResources() {
        val manifest = repository("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertFalse(manifest.contains("""android:allowBackup="true""""))
        assertTrue(manifest.contains("""android:fullBackupContent="@xml/backup_rules""""))
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
    }

    @Test
    fun legacyAutoBackupDeniesEverySupportedDomainRoot() {
        val rules = repository("app/src/main/res/xml/backup_rules.xml").readText()

        assertTrue(rules.contains("BACKUP03B_ANDROID_SYSTEM_BACKUP_DENY_ALL_V1"))
        assertFalse(rules.contains("<include "))

        allBackupDomains.forEach { domain ->
            assertEquals(
                "Legacy rule must deny $domain root exactly once",
                1,
                rules.lineSequence().count {
                    it.trim() == """<exclude domain="$domain" path="." />"""
                },
            )
        }

        assertHistoricalPredecessorFences(rules)
    }

    @Test
    fun android12PlusCloudAndDeviceTransferEachDenyEveryDomainRoot() {
        val rules = repository("app/src/main/res/xml/data_extraction_rules.xml").readText()
        val cloud = section(rules, "cloud-backup")
        val transfer = section(rules, "device-transfer")

        assertTrue(rules.contains("BACKUP03B_ANDROID_SYSTEM_BACKUP_DENY_ALL_V1"))
        assertFalse(rules.contains("<include "))

        listOf("cloud-backup" to cloud, "device-transfer" to transfer).forEach { (name, body) ->
            allBackupDomains.forEach { domain ->
                assertEquals(
                    "$name must deny $domain root exactly once",
                    1,
                    body.lineSequence().count {
                        it.trim() == """<exclude domain="$domain" path="." />"""
                    },
                )
            }
            assertHistoricalPredecessorFences(body)
        }
    }

    @Test
    fun portabilityPolicyClosesBackup03bAndAllowsSuccessorSchemaEvolution() {
        val policy = repository("docs/backup/subshell-backup03-portability-policy-v1.md").readText()
        val backupModel =
            repository("app/src/main/java/app/subshell/prototype/backup/SettingsBackup.kt").readText()

        assertTrue(policy.contains("BACKUP03B_ANDROID_SYSTEM_BACKUP_DENY_ALL_V1"))
        assertTrue(policy.contains("SYSTEM_BACKUP_STATUS=RESOLVED_BACKUP03B"))
        assertTrue(
            policy.contains(
                "Subshell's user-controlled Backup & Restore is the only supported portability mechanism.",
            ),
        )
        assertTrue(policy.contains("`subshell_preferences.xml`"))
        assertTrue(policy.contains("`subshell_backup_preferences.xml`"))
        assertTrue(policy.contains("`subshell_external_poll_handoff.xml`"))
        assertTrue(policy.contains("`filesDir/settings_snapshots`"))
        assertTrue(policy.contains("connected user-selected backup folder"))

        if (backupModel.contains("BACKUP03C1_FAVORITES_SCHEMA_V4_CODEC_STORE_V1")) {
            assertTrue(backupModel.contains("const val SETTINGS_BACKUP_SCHEMA_VERSION = $SETTINGS_BACKUP_SCHEMA_VERSION"))
        } else {
            assertTrue(backupModel.contains("const val SETTINGS_BACKUP_SCHEMA_VERSION = 3"))
            assertFalse(backupModel.contains("SETTINGS_BACKUP_SCHEMA_VERSION = 4"))
        }
    }

    private fun assertHistoricalPredecessorFences(rules: String) {
        assertTrue(
            rules.contains(
                """<exclude domain="sharedpref" path="subshell_device_preferences.xml" />""",
            ),
        )
        assertTrue(
            rules.contains(
                """<exclude domain="database" path="subshell-offline-cache.db" />""",
            ),
        )
        assertTrue(
            rules.contains(
                """<exclude domain="database" path="subshell-offline-cache.db-shm" />""",
            ),
        )
        assertTrue(
            rules.contains(
                """<exclude domain="database" path="subshell-offline-cache.db-wal" />""",
            ),
        )
    }

    private fun section(xml: String, tag: String): String =
        xml.substringAfter("<$tag>")
            .substringBefore("</$tag>")
            .also {
                check(it != xml) { "Missing <$tag> section" }
            }

    private fun repository(relative: String): File =
        File(repositoryRoot, relative).also { file ->
            check(file.isFile) { "Missing repository fixture: ${file.path}" }
        }

    companion object {
        private val allBackupDomains = listOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )
    }
}
