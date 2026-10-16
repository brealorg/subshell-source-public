package app.subshell.prototype.data.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review data/account #2 (ACCOUNT03_MATCHES_NEVER_THROW_V1): the write states'
 * `matches()` are called during composition; an id their normalizer refuses
 * (a local fixture comment `local-comment-<post>-1`) used to throw and crash
 * the screen. It now matches nothing, also in an idle state without an id.
 */
class Account03MatchesNeverThrowTest {
    private val account = "reddit:me"
    private val badComment = "local-comment-p1-1"
    private val badPost = "local-post-1"

    @Test
    fun commentStatesDoNotMatchAnIdTheyCannotRead() {
        assertFalse(CommentDeleteUiState(accountStableId = account).matches(account, badComment))
        assertFalse(CommentDeleteUiState(accountStableId = account, commentFullname = "t1_abc").matches(account, badComment))
        assertFalse(CommentEditUiState(accountStableId = account).matches(account, badComment))
        assertTrue(CommentEditUiState(accountStableId = account, commentFullname = "t1_abc").matches(account, "abc"))
    }

    @Test
    fun postStatesDoNotMatchAnIdTheyCannotRead() {
        assertFalse(PostEditUiState(accountStableId = account).matches(account, badPost))
        assertFalse(PostDeleteUiState(accountStableId = account).matches(account, badPost))
        val target = PostManageTarget(account, "t3_abc", "me", "test")
        assertFalse(PostManageUiState(target = target).matches(account, badPost))
        assertTrue(PostManageUiState(target = target).matches(account, "abc"))
    }

    @Test
    fun userFlairDoesNotMatchACommunityItCannotRead() {
        assertFalse(UserFlairUiState(accountStableId = account).matches(account, "not a community!"))
        assertTrue(UserFlairUiState(accountStableId = account, communityName = "test").matches(account, "r/Test"))
    }
}
