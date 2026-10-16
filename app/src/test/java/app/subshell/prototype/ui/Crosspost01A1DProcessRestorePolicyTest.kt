package app.subshell.prototype.ui

import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Crosspost01A1DProcessRestorePolicyTest {
    private fun source() = ComposerCrosspostSource(
        fullname = "t3_source123",
        community = "motogp",
        author = "source_author",
        title = "Original source title",
        kind = PostKind.LINK,
        nsfw = true,
        spoiler = true,
    )

    private fun snapshot(draft: PostComposerDraft): SubshellProcessSnapshot =
        SubshellProcessSnapshot(
            routeStack = SubshellRouteStack
                .forDestination(
                    MainDestination.HOME,
                    FeedSource.HOME,
                    FeedSort.BEST,
                )
                .push(SubshellRoute.PostComposer),
            scrollAnchors = emptyMap(),
            userProfileTabs = emptyMap(),
            postComposerDraft = draft,
            feedVideoMuted = true,
            backgroundResumeMediaId = null,
            backgroundResumeWasManual = false,
        )

    @Test
    fun sourceBoundDraftRoundTripsProcessSnapshotWithoutFlatteningSource() {
        val flair = ComposerFlair(
            templateId = "team-flair",
            text = "Factory team",
            editable = false,
            textColor = "light",
            backgroundColor = "#cc0000",
        )
        val draft = PostComposerDraft(
            id = "crosspost-process-draft",
            accountStableId = "reddit:owner",
            community = "formula1",
            type = ComposerPostType.CROSSPOST,
            title = "Edited wrapper title",
            flair = flair,
            nsfw = true,
            spoiler = true,
            createdAtEpochMs = 100L,
            updatedAtEpochMs = 200L,
            crosspostSource = source(),
        )

        val restored = requireNotNull(
            decodeSubshellProcessSnapshot(
                encodeSubshellProcessSnapshot(snapshot(draft)),
            ),
        )

        assertEquals(ComposerPostType.CROSSPOST, restored.postComposerDraft.type)
        assertEquals("reddit:owner", restored.postComposerDraft.accountStableId)
        assertEquals("formula1", restored.postComposerDraft.community)
        assertEquals("Edited wrapper title", restored.postComposerDraft.title)
        assertEquals(flair, restored.postComposerDraft.flair)
        assertEquals(source(), restored.postComposerDraft.crosspostSource)
        assertEquals("Original source title", restored.postComposerDraft.crosspostSource?.title)
        assertEquals("t3_source123", restored.postComposerDraft.crosspostSource?.fullname)
        assertTrue(restored.routeStack.current is SubshellRoute.PostComposer)
    }

    @Test
    fun missingSourceCapsuleRejectsCrosspostSnapshotInsteadOfDowngradingToText() {
        val draft = PostComposerDraft(
            id = "missing-source",
            accountStableId = "reddit:owner",
            community = "formula1",
            type = ComposerPostType.CROSSPOST,
            title = "Crosspost without its source",
            nsfw = true,
            spoiler = true,
            crosspostSource = null,
        )

        assertNull(
            decodeSubshellProcessSnapshot(
                encodeSubshellProcessSnapshot(snapshot(draft)),
            ),
        )
    }

    @Test
    fun sourceCapsuleOnOrdinaryDraftRejectsSnapshotInsteadOfSmugglingIdentity() {
        val draft = PostComposerDraft(
            id = "wrong-type",
            accountStableId = "reddit:owner",
            community = "formula1",
            type = ComposerPostType.TEXT,
            title = "Ordinary post must not carry a crosspost source",
            nsfw = true,
            spoiler = true,
            crosspostSource = source(),
        )

        assertNull(
            decodeSubshellProcessSnapshot(
                encodeSubshellProcessSnapshot(snapshot(draft)),
            ),
        )
    }

    @Test
    fun sourceSensitivityCannotBeRemovedByProcessRestore() {
        val draft = PostComposerDraft(
            id = "sensitivity",
            accountStableId = "reddit:owner",
            community = "formula1",
            type = ComposerPostType.CROSSPOST,
            title = "Edited title",
            nsfw = false,
            spoiler = false,
            crosspostSource = source(),
        )

        assertNull(
            decodeSubshellProcessSnapshot(
                encodeSubshellProcessSnapshot(snapshot(draft)),
            ),
        )
    }

    @Test
    fun ordinaryComposerSnapshotRemainsBackwardCompatibleWithoutSourceTags() {
        val draft = PostComposerDraft(
            id = "ordinary",
            accountStableId = "reddit:owner",
            community = "norge",
            type = ComposerPostType.TEXT,
            title = "Ordinary draft",
            body = "Body",
        )

        val restored = requireNotNull(
            decodeSubshellProcessSnapshot(
                encodeSubshellProcessSnapshot(snapshot(draft)),
            ),
        )

        assertEquals(ComposerPostType.TEXT, restored.postComposerDraft.type)
        assertNull(restored.postComposerDraft.crosspostSource)
        assertEquals("Ordinary draft", restored.postComposerDraft.title)
    }
}
