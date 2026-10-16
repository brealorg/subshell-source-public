package app.subshell.prototype.data

import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import app.subshell.prototype.model.UserFixture

class FakeSubshellRepository(
    initialMode: FakeRepositoryMode = FakeRepositoryMode.CONTENT,
) : SubshellRepository {
    var mode: FakeRepositoryMode = initialMode

    override val localUsername: String = SampleData.LOCAL_USERNAME
    override val defaultSavedPostIds: Set<String> = SampleData.defaultSavedPostIds.toSet()
    override val defaultUpvotedPostIds: Set<String> = SampleData.defaultUpvotedPostIds.toSet()
    private val mutableCreatedPosts = mutableListOf<Post>()
    override val createdPosts: List<Post>
        get() = mutableCreatedPosts.toList()

    private val commentsByPost = linkedMapOf<String, MutableList<Comment>>()
    private val savedByPost = linkedMapOf<String, Boolean>()
    private val votesByPost = linkedMapOf<String, Int>()
    private val pollVotesByPost = linkedMapOf<String, String?>()
    private val pageModes = linkedMapOf<Pair<RepositoryQueryKey, RepositoryLoadOperation>, FakeRepositoryMode>()
    private val pageRequests = mutableListOf<FakeRepositoryPageRequestSnapshot>()

    var repeatPreviousItemOnAppend: Boolean = false

    fun setPageMode(
        queryKey: RepositoryQueryKey,
        operation: RepositoryLoadOperation,
        mode: FakeRepositoryMode,
    ) {
        pageModes[queryKey to operation] = mode
    }

    fun clearPageMode(
        queryKey: RepositoryQueryKey,
        operation: RepositoryLoadOperation,
    ) {
        pageModes.remove(queryKey to operation)
    }

    fun clearPageModes() {
        pageModes.clear()
    }

    fun pageRequestSnapshot(): List<FakeRepositoryPageRequestSnapshot> =
        pageRequests.toList()

    fun clearPageRequestHistory() {
        pageRequests.clear()
    }

    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> =
        pageResult(SampleData.posts + mutableCreatedPosts, request)

    override fun post(postId: String): RepositoryResult<Post> {
        val post = findPost(postId)
            ?: return RepositoryResult.Empty("Unknown post: $postId")
        return itemResult(post)
    }

    override fun comments(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<Comment>> =
        pageResult(
            SampleData.comments + commentsByPost.values.flatten(),
            request,
        )

    override fun commentsForPost(
        postId: String,
        request: RepositoryPageRequest,
    ): RepositoryResult<List<Comment>> {
        val post = findPost(postId)
            ?: return RepositoryResult.Empty("Unknown post: $postId")

        val comments = if (post in SampleData.posts) {
            SampleData.comments.toMutableList().apply {
                SampleData.profileComments
                    .filter { fixture -> fixture.postId == post.id }
                    .forEach { fixture ->
                        val index = when (fixture.placement) {
                            ThreadPlacement.TOP -> 0
                            ThreadPlacement.MIDDLE -> size / 2
                            ThreadPlacement.BOTTOM -> size
                        }
                        add(index, fixture.toThreadComment(post))
                    }
            }
        } else {
            mutableListOf()
        }
        comments += commentsByPost[postId].orEmpty()
        return pageResult(comments, request)
    }

    override fun communities(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<Community>> =
        pageResult(SampleData.communities, request)

    override fun community(name: String): RepositoryResult<Community> {
        val community = SampleData.communities.firstOrNull { candidate ->
            candidate.name.equals(name.trim(), ignoreCase = true)
        } ?: return RepositoryResult.Empty("Unknown community: $name")
        return itemResult(community)
    }

    override fun accounts(): RepositoryResult<List<String>> =
        collectionResult(SampleData.accounts)

    override fun users(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<UserFixture>> =
        pageResult(SampleData.users, request)

    override fun user(username: String): RepositoryResult<UserFixture> {
        val normalized = username.trim()
        val user = SampleData.users.firstOrNull { candidate ->
            candidate.username.equals(normalized, ignoreCase = true)
        } ?: UserFixture(
            username = normalized.ifBlank { "unknown" },
            description = "Public profile preview.",
            karma = "—",
        )
        return itemResult(user)
    }

    override fun profileComments(
        username: String?,
        request: RepositoryPageRequest,
    ): RepositoryResult<List<ProfileCommentFixture>> {
        val filtered = username
            ?.takeIf(String::isNotBlank)
            ?.let { selected ->
                SampleData.profileComments.filter { fixture ->
                    fixture.author.equals(selected, ignoreCase = true)
                }
            }
            ?: SampleData.profileComments
        return pageResult(filtered, request)
    }

    override fun inbox(
        request: RepositoryPageRequest,
    ): RepositoryResult<List<InboxItem>> =
        pageResult(SampleData.inbox, request)

    override fun inboxItem(itemId: String): RepositoryResult<InboxItem> {
        val item = SampleData.inbox.firstOrNull { candidate -> candidate.id == itemId }
            ?: return RepositoryResult.Empty("Unknown inbox item: $itemId")
        return itemResult(item)
    }

    override fun createPost(post: Post): RepositoryResult<Post> {
        if ((SampleData.posts + mutableCreatedPosts).any { existing -> existing.id == post.id }) {
            return RepositoryResult.Error("Duplicate post id: ${post.id}")
        }
        mutableCreatedPosts += post
        return RepositoryResult.Content(post)
    }

    override fun addComment(postId: String, comment: Comment): RepositoryResult<Comment> {
        if (findPost(postId) == null) {
            return RepositoryResult.Error("Unknown post: $postId")
        }
        val comments = commentsByPost.getOrPut(postId) { mutableListOf() }
        if (comments.any { existing -> existing.id == comment.id }) {
            return RepositoryResult.Error("Duplicate comment id: ${comment.id}")
        }
        comments += comment
        return RepositoryResult.Content(comment)
    }

    override fun recordSaved(postId: String, saved: Boolean): RepositoryResult<Unit> {
        if (findPost(postId) == null) {
            return RepositoryResult.Error("Unknown post: $postId")
        }
        savedByPost[postId] = saved
        return RepositoryResult.Content(Unit)
    }

    override fun recordVote(postId: String, direction: Int): RepositoryResult<Unit> {
        require(direction in -1..1) { "Vote direction must be -1, 0 or 1" }
        if (findPost(postId) == null) {
            return RepositoryResult.Error("Unknown post: $postId")
        }
        votesByPost[postId] = direction
        return RepositoryResult.Content(Unit)
    }

    override fun recordPollVote(postId: String, optionId: String?): RepositoryResult<Unit> {
        val post = findPost(postId)
            ?: return RepositoryResult.Error("Unknown post: $postId")
        val poll = post.poll
            ?: return RepositoryResult.Error("Post is not a poll: $postId")
        if (optionId != null && poll.options.none { option -> option.id == optionId }) {
            return RepositoryResult.Error("Unknown poll option: $optionId")
        }
        pollVotesByPost[postId] = optionId
        return RepositoryResult.Content(Unit)
    }

    private fun findPost(postId: String): Post? =
        (SampleData.posts + mutableCreatedPosts).firstOrNull { candidate ->
            candidate.id == postId
        }

    fun mutationSnapshot(): FakeRepositoryMutationSnapshot =
        FakeRepositoryMutationSnapshot(
            createdPostIds = mutableCreatedPosts.map(Post::id),
            commentIdsByPost = commentsByPost.mapValues { (_, comments) ->
                comments.map(Comment::id)
            },
            savedByPost = savedByPost.toMap(),
            votesByPost = votesByPost.toMap(),
            pollVotesByPost = pollVotesByPost.toMap(),
        )

    private fun <T> itemResult(value: T): RepositoryResult<T> = when (mode) {
        FakeRepositoryMode.CONTENT -> RepositoryResult.Content(value)
        FakeRepositoryMode.REFRESHING -> RepositoryResult.Content(value, refreshing = true)
        FakeRepositoryMode.LOADING -> RepositoryResult.Loading
        FakeRepositoryMode.EMPTY -> RepositoryResult.Empty()
        FakeRepositoryMode.OFFLINE -> RepositoryResult.Offline(value)
        FakeRepositoryMode.ERROR -> RepositoryResult.Error("Fake repository error", value)
    }

    private fun <T> collectionResult(values: List<T>): RepositoryResult<List<T>> = when {
        values.isEmpty() && mode in setOf(FakeRepositoryMode.CONTENT, FakeRepositoryMode.REFRESHING) ->
            RepositoryResult.Empty()
        else -> itemResult(values)
    }

    private fun <T> pageResult(
        values: List<T>,
        request: RepositoryPageRequest,
    ): RepositoryResult<List<T>> {
        pageRequests += FakeRepositoryPageRequestSnapshot(
            queryKey = request.queryKey,
            operation = request.operation,
            cursor = request.cursor,
            limit = request.limit,
        )

        val start = request.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val contentPage = values.drop(start).take(request.limit)
        val page = if (
            repeatPreviousItemOnAppend &&
            request.operation == RepositoryLoadOperation.APPEND &&
            start > 0 &&
            contentPage.isNotEmpty()
        ) {
            listOf(values[start - 1]) + contentPage
        } else {
            contentPage
        }
        val consumed = contentPage.size
        val nextCursor = (start + consumed)
            .takeIf { next -> next < values.size }
            ?.toString()
        val resolvedMode = pageModes[request.queryKey to request.operation] ?: mode

        return when (resolvedMode) {
            FakeRepositoryMode.CONTENT -> if (page.isEmpty()) {
                RepositoryResult.Empty()
            } else {
                RepositoryResult.Content(page, nextCursor = nextCursor)
            }
            FakeRepositoryMode.REFRESHING -> if (page.isEmpty()) {
                RepositoryResult.Empty()
            } else {
                RepositoryResult.Content(
                    value = page,
                    refreshing = true,
                    nextCursor = nextCursor,
                )
            }
            FakeRepositoryMode.LOADING -> RepositoryResult.Loading
            FakeRepositoryMode.EMPTY -> RepositoryResult.Empty()
            FakeRepositoryMode.OFFLINE -> RepositoryResult.Offline(
                cached = page,
                nextCursor = nextCursor,
            )
            FakeRepositoryMode.ERROR -> RepositoryResult.Error(
                message = "Fake repository error",
                cached = page,
                nextCursor = nextCursor,
            )
        }
    }
}

internal fun createFakeSubshellRepository(): SubshellRepository =
    FakeSubshellRepository()

private fun ProfileCommentFixture.toThreadComment(post: Post): Comment = Comment(
    id = id,
    author = author,
    age = age,
    body = body,
    score = score,
    depth = 0,
    accent = 0xFFFF8A50,
    isOp = author.equals(post.author, ignoreCase = true),
)
