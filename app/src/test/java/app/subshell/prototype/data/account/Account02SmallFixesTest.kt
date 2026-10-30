package app.subshell.prototype.data.account

import app.subshell.prototype.data.RedditCommentSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review data/account (reports/2026-09-28-review-account.md) group B: #7, #6, #5, #8. */
class Account02SmallFixesTest {
    // #7 ACCOUNT02_SORT_KEEPS_AUTHOR_FULLNAMES_V1
    @Test
    fun aSortChangeKeepsTheAvatarLookupsOfTheCommentsStillOnScreen() {
        val loaded = RedditCommentsUiState(
            accountStableId = "reddit:me",
            postId = "p1",
            sort = RedditCommentSort.BEST,
            authorFullnames = mapOf("alice" to "t2_alice"),
        )

        val sameSort = loaded.beginSortLoad("reddit:me", "p1", RedditCommentSort.NEW)
        val otherPost = loaded.beginSortLoad("reddit:me", "p2", RedditCommentSort.NEW)

        assertEquals(mapOf("alice" to "t2_alice"), sameSort.authorFullnames)
        assertEquals(emptyMap<String, String>(), otherPost.authorFullnames)
    }

    // #6 ACCOUNT02_CHAT_PARTICIPANT_SLASH_U_V1
    @Test
    fun aChatParticipantMayBeWrittenWithOrWithoutSlashU() {
        assertEquals("alice", normalizeDirectChatParticipantUsername("/u/alice"))
        assertEquals("Alice", normalizeDirectChatParticipantUsername(" U/Alice "))
        assertEquals("alice", normalizeDirectChatParticipantUsername("alice"))
    }

    // #5 ACCOUNT02_SAVE_TARGET_KIND_V1
    @Test
    fun onlyAnExplicitT1IsACommentSaveAndDoublePrefixesAreRejected() {
        assertFalse(isCommentSaveFullname("abc123"))
        assertFalse(isCommentSaveFullname("t3_abc123"))
        assertTrue(isCommentSaveFullname("T1_abc123"))
        assertEquals("t3_abc123", normalizePostSaveFullname("abc123"))
        assertTrue(runCatching { normalizePostSaveFullname("t1_t3_abc") }.isFailure)
    }

    // #8 ACCOUNT02_REPORT_TARGET_ANY_REDDIT_HOST_V1
    @Test
    fun reportLookupAcceptsEveryRedditHostAndIgnoresCaseInPathWordsAndIds() {
        assertEquals(
            ModeratorReportTarget("t3_abc", "x"),
            moderatorReportTargetOrNull("https://sh.reddit.com/r/x/comments/abc/"),
        )
        assertEquals(
            ModeratorReportTarget("t1_def", "askreddit", postFullname = "t3_abc"),
            moderatorReportTargetOrNull("https://www.reddit.com/R/AskReddit/COMMENTS/ABC/title/DEF/", communityHint = "askreddit"),
        )
        assertEquals(ModeratorReportTarget("t3_xyz", null), moderatorReportTargetOrNull("https://redd.it/XYZ"))
    }

    @Test
    fun reportLookupStillRefusesLookalikeHostsAndPlainHttp() {
        assertNull(moderatorReportTargetOrNull("https://reddit.com.evil.com/r/x/comments/abc/"))
        assertNull(moderatorReportTargetOrNull("https://notreddit.com/r/x/comments/abc/"))
        assertNull(moderatorReportTargetOrNull("http://www.reddit.com/r/x/comments/abc/"))
        assertNull(moderatorReportTargetOrNull("https://www.reddit.com/r/y/comments/abc/", communityHint = "x"))
    }
}
