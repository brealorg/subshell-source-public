package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.RepositoryResult
import io.github.brealorg.subshell.data.RoomBackedIdentityMetadataRepository
import io.github.brealorg.subshell.data.IdentityMetadataCacheRepository
import io.github.brealorg.subshell.data.account.CommentEditRequest
import io.github.brealorg.subshell.data.account.RedditBannedUser
import io.github.brealorg.subshell.data.account.RedditModeratorUserBanRequest
import io.github.brealorg.subshell.data.account.RedditRemovalReasonFailureKind
import io.github.brealorg.subshell.data.account.RedditRemovalReasonPhase
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapability
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapabilityResult
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditBannedUsersReader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditBannedUsersResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentLoader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditCommentResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorUserMutationResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorUserWriter
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditRemovalReasonLoader
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedRedditRemovalReasonResult
import io.github.brealorg.subshell.data.network.NetworkRateLimit
import io.github.brealorg.subshell.data.network.NetworkResponseMetadata
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.reddit.IdentityMetadataDataSource
import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.model.Community
import io.github.brealorg.subshell.model.UserFixture
import io.github.brealorg.subshell.model.ResolvedRedditMedia
import io.github.brealorg.subshell.model.annotateRedditMedia
import io.github.brealorg.subshell.model.redditSourceMarkdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** MED1: OCB-408, OCB-430, OCB-445, OCB-655, OCB-461 (medium audit findings). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Med1MediumCorrectnessTest {
    private val emote = "![img](emote|t5_x|1 \"subshell-media https://emoji.redditmedia.com/x.png 20x20\")"

    @Test
    fun anEditedCommentKeepsItsEmoteAddress() = runBlocking {
        val coordinator = CommentThreadCoordinator(
            commentLoader = loader(comment("typo $emote")),
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        coordinator.loadRedditComments("acc", "t3_post", RedditCommentSort.BEST)

        coordinator.applyEditedComment(
            CommentEditRequest(
                accountStableId = "acc",
                postId = "t3_post",
                commentFullname = "t1_c1",
                commentAuthor = "me",
                text = "fixed ![img](emote|t5_x|1)",
                sort = RedditCommentSort.BEST,
            ),
        )

        assertEquals("fixed $emote", coordinator.redditCommentsUiState.value.comments.single().body)
    }

    @Test
    fun aConfirmedBanAndUnbanShowInTheBannedList() = runBlocking {
        var banned = false
        val coordinator = ModeratorUserManagementCoordinator(
            capability = null,
            reader = object : AuthenticatedRedditBannedUsersReader {
                override suspend fun load(accountStableId: String, rawCommunityName: String, after: String?, username: String?) =
                    AuthenticatedRedditBannedUsersResult.Loaded(
                        "subshell",
                        if (banned && username != null) listOf(RedditBannedUser(username)) else emptyList(),
                        null,
                    )
            },
            writer = object : AuthenticatedRedditModeratorUserWriter {
                override suspend fun mutate(request: RedditModeratorUserBanRequest): AuthenticatedRedditModeratorUserMutationResult {
                    banned = request.banned
                    return AuthenticatedRedditModeratorUserMutationResult.Acknowledged
                }
            },
            clientMode = null,
            activeAccountStableId = { "acct" },
            activeAccountUsername = { "current_user" },
        )
        coordinator.open("acct", "subshell")
        coordinator.load("acct")

        coordinator.mutate(RedditModeratorUserBanRequest("acct", "subshell", "target_user", banned = true))
        assertEquals(listOf("target_user"), coordinator.uiState.value.bannedUsers.map { it.username })

        coordinator.mutate(RedditModeratorUserBanRequest("acct", "subshell", "target_user", banned = false))
        assertTrue(coordinator.uiState.value.bannedUsers.isEmpty())
    }

    @Test
    fun aRejectedCallbackEndsTheRemovalReasonWait() = runBlocking {
        val capability = RecordingCapability()
        val coordinator = RemovalReasonCoordinator(
            capability = capability,
            loader = object : AuthenticatedRedditRemovalReasonLoader {
                override suspend fun load(accountStableId: String, communityName: String) =
                    AuthenticatedRedditRemovalReasonResult.AuthorizationRequired
            },
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            activeAccountStableId = { "acct" },
        )
        coordinator.load("acct", "subshell")
        coordinator.beginAuthorization()
        assertEquals(RedditRemovalReasonPhase.AWAITING_CALLBACK, coordinator.uiState.value.phase)

        val owned = coordinator.handleCallbackResultTokenIfOwned("rejected:AUTH_TAB_VERIFICATION_FAILED")

        assertTrue(owned)
        assertEquals(1, capability.cancels)
        assertEquals(RedditRemovalReasonFailureKind.CALLBACK, coordinator.uiState.value.failureKind)
    }

    @Test
    fun aPanoramaSizeRoundTripsInTheMediaAnnotation() {
        val annotated = annotateRedditMedia("![img](abc123)", resolve = { _ ->
            ResolvedRedditMedia(
                url = "https://i.redd.it/abc123.jpg",
                width = 12_000,
                height = 3_000,
            )
        })

        assertTrue(annotated, annotated.contains("12000x3000"))
        assertEquals("![img](abc123)", annotated.redditSourceMarkdown())
    }

    @Test
    fun aCacheFaultIsAStorageFailureNotACrash() = runBlocking {
        val repository = RoomBackedIdentityMetadataRepository(
            dataSource = object : IdentityMetadataDataSource {
                override suspend fun user(username: String, fallback: UserFixture) = ok(fallback)
                override suspend fun community(communityName: String, fallback: Community) = ok(fallback)
                override suspend fun communities(fallbacks: List<Community>) = ok(fallbacks)
                override suspend fun authorAvatars(fullnames: Collection<String>) = ok(emptyMap<String, String?>())
            },
            cache = object : IdentityMetadataCacheRepository {
                override fun cachedCommunityIdentity(name: String): Community? = throw IllegalStateException("disk full")
                override fun cacheCommunityIdentity(community: Community) = throw IllegalStateException("disk full")
                override fun cachedUserIdentity(username: String): UserFixture? = throw IllegalStateException("disk full")
                override fun cacheUserIdentity(user: UserFixture) = throw IllegalStateException("disk full")
            },
        )
        val fallback = Community(id = "x", name = "x", description = "", members = "", accent = 0L)

        val result = repository.refreshCommunityIdentity("x", fallback)

        assertTrue(result.toString(), result is RepositoryResult.Error)
    }

    private fun <T> ok(value: T): NetworkResult<T> = NetworkResult.Success(
        value = value,
        metadata = NetworkResponseMetadata(
            statusCode = 200,
            receivedAtEpochMs = 0L,
            rateLimit = NetworkRateLimit(remaining = null, used = null, resetAfterMs = null),
            retryAfterMs = null,
        ),
    )

    private fun loader(vararg comments: Comment) = object : AuthenticatedRedditCommentLoader {
        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) =
            AuthenticatedRedditCommentResult.Loaded(comments = comments.toList(), fromCache = true)
        override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort) =
            AuthenticatedRedditCommentResult.Loaded(comments = comments.toList(), fromCache = false)
        override suspend fun loadMore(accountStableId: String, postId: String, sort: RedditCommentSort, moreNodeId: String) =
            error("unused")
        override suspend fun cacheSubmittedComment(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            parentFullname: String,
            comment: Comment,
        ) = error("unused")
    }

    private fun comment(body: String) = Comment(
        id = "t1_c1",
        author = "me",
        age = "1h",
        body = body,
        score = 1,
        depth = 0,
        accent = 0L,
    )

    private class RecordingCapability : AuthCommentWriteCapability {
        var cancels = 0
        override suspend fun beginAuthorization(accountStableId: String) = "https://example.test/authorize"
        override fun hasPendingAuthorization() = true
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() {
            cancels++
        }
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("unused")
    }
}
