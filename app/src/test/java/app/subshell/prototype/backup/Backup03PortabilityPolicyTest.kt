package app.subshell.prototype.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup03PortabilityPolicyTest {
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
    fun portabilityMatrixIsExplicitAndSchemaV4HasNotStarted() {
        val policy = repository("docs/backup/subshell-backup03-portability-policy-v1.md").readText()
        val model = source("backup/SettingsBackup.kt")

        assertTrue(policy.contains("BACKUP03A1_PORTABILITY_POLICY_V1"))
        assertTrue(policy.contains("CLASSIFICATION=PORTABLE"))
        assertTrue(policy.contains("CLASSIFICATION=PRIVACY_SENSITIVE_OPT_IN"))
        assertTrue(policy.contains("CLASSIFICATION=DEVICE_LOCAL"))
        assertTrue(policy.contains("CLASSIFICATION=NEVER_BACKUP"))
        assertTrue(
            policy.contains("SCHEMA_V4_STATUS=NOT_STARTED_BY_BACKUP03A1") ||
                policy.contains("SCHEMA_V4_STATUS=IN_PROGRESS_BACKUP03C1_CODEC_STORE_ONLY") ||
                policy.contains("SCHEMA_V4_STATUS=RESOLVED_BACKUP03C") ||
                policy.contains("SCHEMA_V5_STATUS=RESOLVED_BACKUP04A1")
        )

        listOf(
            "SubshellSettings",
            "joinedCommunityIdsByAccount",
            "favoriteCommunityNames",
            "favoriteCustomFeedPaths",
            "hiddenCommunityNames",
            "communityMutes",
            "postComposerDrafts",
            "moderatorSavedResponses",
            "recentCommunityVisits",
            "recentSearches",
            "searchIndexEntries",
            "AccountPostActions",
            "savedSearches",
            "watchedCommunities",
        ).forEach { symbol ->
            assertTrue("Missing policy symbol: $symbol", policy.contains(symbol))
        }

        assertTrue(
            model.contains(
                "val joinedCommunityIdsByAccount: Map<String, Set<String>>? = null",
            ),
        )
        val schemaV4Started =
            model.contains("BACKUP03C1_FAVORITES_SCHEMA_V4_CODEC_STORE_V1")
        val schemaV7Started =
            model.contains("BACKUP04A3_POST_COMPOSER_DRAFTS_SCHEMA_V7_OPT_IN_V1")
        val schemaV5Started =
            model.contains("BACKUP04A1_HIDDEN_COMMUNITIES_SCHEMA_V5_OPT_IN_V1")
        val schemaV8Started =
            model.contains("BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1")
        val schemaV9Started =
            model.contains("BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1")
        val schemaV10Started =
            model.contains("ISSUE206_SAVED_SEARCHES_SCHEMA_V10_V1")
        val schemaV11Started =
            model.contains("ISSUE239_WATCHED_COMMUNITIES_SCHEMA_V11_V1")
        if (schemaV11Started) {
            assertTrue(model.contains("const val SETTINGS_BACKUP_SCHEMA_VERSION = 11"))
            assertTrue(
                model.contains(
                    "val watchedCommunityNamesByAccount: Map<String, Set<String>>? = null",
                ),
            )
            assertTrue(
                model.contains(
                    "val savedSearchesByAccount: Map<String, List<SavedSearch>>? = null",
                ),
            )
            assertTrue(policy.contains("SCHEMA_V11_STATUS=RESOLVED_ISSUE239"))
        } else if (schemaV10Started) {
            assertTrue(model.contains("const val SETTINGS_BACKUP_SCHEMA_VERSION = 10"))
            assertTrue(
                model.contains(
                    "val savedSearchesByAccount: Map<String, List<SavedSearch>>? = null",
                ),
            )
            assertTrue(
                model.contains(
                    "val userTagsByAccount: Map<String, Map<String, PortableUserTag>>? = null",
                ),
            )
            assertTrue(policy.contains("SCHEMA_V10_STATUS=RESOLVED_ISSUE206"))
        } else if (schemaV9Started) {
            assertTrue(model.contains("const val SETTINGS_BACKUP_SCHEMA_VERSION = 9"))
            assertTrue(
                model.contains(
                    "val userTagsByAccount: Map<String, Map<String, PortableUserTag>>? = null",
                ),
            )
            assertTrue(
                model.contains(
                    "val moderatorSavedResponses: List<PortableModeratorSavedResponse>? = null",
                ),
            )
        } else if (schemaV8Started) {
            assertTrue(model.contains("const val SETTINGS_BACKUP_SCHEMA_VERSION = 8"))
            assertTrue(
                model.contains(
                    "val moderatorSavedResponses: List<PortableModeratorSavedResponse>? = null",
                ),
            )
            assertTrue(
                model.contains(
                    "val postComposerDraftsByAccount: Map<String, List<PortablePostComposerDraft>>? = null",
                ),
            )
        } else if (schemaV7Started) {
            assertTrue(model.contains("const val SETTINGS_BACKUP_SCHEMA_VERSION = 7"))
            assertTrue(
                model.contains(
                    "val postComposerDraftsByAccount: Map<String, List<PortablePostComposerDraft>>? = null",
                ),
            )
        } else if (schemaV5Started) {
            assertTrue(
                model.contains("const val SETTINGS_BACKUP_SCHEMA_VERSION = 5") ||
                    model.contains("const val SETTINGS_BACKUP_SCHEMA_VERSION = 6")
            )
            assertTrue(
                model.contains(
                    "val favoriteCommunityNamesByAccount: Map<String, Set<String>>? = null",
                ),
            )
            assertTrue(
                model.contains(
                    "val favoriteCustomFeedPathsByAccount: Map<String, Set<String>>? = null",
                ),
            )
            assertTrue(
                model.contains(
                    "val hiddenCommunityNamesByAccount: Map<String, Set<String>>? = null",
                ),
            )
        } else if (schemaV4Started) {
            assertTrue(model.contains("const val SETTINGS_BACKUP_SCHEMA_VERSION = 4"))
            assertTrue(
                model.contains(
                    "val favoriteCommunityNamesByAccount: Map<String, Set<String>>? = null",
                ),
            )
            assertTrue(
                model.contains(
                    "val favoriteCustomFeedPathsByAccount: Map<String, Set<String>>? = null",
                ),
            )
        } else {
            assertTrue(model.contains("const val SETTINGS_BACKUP_SCHEMA_VERSION = 3"))
            assertFalse(model.contains("SETTINGS_BACKUP_SCHEMA_VERSION = 4"))
        }
    }

    @Test
    fun authCacheHistoryAndMixedActionStateRemainOutsideBackupPackage() {
        val backupSources = repository("app/src/main/java/app/subshell/prototype/backup")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        listOf(
            "StoredOAuthSession",
            "accessToken",
            "refreshToken",
            "recentCommunityVisits",
            "SearchIndexEntry",
            "AccountPostActions",
        ).forEach { forbidden ->
            assertFalse(
                "Backup package must not depend on forbidden state: $forbidden",
                backupSources.contains(forbidden),
            )
        }

        val authStorage = source("data/auth/AuthStorage.kt")
        assertTrue(authStorage.contains("noBackupFilesDir"))
    }

    @Test
    fun androidSystemBackupBoundaryRemainsSuccessorAware() {
        val policy = repository("docs/backup/subshell-backup03-portability-policy-v1.md").readText()
        val legacyRules = repository("app/src/main/res/xml/backup_rules.xml").readText()
        val extractionRules =
            repository("app/src/main/res/xml/data_extraction_rules.xml").readText()
        val manifest = repository("app/src/main/AndroidManifest.xml").readText()

        val backup03bResolved =
            policy.contains("BACKUP03B_ANDROID_SYSTEM_BACKUP_DENY_ALL_V1")

        if (backup03bResolved) {
            assertTrue(policy.contains("SYSTEM_BACKUP_STATUS=RESOLVED_BACKUP03B"))
            assertTrue(manifest.contains("android:allowBackup=\"false\""))
            assertTrue(legacyRules.contains("""<exclude domain="sharedpref" path="." />"""))
            assertTrue(extractionRules.contains("<cloud-backup>"))
            assertTrue(extractionRules.contains("<device-transfer>"))
            assertTrue(extractionRules.contains("""<exclude domain="sharedpref" path="." />"""))
        } else {
            assertTrue(policy.contains("SYSTEM_BACKUP_STATUS=OPEN_BACKUP03B"))
            assertTrue(manifest.contains("android:allowBackup=\"true\""))
            listOf(legacyRules, extractionRules).forEach { rules ->
                assertTrue(rules.contains("subshell_device_preferences.xml"))
                assertTrue(rules.contains("subshell-offline-cache.db"))
                assertFalse(rules.contains("subshell_preferences.xml"))
            }
        }
    }

    @Test
    fun schemaV4CandidateIsNarrowAndPreservesAbsentVsExplicitEmptySemantics() {
        val policy = repository("docs/backup/subshell-backup03-portability-policy-v1.md").readText()

        assertTrue(policy.contains("favorite communities, account-scoped by stable account ID"))
        assertTrue(policy.contains("favorite custom feeds, account-scoped by stable account ID"))
        assertTrue(policy.contains("`null` / absent module = legacy backup"))
        assertTrue(policy.contains("authoritative empty state"))
        assertTrue(policy.contains("do not remap one account's row to another account"))
        assertTrue(
            policy.contains(
                "do not add privacy-sensitive, device-local, auth, or cache state in the same change",
            ),
        )
    }

    private fun repository(relative: String): File =
        File(repositoryRoot, relative).also { file ->
            check(file.isFile || file.isDirectory) { "Missing repository fixture: ${file.path}" }
        }

    private fun source(relative: String): String =
        repository("app/src/main/java/app/subshell/prototype/$relative").readText()
}
