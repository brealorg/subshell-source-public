package app.subshell.prototype.data.auth

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.normalizeRedditUsername
import app.subshell.prototype.data.redditUserSubmittedPostFeedQueryKey
import app.subshell.prototype.data.network.reddit.redditUserSubmittedJsonRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Profile01a1UserSubmittedFeedPolicyTest {
    @Test
    fun redditUsernameNormalizationAcceptsCanonicalUserPrefixes() {
        assertEquals(
            "profile_user",
            normalizeRedditUsername("/u/Profile_User"),
        )
        assertEquals(
            "profile-user",
            normalizeRedditUsername("u/Profile-User"),
        )
        assertEquals(
            "profileuser",
            normalizeRedditUsername("ProfileUser"),
        )
    }

    @Test
    fun userTargetIsExclusiveAndUsesNewSubmittedOrder() {
        val target = AuthenticatedRedditFeedTarget.user("/u/Profile_User")

        assertNull(target.mainFeed)
        assertNull(target.communityName)
        assertNull(target.searchQuery)
        assertEquals("profile_user", target.username)
        assertEquals("profile_user", target.normalizedUsername)
        assertEquals(RedditFeedSort.NEW, target.sort)
        assertEquals(RedditListingTimePeriod.ALL, target.timePeriod)
    }

    @Test
    fun userSubmittedQueryIdentityIsAccountScopedAndUsernameScoped() {
        val key = redditUserSubmittedPostFeedQueryKey(
            accountStableId =
                "reddit:redreader_compatibility:t2_profile01",
            username = "/u/Profile_User",
        )

        assertEquals(
            "account|authenticated|" +
                "reddit:redreader_compatibility:t2_profile01|" +
                "profile01a1|user|profile_user|submitted",
            key.value,
        )
    }

    @Test
    fun liveFeedPageRequestPreservesRefreshAndAppendSemanticsForUserTarget() {
        val accountStableId =
            "reddit:redreader_compatibility:t2_profile01"
        val target =
            AuthenticatedRedditFeedTarget.user("Profile_User")
        val expectedKey = redditUserSubmittedPostFeedQueryKey(
            accountStableId = accountStableId,
            username = "Profile_User",
        )

        val refresh = live02FeedPageRequest(
            accountStableId = accountStableId,
            operation = RepositoryLoadOperation.REFRESH,
            remoteCursor = "ignored-on-refresh",
            target = target,
        )

        assertEquals(expectedKey, refresh.queryKey)
        assertEquals(25, refresh.limit)
        assertNull(refresh.cursor)

        val append = live02FeedPageRequest(
            accountStableId = accountStableId,
            operation = RepositoryLoadOperation.APPEND,
            remoteCursor = "t3_profile_after",
            target = target,
        )

        assertEquals(expectedKey, append.queryKey)
        assertEquals(25, append.limit)
        assertEquals("t3_profile_after", append.cursor)
    }

    @Test
    fun submittedListingRequestUsesCanonicalEndpointNewOrderAndAfterCursor() {
        val request = redditUserSubmittedJsonRequest(
            username = "/u/Profile_User",
            request = RepositoryPageRequest(
                cursor = "t3_profile_after",
                limit = 25,
                operation = RepositoryLoadOperation.APPEND,
            ),
        )

        assertEquals(
            listOf("user", "profile_user", "submitted"),
            request.pathSegments,
        )
        assertEquals(
            listOf(
                "raw_json" to "1",
                "sort" to "new",
                "limit" to "25",
                "after" to "t3_profile_after",
            ),
            request.queryParameters,
        )
    }
}
