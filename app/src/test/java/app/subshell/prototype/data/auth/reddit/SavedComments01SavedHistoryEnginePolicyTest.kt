package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.cache.RedditSavedHistoryCacheStore
import app.subshell.prototype.data.network.reddit.RedditSavedHistoryItem
import app.subshell.prototype.data.redditMixedSavedHistoryQueryKey
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedComments01SavedHistoryEnginePolicyTest {
    @Test
    fun mixedSavedQueryIdentityIsAccountAndUserScopedAndSeparateFromSplitKinds() {
        val mixedA = redditMixedSavedHistoryQueryKey(
            accountStableId = "reddit:a",
            username = "BrealOrg",
        )
        val mixedB = redditMixedSavedHistoryQueryKey(
            accountStableId = "reddit:b",
            username = "BrealOrg",
        )

        assertTrue(mixedA.value.contains("saved-mixed"))
        assertNotEquals(mixedA, mixedB)
    }

    @Test
    fun cachePersistsMixedOrderAndOpaqueCursorAcrossStoreRecreation() {
        val directory = Files.createTempDirectory("subshell-saved-history").toFile()
        try {
            val key = redditMixedSavedHistoryQueryKey(
                accountStableId = "reddit:a",
                username = "brealorg",
            )
            val post = RedditSavedHistoryItem.PostItem(
                Post(
                    id = "post1",
                    community = "subshell",
                    author = "poster",
                    age = "1m",
                    title = "Saved post",
                    score = 4,
                    comments = 2,
                    kind = PostKind.TEXT,
                    saved = true,
                ),
            )
            val comment = RedditSavedHistoryItem.CommentItem(
                ProfileCommentFixture(
                    id = "t1_comment1",
                    author = "commenter",
                    postId = "parent1",
                    age = "2m",
                    body = "Saved comment",
                    score = 3,
                    placement = ThreadPlacement.TOP,
                    community = "subshell",
                    postTitle = "Parent post",
                ),
            )

            RedditSavedHistoryCacheStore(directory).replace(
                queryKey = key,
                items = listOf(post, comment),
                nextCursor = "t1_after",
            )
            val restored = RedditSavedHistoryCacheStore(directory).read(key)

            requireNotNull(restored)
            assertEquals(listOf("t3_post1", "t1_comment1"), restored.items.map { it.fullname })
            assertEquals("t1_after", restored.nextCursor)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun appendDeduplicatesByFullnameWithoutReorderingExistingItems() {
        val directory = Files.createTempDirectory("subshell-saved-history").toFile()
        try {
            val key = redditMixedSavedHistoryQueryKey(
                accountStableId = "reddit:a",
                username = "brealorg",
            )
            val first = RedditSavedHistoryItem.CommentItem(
                ProfileCommentFixture(
                    id = "t1_first",
                    author = "a",
                    postId = "p1",
                    age = "1m",
                    body = "first",
                    score = 1,
                    placement = ThreadPlacement.TOP,
                ),
            )
            val duplicate = first.copy(
                comment = first.comment.copy(body = "new body ignored for existing identity"),
            )
            val second = RedditSavedHistoryItem.CommentItem(
                ProfileCommentFixture(
                    id = "t1_second",
                    author = "b",
                    postId = "p2",
                    age = "1m",
                    body = "second",
                    score = 1,
                    placement = ThreadPlacement.TOP,
                ),
            )

            val store = RedditSavedHistoryCacheStore(directory)
            store.replace(
                queryKey = key,
                items = listOf(first),
                nextCursor = "cursor1",
            )
            val appended = store.append(
                queryKey = key,
                expectedCursor = "cursor1",
                items = listOf(duplicate, second),
                nextCursor = null,
            )

            assertEquals(listOf("t1_first", "t1_second"), appended.items.map { it.fullname })
            assertEquals("first", (appended.items.first() as RedditSavedHistoryItem.CommentItem).comment.body)
            assertFalse(appended.nextCursor != null)
        } finally {
            directory.deleteRecursively()
        }
    }
}
