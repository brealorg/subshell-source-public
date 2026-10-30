package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageOperation
import app.subshell.prototype.data.account.PostManageTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RedditPostManageNetworkPolicyTest {
    private val target = PostManageTarget(
        accountStableId = "account",
        postFullname = "t3_abc123",
        postAuthor = "owner",
        community = "subshell",
    )

    @Test
    fun infoAndFlairRequestsUseCanonicalReadEndpoints() {
        assertEquals(
            listOf("comments", "abc123"),
            redditPostManageInfoRequest("abc123").pathSegments,
        )
        assertEquals(
            listOf("r", "subshell", "api", "link_flair_v2"),
            redditPostManageFlairRequest("r/SubShell").pathSegments,
        )
    }

    @Test
    fun mutationsUseDocumentedPostMetadataEndpoints() {
        val replies = requireNotNull(
            redditPostManageMutationFormRequest(
                PostManageMutationRequest(
                    target = target,
                    operation = PostManageOperation.SET_SEND_REPLIES,
                    enabled = false,
                ),
            ),
        )
        assertEquals(listOf("api", "sendreplies"), replies.pathSegments)
        assertEquals("false", replies.fields.toMap()["state"])

        val nsfw = requireNotNull(
            redditPostManageMutationFormRequest(
                PostManageMutationRequest(
                    target = target,
                    operation = PostManageOperation.SET_NSFW,
                    enabled = true,
                ),
            ),
        )
        assertEquals(listOf("api", "marknsfw"), nsfw.pathSegments)

        val spoiler = requireNotNull(
            redditPostManageMutationFormRequest(
                PostManageMutationRequest(
                    target = target,
                    operation = PostManageOperation.SET_SPOILER,
                    enabled = false,
                ),
            ),
        )
        assertEquals(listOf("api", "unspoiler"), spoiler.pathSegments)

        val flair = requireNotNull(
            redditPostManageMutationFormRequest(
                PostManageMutationRequest(
                    target = target,
                    operation = PostManageOperation.SET_FLAIR,
                    flairTemplateId = "template",
                    flairText = "Photo",
                ),
            ),
        )
        assertEquals(
            listOf("r", "subshell", "api", "selectflair"),
            flair.pathSegments,
        )
        assertEquals("t3_abc123", flair.fields.toMap()["link"])

        assertNull(
            redditPostManageMutationFormRequest(
                PostManageMutationRequest(
                    target = target,
                    operation = PostManageOperation.FLAIR_ACCESS,
                ),
            ),
        )
    }
}
