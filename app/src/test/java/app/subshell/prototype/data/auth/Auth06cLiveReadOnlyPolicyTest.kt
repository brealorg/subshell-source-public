package app.subshell.prototype.data.auth

import app.subshell.prototype.data.COMMENTS02_MORE_BATCH_SIZE
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RedditListingTimePeriod
import app.subshell.prototype.data.RedditFeedSort
import app.subshell.prototype.data.RedditMainFeed
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.account.RedditFeedUiState
import app.subshell.prototype.data.account.shouldStartRequest
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.auth.reddit.toRedditListingSort
import app.subshell.prototype.data.network.reddit.RedditListingSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Auth06cLiveReadOnlyPolicyTest {
    @Test
    fun redReaderCompatibilityUsesOnlyIdentityAndReadScopes() {
        assertEquals(
            setOf("identity", "mysubreddits", "read"),
            RedditOAuth.coreReadScopes,
        )
        val selection = RedditOAuth.selectRuntimeConfiguration(
            storedSelection = RedditOAuth.redReaderCompatibilitySelection(
                "auth06c_fixture_client",
            ),
            nativeConfigured = false,
            nativeClientId = "",
            nativeRedirectUri = "",
            nativeUserAgent = "native-unused",
        )
        assertEquals(RedditOAuth.coreReadScopes, selection?.configuration?.scopes)
        assertFalse(selection?.configuration?.scopes.orEmpty().any { scope ->
            scope in setOf("edit", "flair", "history", "modposts", "save", "submit", "subscribe", "vote")
        })
        assertTrue(
            OAuthTokenSet(
                accessToken = "access",
                refreshToken = "refresh",
                tokenType = "bearer",
                scope = "identity mysubreddits read submit",
                expiresAtEpochMs = Long.MAX_VALUE,
            ).grants(RedditOAuth.coreReadScopes),
        )
    }

    @Test
    fun initialLiveProofRequestIsOneBestPageOfTwentyFiveWrittenUnderHomeQueryKey() {
        val accountStableId = "reddit:redreader_compatibility:t2_auth06c"
        val request = auth06cInitialFeedRequest(accountStableId)
        assertEquals(auth06cHomeBestQueryKey(accountStableId), request.queryKey)
        assertEquals(
            "account|authenticated|reddit:redreader_compatibility:t2_auth06c|main|home|best",
            request.queryKey.value,
        )
        assertEquals(25, request.limit)
        assertEquals(RepositoryLoadOperation.REFRESH, request.operation)
        assertEquals(null, request.cursor)
    }

    @Test
    fun syncingReadOnlyFeedIsSanitizedAndInProgress() {
        assertTrue(
            app.subshell.prototype.data.account.ConnectAccountUiState(
                phase = app.subshell.prototype.data.account.ConnectAccountPhase.SYNCING_REDDIT_FEED,
                providerLabel = "Reddit · RedReader compatibility",
            ).inProgress,
        )
        assertTrue(
            app.subshell.prototype.data.account.ConnectAccountFailureKind.REDDIT_FEED in
                app.subshell.prototype.data.account.ConnectAccountFailureKind.entries,
        )
    }

    @Test
    fun live02RefreshAndAppendAreExplicitPagesOfTwentyFiveWithRemoteCursorOnly() {
        val accountStableId = "reddit:redreader_compatibility:t2_live02"
        val refresh = live02FeedPageRequest(
            accountStableId = accountStableId,
            operation = RepositoryLoadOperation.REFRESH,
            remoteCursor = "ignored-on-refresh",
        )
        assertEquals(auth06cHomeBestQueryKey(accountStableId), refresh.queryKey)
        assertEquals(25, refresh.limit)
        assertEquals(null, refresh.cursor)

        val append = live02FeedPageRequest(
            accountStableId = accountStableId,
            operation = RepositoryLoadOperation.APPEND,
            remoteCursor = "t3_remote_after",
        )
        assertEquals(auth06cHomeBestQueryKey(accountStableId), append.queryKey)
        assertEquals(25, append.limit)
        assertEquals("t3_remote_after", append.cursor)
    }

    @Test
    fun feed02HomeListingsHaveDistinctAccountScopedKeys() {
        val accountStableId = "reddit:redreader_compatibility:t2_feed02"
        val keys = RedditFeedSort.entries.map { sort ->
            authenticatedRedditFeedQueryKey(
                accountStableId,
                AuthenticatedRedditFeedTarget.home(sort),
            )
        }

        assertEquals(RedditFeedSort.entries.size, keys.distinct().size)
        assertEquals(
            listOf("best", "hot", "new", "rising", "top|all", "controversial|all"),
            keys.map { key -> key.value.substringAfter("|main|home|") },
        )
        assertTrue(keys.all { key ->
            key.value.startsWith("account|authenticated|$accountStableId|main|home|")
        })
        assertEquals(
            listOf(
                RedditListingSort.BEST,
                RedditListingSort.HOT,
                RedditListingSort.NEW,
                RedditListingSort.RISING,
                RedditListingSort.TOP,
                RedditListingSort.CONTROVERSIAL,
            ),
            RedditFeedSort.entries.map { sort ->
                AuthenticatedRedditFeedTarget.home(sort).toRedditListingSort()
            },
        )
    }

    @Test
    fun feed02bPopularAndAllAreTypedListingsWithDistinctAccountScopedKeys() {
        val accountStableId = "reddit:redreader_compatibility:t2_feed02b"
        val popular = AuthenticatedRedditFeedTarget.main(
            feed = RedditMainFeed.POPULAR,
            sort = RedditFeedSort.HOT,
        )
        val all = AuthenticatedRedditFeedTarget.main(
            feed = RedditMainFeed.ALL,
            sort = RedditFeedSort.TOP,
            timePeriod = RedditListingTimePeriod.YEAR,
        )

        assertEquals("popular", popular.listingCommunityName)
        assertEquals("all", all.listingCommunityName)
        assertEquals(
            "account|authenticated|$accountStableId|main|popular|hot",
            authenticatedRedditFeedQueryKey(accountStableId, popular).value,
        )
        assertEquals(
            "account|authenticated|$accountStableId|main|all|top|year",
            authenticatedRedditFeedQueryKey(accountStableId, all).value,
        )
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticatedRedditFeedTarget.main(
                feed = RedditMainFeed.POPULAR,
                sort = RedditFeedSort.BEST,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticatedRedditFeedTarget.main(
                feed = RedditMainFeed.ALL,
                sort = RedditFeedSort.BEST,
            )
        }
    }

    @Test
    fun feed02TimePeriodsAreExplicitCanonicalAndCacheIsolated() {
        val accountStableId = "reddit:redreader_compatibility:t2_feed02_time"
        assertEquals(
            listOf("hour", "day", "week", "month", "year", "all"),
            RedditListingTimePeriod.entries.map(RedditListingTimePeriod::apiValue),
        )

        val topKeys = RedditListingTimePeriod.entries.map { timePeriod ->
            authenticatedRedditFeedQueryKey(
                accountStableId,
                AuthenticatedRedditFeedTarget.home(RedditFeedSort.TOP, timePeriod),
            )
        }
        assertEquals(RedditListingTimePeriod.entries.size, topKeys.distinct().size)
        assertEquals(
            "account|authenticated|$accountStableId|main|home|top|week",
            topKeys[RedditListingTimePeriod.WEEK.ordinal].value,
        )
        assertEquals(
            "account|authenticated|$accountStableId|live04|community|android|controversial|week",
            authenticatedRedditFeedQueryKey(
                accountStableId,
                AuthenticatedRedditFeedTarget.community(
                    "android",
                    RedditFeedSort.CONTROVERSIAL,
                    RedditListingTimePeriod.WEEK,
                ),
            ).value,
        )

        val hot = AuthenticatedRedditFeedTarget.home(
            RedditFeedSort.HOT,
            RedditListingTimePeriod.WEEK,
        )
        assertEquals(RedditListingTimePeriod.ALL, hot.effectiveTimePeriod)
        assertEquals(
            "account|authenticated|$accountStableId|main|home|hot",
            authenticatedRedditFeedQueryKey(accountStableId, hot).value,
        )
    }

    @Test
    fun live02RejectsAutomaticInitialLoadsAndAppendWithoutStoredRemoteCursor() {
        val accountStableId = "reddit:redreader_compatibility:t2_live02"
        assertThrows(IllegalArgumentException::class.java) {
            live02FeedPageRequest(
                accountStableId = accountStableId,
                operation = RepositoryLoadOperation.INITIAL,
                remoteCursor = null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            live02FeedPageRequest(
                accountStableId = accountStableId,
                operation = RepositoryLoadOperation.APPEND,
                remoteCursor = " ",
            )
        }
    }

    @Test
    fun live04CommunityListingsAreNormalizedAccountScopedAndUserPagedByTwentyFive() {
        val firstAccount = "reddit:redreader_compatibility:t2_live04_first"
        val secondAccount = "reddit:redreader_compatibility:t2_live04_second"
        val target = AuthenticatedRedditFeedTarget.community(
            name = "/r/MotoGP",
            sort = RedditFeedSort.HOT,
        )
        val firstKey = authenticatedRedditFeedQueryKey(firstAccount, target)
        val secondKey = authenticatedRedditFeedQueryKey(secondAccount, target)
        assertEquals(
            "account|authenticated|reddit:redreader_compatibility:t2_live04_first|" +
                "live04|community|motogp|hot",
            firstKey.value,
        )
        assertTrue(firstKey != secondKey)
        val uiState = RedditFeedUiState(
            accountStableId = firstAccount,
            queryKey = firstKey,
        )
        assertTrue(uiState.matches(firstAccount, firstKey))
        assertFalse(uiState.matches(secondAccount, firstKey))

        val refresh = live02FeedPageRequest(
            accountStableId = firstAccount,
            operation = RepositoryLoadOperation.REFRESH,
            remoteCursor = "ignored",
            target = target,
        )
        assertEquals(firstKey, refresh.queryKey)
        assertEquals(25, refresh.limit)
        assertEquals(null, refresh.cursor)

        val append = live02FeedPageRequest(
            accountStableId = firstAccount,
            operation = RepositoryLoadOperation.APPEND,
            remoteCursor = "t3_live04_after",
            target = target,
        )
        assertEquals(firstKey, append.queryKey)
        assertEquals(25, append.limit)
        assertEquals("t3_live04_after", append.cursor)
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticatedRedditFeedTarget.community(
                name = "r/not-valid!",
                sort = RedditFeedSort.BEST,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticatedRedditFeedTarget.community(
                name = "android",
                sort = RedditFeedSort.BEST,
            )
        }
    }

    @Test
    fun activeRequestDeduplicatesOnlyTheSameFeedAndAllowsAChangedTarget() {
        val accountStableId = "reddit:redreader_compatibility:t2_latest_request"
        val firstKey = authenticatedRedditFeedQueryKey(
            accountStableId,
            AuthenticatedRedditFeedTarget.community("androiddev", RedditFeedSort.HOT),
        )
        val secondKey = authenticatedRedditFeedQueryKey(
            accountStableId,
            AuthenticatedRedditFeedTarget.community("androiddev", RedditFeedSort.NEW),
        )
        val loadingFirst = RedditFeedUiState(
            accountStableId = accountStableId,
            queryKey = firstKey,
            inProgressOperation = RepositoryLoadOperation.REFRESH,
        )

        assertFalse(loadingFirst.shouldStartRequest(accountStableId, firstKey))
        assertTrue(loadingFirst.shouldStartRequest(accountStableId, secondKey))
        assertTrue(RedditFeedUiState().shouldStartRequest(accountStableId, firstKey))
    }

    @Test
    fun live05SearchIsNormalizedAccountScopedReadOnlyAndExplicitlyPagedByTwentyFive() {
        val firstAccount = "reddit:redreader_compatibility:t2_live05_first"
        val secondAccount = "reddit:redreader_compatibility:t2_live05_second"
        val target = AuthenticatedRedditFeedTarget.search(
            query = "  Valentino   Rossi  ",
            communityName = "/r/MotoGP",
        )
        val firstKey = authenticatedRedditFeedQueryKey(firstAccount, target)
        val secondKey = authenticatedRedditFeedQueryKey(secondAccount, target)

        assertEquals("Valentino Rossi", target.normalizedSearchQuery)
        assertEquals("motogp", target.normalizedCommunityName)
        assertEquals(
            "account|authenticated|reddit:redreader_compatibility:t2_live05_first|" +
                "live05|search|motogp|valentino rossi",
            firstKey.value,
        )
        assertTrue(firstKey != secondKey)

        val refresh = live02FeedPageRequest(
            accountStableId = firstAccount,
            operation = RepositoryLoadOperation.REFRESH,
            remoteCursor = "ignored",
            target = target,
        )
        assertEquals(firstKey, refresh.queryKey)
        assertEquals(25, refresh.limit)
        assertEquals(null, refresh.cursor)

        val append = live02FeedPageRequest(
            accountStableId = firstAccount,
            operation = RepositoryLoadOperation.APPEND,
            remoteCursor = "t3_live05_after",
            target = target,
        )
        assertEquals(firstKey, append.queryKey)
        assertEquals(25, append.limit)
        assertEquals("t3_live05_after", append.cursor)

        assertThrows(IllegalArgumentException::class.java) {
            AuthenticatedRedditFeedTarget.search("x")
        }
    }

    @Test
    fun live03CommentCacheIsAccountScopedAndRejectsUnsupportedPostIds() {
        val firstAccount = "reddit:redreader_compatibility:t2_first"
        val secondAccount = "reddit:redreader_compatibility:t2_second"
        val firstKey = live03CommentCacheKey(firstAccount, "t3_abc123")
        val secondKey = live03CommentCacheKey(secondAccount, "abc123")
        val topKey = live03CommentCacheKey(
            firstAccount,
            "abc123",
            RedditCommentSort.TOP,
        )

        assertEquals(
            "account|authenticated|reddit:redreader_compatibility:t2_first|" +
                "live03|comments|t3_abc123",
            firstKey,
        )
        assertTrue(firstKey != secondKey)
        assertTrue(topKey.endsWith("live03|comments|t3_abc123|top"))
        assertThrows(IllegalArgumentException::class.java) {
            live03CommentCacheKey(firstAccount, "t1_not-a-post")
        }
        assertEquals(100, LIVE03_COMMENT_LIMIT)
        assertEquals(8, LIVE03_COMMENT_DEPTH)
        assertEquals(25, COMMENTS02_MORE_BATCH_SIZE)
    }

    @Test
    fun live03UiStateMatchesOnlyTheRequestedAccountAndPost() {
        val state = RedditCommentsUiState(
            accountStableId = "reddit:redreader_compatibility:t2_live",
            postId = "t3_post",
            sort = RedditCommentSort.TOP,
            loading = true,
        )
        assertTrue(
            state.matches(
                accountStableId = "reddit:redreader_compatibility:t2_live",
                postId = "t3_post",
                sort = RedditCommentSort.TOP,
            ),
        )
        assertFalse(
            state.matches(
                accountStableId = "reddit:redreader_compatibility:t2_other",
                postId = "t3_post",
            ),
        )
        assertFalse(
            state.matches(
                accountStableId = "reddit:redreader_compatibility:t2_live",
                postId = "t3_post",
                sort = RedditCommentSort.BEST,
            ),
        )
    }
}
