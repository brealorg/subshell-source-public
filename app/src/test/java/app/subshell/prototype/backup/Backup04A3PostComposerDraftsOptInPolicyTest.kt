package app.subshell.prototype.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup04A3PostComposerDraftsOptInPolicyTest {
    private val repositoryRoot = File(requireNotNull(System.getProperty("user.dir"))).let { appDir ->
        appDir.parentFile ?: appDir
    }

    @Test
    fun repositoryOwnsThirdIndependentDefaultOffPrivacyOptIn() {
        val source = repository(
            "app/src/main/java/app/subshell/prototype/backup/SettingsBackupRepository.kt",
        ).readText()

        assertTrue(source.contains("BACKUP04A3_POST_COMPOSER_DRAFTS_DEVICE_LOCAL_PRIVACY_OPT_IN_V1"))
        assertTrue(source.contains("preferences.getBoolean(KEY_POST_COMPOSER_DRAFTS_BACKUP, false)"))
        assertTrue(source.contains("postComposerDraftsByAccount.takeIf { postComposerDraftsBackupEnabled() }"))
        assertFalse(source.contains("postComposerDraftsByAccount.takeIf { communityMutesBackupEnabled() }"))
        assertFalse(source.contains("postComposerDraftsByAccount.takeIf { hiddenCommunitiesBackupEnabled() }"))
    }

    @Test
    fun uiStatesUnpublishedTextLinkOnlyAndMediaDeviceLocalBoundary() {
        val screen = repository(
            "app/src/main/java/app/subshell/prototype/ui/screens/BackupRestoreScreen.kt",
        ).readText()

        assertTrue(screen.contains("Include post drafts in backup & restore"))
        assertTrue(screen.contains("unpublished text and link post drafts"))
        assertTrue(screen.contains("Media drafts and local attachments stay only on this device"))
        assertTrue(screen.contains("Post drafts · privacy-sensitive"))
        assertTrue(screen.contains("target.postComposerDraftsByAccount.takeIf"))
    }

    @Test
    fun policyFreezesSubsetAwareSchemaSevenSemantics() {
        val policy = repository("docs/backup/subshell-backup03-portability-policy-v1.md").readText()

        assertTrue(policy.contains("BACKUP04A3_POST_COMPOSER_DRAFTS_SCHEMA_V7_OPT_IN_V1"))
        assertTrue(policy.contains("SCHEMA_V7_STATUS=RESOLVED_BACKUP04A3"))
        assertTrue(policy.contains("only post composer drafts whose type is `TEXT` or `LINK`"))
        assertTrue(policy.contains("media post drafts"))
        assertTrue(policy.contains("null/blank account owner"))
        assertTrue(policy.contains("present empty module removes only portable text/link drafts"))
        assertTrue(policy.contains("same existing"))
        assertTrue(policy.contains("SharedPreferences.Editor.commit()"))
    }

    @Test
    fun productionRestoreStagesNativeDraftJsonBeforeSingleCommitAndProjectsAfterward() {
        // The persistence wiring lives in RememberSubshellState.kt.
        val state = repository(
            "app/src/main/java/app/subshell/prototype/ui/SubshellState.kt",
        ).readText() + repository(
            "app/src/main/java/app/subshell/prototype/ui/RememberSubshellState.kt",
        ).readText()
        val draftStore = repository(
            "app/src/main/java/app/subshell/prototype/data/drafts/ComposerDraftStore.kt",
        ).readText()

        assertTrue(state.contains("BACKUP04A3_PRIVACY_SENSITIVE_SEVENTH_MODULE_POST_COMMIT_V1"))
        assertTrue(state.contains("composerDraftStore.stageRestore(editor, drafts)"))
        assertTrue(state.contains("check(editor.commit())"))
        assertTrue(draftStore.contains("BACKUP04A3_NATIVE_DRAFT_JSON_SHARED_EDITOR_STAGE_V1"))

        val restoreStart = state.indexOf("fun restoreBackupState(")
        val persistence = state.indexOf("persistBackupRestoreState(", restoreStart)
        val projection = state.indexOf("applyRestoredPostComposerDraftsToLiveState(", restoreStart)
        assertTrue(restoreStart >= 0)
        assertTrue(persistence > restoreStart)
        assertTrue(projection > persistence)
    }

    private fun repository(relative: String): File =
        File(repositoryRoot, relative).also { file ->
            check(file.exists()) { "Missing repository fixture: ${file.path}" }
        }
}
