package io.github.brealorg.subshell.backup

import io.github.brealorg.subshell.ui.ComposerPostType
import io.github.brealorg.subshell.ui.PostComposerDraft
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.crosspost01A1CDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Crosspost01A1CBackupIsolationPolicyTest {
    @Test
    fun existingPortableTextLinkFormatNeverExportsASourceBoundCrosspost() {
        val sourceBound = crosspost01A1CDraft().copy(id = "crosspost", accountStableId = "local:root")
        val inconsistent = sourceBound.copy(id = "inconsistent", type = ComposerPostType.TEXT)
        val state = SubshellState(initialPostComposerDrafts = listOf(sourceBound, inconsistent))
        assertTrue(state.capturePostComposerDraftsByAccount().values.flatten().isEmpty())
    }

    @Test
    fun emptyPortableDraftRestorePreservesExistingNonportableCrossposts() {
        var saved: List<PostComposerDraft>? = null
        val sourceBound = crosspost01A1CDraft().copy(id = "crosspost", accountStableId = "local:root")
        val ordinary = PostComposerDraft(id = "ordinary", title = "Ordinary", accountStableId = "local:root")
        val state = SubshellState(
            initialPostComposerDrafts = listOf(sourceBound, ordinary),
            persistBackupRestoreState = { _, _, _, _, _, _, drafts, _, _, _, _ -> saved = drafts },
        )
        state.restoreBackupState(state.captureSettings(), joinedCommunityIdsByAccount = null, postComposerDraftsByAccount = emptyMap())
        assertEquals(listOf(sourceBound), saved)
        assertEquals(listOf(sourceBound), state.postComposerDrafts.toList())
    }

    @Test
    fun crosspostDraftCannotReopenForAnUnauthenticatedOrDifferentAccount() {
        val draft = crosspost01A1CDraft()
        val state = SubshellState(initialPostComposerDrafts = listOf(draft))
        assertFalse(state.openPostComposerDraft(draft.id))
        assertEquals(listOf(draft), state.postComposerDrafts.toList())
    }
}
