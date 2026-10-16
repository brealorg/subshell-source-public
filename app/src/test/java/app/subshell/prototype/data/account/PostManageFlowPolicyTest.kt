package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostManageFlowPolicyTest {
    private val target = PostManageTarget(
        accountStableId = "reddit:redreader:owner",
        postFullname = "t3_abc123",
        postAuthor = "owner",
        community = "subshell",
    )

    @Test
    fun everyMutationKeepsCanonicalTargetAndTypedPayload() {
        assertEquals("t3_abc123", target.postFullname)
        assertEquals("subshell", target.community)

        val nsfw = PostManageMutationRequest(
            target = target,
            operation = PostManageOperation.SET_NSFW,
            enabled = true,
        )
        assertTrue(requireNotNull(nsfw.enabled))

        val flair = PostManageMutationRequest(
            target = target,
            operation = PostManageOperation.SET_FLAIR,
            flairTemplateId = "template",
            flairText = "Photo",
        )
        assertEquals("template", flair.flairTemplateId)
    }

    @Test
    fun successfulMutationUpdatesOnlyRequestedSnapshotField() {
        val snapshot = PostManageSnapshot(
            postFullname = target.postFullname,
            author = "owner",
            community = "subshell",
            flairText = "Old",
            nsfw = false,
            spoiler = false,
            sendReplies = true,
        )
        val updated = applyPostManageMutation(
            snapshot,
            PostManageMutationRequest(
                target = target,
                operation = PostManageOperation.SET_SPOILER,
                enabled = true,
            ),
        )

        assertTrue(updated.spoiler)
        assertFalse(updated.nsfw)
        assertEquals("Old", updated.flairText)
        assertTrue(updated.sendReplies)
    }

    @Test
    fun flairMutationPreservesTemplateIdentityAndCustomText() {
        val snapshot = PostManageSnapshot(
            postFullname = target.postFullname,
            author = "owner",
            community = "subshell",
            flairTemplateId = "old-template",
            flairText = "Old",
        )
        val updated = applyPostManageMutation(
            snapshot,
            PostManageMutationRequest(
                target = target,
                operation = PostManageOperation.SET_FLAIR,
                flairTemplateId = "editable-template",
                flairText = "Custom text",
            ),
        )

        assertEquals("editable-template", updated.flairTemplateId)
        assertEquals("Custom text", updated.flairText)
    }
}
