package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.account.RedditSavedHistoryUiItem
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedComments01MixedSavedUiPolicyTest {
    @Test
    fun stableKeysPreserveMixedFullnameIdentity() {
        val post = RedditSavedHistoryUiItem.PostItem(
fullname = "t3_post1",
            post = Post(
                id = "post1",
                community = "subshell",
                author = "poster",
                age = "1m",
                title = "Post",
                score = 1,
                comments = 2,
                kind = PostKind.TEXT,
                saved = true,
            ),
        )
        val comment = RedditSavedHistoryUiItem.CommentItem(
fullname = "t1_comment1",
            comment = ProfileCommentFixture(
                id = "t1_comment1",
                author = "commenter",
                postId = "parent1",
                age = "2m",
                body = "Comment",
                score = 1,
                placement = ThreadPlacement.TOP,
            ),
        )

        assertEquals("profile-saved-t3_post1", savedHistoryItemKey("profile-saved", post))
        assertEquals("profile-saved-t1_comment1", savedHistoryItemKey("profile-saved", comment))
    }

    @Test
    fun mediaProjectionKeepsOnlyPostsInTheirSavedOrder() {
        val first = RedditSavedHistoryUiItem.PostItem(
fullname = "t3_first",
            post = Post(
                id = "first",
                community = "subshell",
                author = "a",
                age = "1m",
                title = "First",
                score = 1,
                comments = 0,
                kind = PostKind.IMAGE,
                saved = true,
            ),
        )
        val comment = RedditSavedHistoryUiItem.CommentItem(
fullname = "t1_middle",
            comment = ProfileCommentFixture(
                id = "t1_comment",
                author = "b",
                postId = "parent",
                age = "1m",
                body = "Comment",
                score = 1,
                placement = ThreadPlacement.TOP,
            ),
        )
        val second = RedditSavedHistoryUiItem.PostItem(
fullname = "t3_second",
            post = Post(
                id = "second",
                community = "android",
                author = "c",
                age = "1m",
                title = "Second",
                score = 2,
                comments = 1,
                kind = PostKind.VIDEO,
                saved = true,
            ),
        )

        assertEquals(
            listOf("first", "second"),
            savedHistoryMediaPosts(listOf(first, comment, second)).map(Post::id),
        )
    }

    @Test
    fun commentTargetCarriesExactParentAndCommentIdentity() {
        val comment = RedditSavedHistoryUiItem.CommentItem(
fullname = "t1_comment",
            comment = ProfileCommentFixture(
                id = "t1_comment",
                author = "b",
                postId = "parent",
                age = "1m",
                body = "Comment",
                score = 1,
                placement = ThreadPlacement.TOP,
            ),
        )

        assertEquals(
            SavedCommentNavigationTarget(
                postId = "parent",
                commentId = "t1_comment",
            ),
            comment.savedCommentNavigationTarget(),
        )
        assertNull(
            RedditSavedHistoryUiItem.PostItem(
fullname = "t3_post",
                post = Post(
                    id = "post",
                    community = "subshell",
                    author = "a",
                    age = "1m",
                    title = "Post",
                    score = 1,
                    comments = 0,
                    kind = PostKind.TEXT,
                    saved = true,
                ),
            ).savedCommentNavigationTarget(),
        )
    }
}
