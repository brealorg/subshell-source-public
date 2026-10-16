package app.subshell.prototype.backup

import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.ComposerMediaKind
import app.subshell.prototype.ui.ComposerPostType
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup04A3PostComposerDraftsStateTest {
    @Test
    fun captureIncludesKnownEmptyRowsAndExcludesMediaAndOwnerlessDrafts() {
        val state = SubshellState(
            initialPostComposerDrafts = listOf(
                text("text", "local:root", "Portable"),
                media("media", "local:root"),
                text("ownerless", null, "Legacy"),
                text("disconnected", "Disconnected:Case", "Keep owner"),
            ),
        )

        val captured = state.capturePostComposerDraftsByAccount()

        assertEquals(listOf("text"), captured["local:root"]?.map { it.id })
        assertEquals(listOf("disconnected"), captured["Disconnected:Case"]?.map { it.id })
        assertFalse(captured.values.flatten().any { it.id == "media" })
        assertFalse(captured.values.flatten().any { it.id == "ownerless" })
    }

    @Test
    fun nullModuleIsNoopAndEmptyModuleClearsOnlyPortableDrafts() {
        var persistedDrafts: List<PostComposerDraft>? = null
        val state = SubshellState(
            initialPostComposerDrafts = listOf(
                text("portable", "local:root", "Portable"),
                media("media", "local:root"),
                text("ownerless", null, "Legacy"),
            ),
            persistBackupRestoreState = { _, _, _, _, _, _, drafts, _, _, _ ->
                persistedDrafts = drafts
            },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            postComposerDraftsByAccount = null,
        )
        assertNull(persistedDrafts)
        assertEquals(3, state.postComposerDrafts.size)

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            postComposerDraftsByAccount = emptyMap(),
        )
        assertEquals(setOf("media", "ownerless"), persistedDrafts.orEmpty().map { it.id }.toSet())
        assertEquals(setOf("media", "ownerless"), state.postComposerDrafts.map { it.id }.toSet())
    }

    @Test
    fun accountRowReplacementPreservesOtherAccountsAndNonportableIdCollisionWins() {
        var persistedDrafts: List<PostComposerDraft>? = null
        val state = SubshellState(
            initialPostComposerDrafts = listOf(
                text("root-old", "local:root", "Old"),
                text("other", "Other:Case", "Other"),
                media("collision", "local:root"),
            ),
            persistBackupRestoreState = { _, _, _, _, _, _, drafts, _, _, _ ->
                persistedDrafts = drafts
            },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            postComposerDraftsByAccount = mapOf(
                "local:root" to listOf(
                    portable("root-new", "New", 5_000L),
                    portable("collision", "Imported collision", 6_000L),
                ),
            ),
        )

        val ids = persistedDrafts.orEmpty().map { it.id }.toSet()
        assertTrue("root-new" in ids)
        assertTrue("other" in ids)
        assertTrue("collision" in ids)
        assertFalse("root-old" in ids)
        assertEquals(
            ComposerPostType.MEDIA,
            persistedDrafts.orEmpty().first { it.id == "collision" }.type,
        )
    }

    @Test
    fun explicitEmptyAccountRowClearsOnlyThatPortableOwner() {
        val state = SubshellState(
            initialPostComposerDrafts = listOf(
                text("root", "local:root", "Root"),
                text("other", "Other", "Other"),
                media("media", "local:root"),
            ),
            persistBackupRestoreState = { _, _, _, _, _, _, _, _, _, _ -> },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            postComposerDraftsByAccount = mapOf("local:root" to emptyList()),
        )

        assertEquals(setOf("other", "media"), state.postComposerDrafts.map { it.id }.toSet())
    }

    @Test
    fun durableFailurePreventsLiveDraftProjection() {
        val state = SubshellState(
            initialPostComposerDrafts = listOf(text("old", "local:root", "Old")),
            persistBackupRestoreState = { _, _, _, _, _, _, _, _, _, _ ->
                error("durable failure")
            },
        )

        val failed = runCatching {
            state.restoreBackupState(
                settings = state.captureSettings(),
                joinedCommunityIdsByAccount = null,
                postComposerDraftsByAccount = mapOf(
                    "local:root" to listOf(portable("new", "New", 5_000L)),
                ),
            )
        }
        assertTrue(failed.isFailure)
        assertEquals(listOf("old"), state.postComposerDrafts.map { it.id })
    }

    private fun text(
        id: String,
        owner: String?,
        title: String,
    ) = PostComposerDraft(
        id = id,
        accountStableId = owner,
        community = "subshell",
        type = ComposerPostType.TEXT,
        title = title,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 2_000L,
    )

    private fun media(
        id: String,
        owner: String?,
    ) = PostComposerDraft(
        id = id,
        accountStableId = owner,
        community = "subshell",
        type = ComposerPostType.MEDIA,
        title = "Media",
        media = listOf(
            ComposerMediaAttachment(
                uri = "content://device-local/$id",
                mimeType = "image/png",
                kind = ComposerMediaKind.IMAGE,
            ),
        ),
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 3_000L,
    )

    private fun portable(
        id: String,
        title: String,
        updated: Long,
    ) = PortablePostComposerDraft(
        id = id,
        community = "subshell",
        type = "TEXT",
        title = title,
        body = "",
        linkUrl = "",
        inputMode = "MARKDOWN",
        nsfw = false,
        spoiler = false,
        sendReplies = true,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = updated,
    )
}
