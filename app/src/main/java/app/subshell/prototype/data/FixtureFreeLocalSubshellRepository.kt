package app.subshell.prototype.data

import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.UserFixture

internal const val FIXTURE_FREE_LOCAL_USERNAME = "local"

internal class FixtureFreeLocalSubshellRepository : SubshellRepository {
    override val localUsername: String = FIXTURE_FREE_LOCAL_USERNAME
    override val defaultSavedPostIds: Set<String> = emptySet()
    override val defaultUpvotedPostIds: Set<String> = emptySet()
    override val createdPosts: List<Post> = emptyList()

    override fun posts(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<Post>> = RepositoryResult.Empty()

    override fun post(postId: String): RepositoryResult<Post> =
        RepositoryResult.Empty("Post is not available in the local production fallback")

    override fun comments(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<Comment>> = RepositoryResult.Empty()

    override fun commentsForPost(
        postId: String,
        request: RepositoryPageRequest,
    ): RepositoryResult<List<Comment>> = RepositoryResult.Empty()

    override fun communities(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<Community>> = RepositoryResult.Empty()

    override fun community(name: String): RepositoryResult<Community> =
        RepositoryResult.Empty("Community is not available in the local production fallback")

    override fun accounts(): RepositoryResult<List<String>> =
        RepositoryResult.Content(listOf(localUsername))

    override fun users(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<UserFixture>> = RepositoryResult.Empty()

    override fun user(username: String): RepositoryResult<UserFixture> =
        RepositoryResult.Empty("User is not available in the local production fallback")

    override fun profileComments(
        username: String?,
        request: RepositoryPageRequest,
    ): RepositoryResult<List<ProfileCommentFixture>> = RepositoryResult.Empty()

    override fun inbox(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<InboxItem>> = RepositoryResult.Empty()

    override fun inboxItem(itemId: String): RepositoryResult<InboxItem> =
        RepositoryResult.Empty("Inbox item is not available in the local production fallback")

    override fun createPost(post: Post): RepositoryResult<Post> =
        RepositoryResult.Error("Local post creation is unavailable")

    override fun addComment(
        postId: String,
        comment: Comment,
    ): RepositoryResult<Comment> =
        RepositoryResult.Error("Local comment creation is unavailable")

    override fun recordSaved(
        postId: String,
        saved: Boolean,
    ): RepositoryResult<Unit> =
        RepositoryResult.Error("Local save mutation is unavailable")

    override fun recordVote(
        postId: String,
        direction: Int,
    ): RepositoryResult<Unit> =
        RepositoryResult.Error("Local vote mutation is unavailable")

    override fun recordPollVote(
        postId: String,
        optionId: String?,
    ): RepositoryResult<Unit> =
        RepositoryResult.Error("Local poll mutation is unavailable")
}

internal fun createFixtureFreeLocalSubshellRepository(): SubshellRepository =
    FixtureFreeLocalSubshellRepository()
