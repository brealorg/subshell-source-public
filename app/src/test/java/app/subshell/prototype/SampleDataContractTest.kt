package app.subshell.prototype

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.MediaAsset
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.ThreadPlacement
import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.CommentActionPlacement
import app.subshell.prototype.ui.CommunitySort
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SearchArea
import app.subshell.prototype.ui.SearchScope
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.VideoAutoplay
import app.subshell.prototype.ui.VideoQuality
import app.subshell.prototype.ui.VideoTimelineSize
import app.subshell.prototype.ui.screens.followerCountLabel
import app.subshell.prototype.ui.screens.focusedCommentTopLevelIndex
import app.subshell.prototype.ui.screens.commentsForPostSession
import app.subshell.prototype.ui.screens.postCommentsFor
import app.subshell.prototype.ui.screens.profileTabsFor
import app.subshell.prototype.ui.screens.savedPostsForProfile
import app.subshell.prototype.ui.screens.upvotedPostsForProfile
import app.subshell.prototype.ui.sortCommunityPosts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleDataContractTest {
    private val repository = FakeSubshellRepository()

    @Test
    fun fixtureIdsAreUnique() {
        assertEquals(SampleData.posts.size, SampleData.posts.map { it.id }.distinct().size)
        assertEquals(SampleData.communities.size, SampleData.communities.map { it.id }.distinct().size)
        assertEquals(SampleData.users.size, SampleData.users.map { it.username }.distinct().size)
        assertEquals(SampleData.inbox.size, SampleData.inbox.map { it.id }.distinct().size)
        val comments = SampleData.comments.flatMap { it.flatten() }
        assertEquals(comments.size, comments.map { it.id }.distinct().size)
        assertEquals(
            SampleData.profileComments.size,
            SampleData.profileComments.map { it.id }.distinct().size,
        )
    }

    @Test
    fun videoLabCommunityContainsAlphabetFixtureMatrix() {
        val community = SampleData.communities.single {
            it.name == SampleData.VIDEO_LAB_COMMUNITY_NAME
        }
        val fixtures = SampleData.posts.filter {
            it.community == SampleData.VIDEO_LAB_COMMUNITY_NAME
        }
        val expectedIds = ('a'..'z').map { "video-lab-$it" }
        val expectedAssetNames = ('A'..'Z').map { "VIDEO_LAB_$it" }

        assertTrue(community.joined)
        assertEquals("26 fixtures", community.members)
        assertEquals(expectedIds, fixtures.map { it.id })
        assertTrue(fixtures.all { it.kind == PostKind.VIDEO })
        assertEquals(expectedAssetNames, fixtures.map { it.mediaAsset?.name })
        assertTrue(
            fixtures.zipWithNext().all { (first, second) ->
                first.score + first.comments * 2 > second.score + second.comments * 2
            },
        )
    }

    @Test
    fun adaptiveMediaLabContainsProviderNeutralSourceFixtures() {
        val community = SampleData.communities.single {
            it.name == SampleData.ADAPTIVE_MEDIA_LAB_COMMUNITY_NAME
        }
        val fixtures = SampleData.posts.filter {
            it.community == SampleData.ADAPTIVE_MEDIA_LAB_COMMUNITY_NAME
        }

        assertFalse(community.joined)
        assertEquals("5 fixtures", community.members)
        assertEquals(
            listOf(
                "media03-dash-adaptive",
                "media03-hls-adaptive",
                "media03-broken-primary-fallback",
                "media03-split-av",
                "media03-broken-audio-fallback",
            ),
            fixtures.map { it.id },
        )
        assertTrue(fixtures.all { it.kind == PostKind.VIDEO })
        assertEquals(
            listOf(
                MediaAsset.MEDIA03_DASH,
                MediaAsset.MEDIA03_HLS,
                MediaAsset.MEDIA03_FALLBACK,
                MediaAsset.MEDIA03_SPLIT_AV,
                MediaAsset.MEDIA03_SPLIT_AUDIO_FALLBACK,
            ),
            fixtures.map { it.mediaAsset },
        )

        val syncSlate = fixtures.single { it.id == "media03-split-av" }
        assertTrue(syncSlate.title.contains("deterministic split A/V sync slate"))
        assertTrue(syncSlate.body.contains("full-frame white flash"))
        assertTrue(syncSlate.body.contains("1500 Hz click"))
        assertTrue(syncSlate.mediaLabel.contains("flash + click"))
    }

    @Test
    fun productMapHasExactlyFourMainDestinations() {
        assertEquals(
            listOf("Home", "Communities", "Inbox", "You"),
            MainDestination.entries.map { it.label },
        )
    }

    @Test
    fun accountSwitchingUsesKnownAccountsAndReturnsHome() {
        val persistedUsernames = mutableListOf<String>()
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            persistActiveAccountUsername = persistedUsernames::add,
        )

        state.navigate(MainDestination.INBOX)
        state.switchAccount("packetmoss")

        assertEquals("packetmoss", state.activeAccountUsername)
        assertEquals(MainDestination.HOME, state.destination)
        assertEquals(listOf("packetmoss"), persistedUsernames)
    }

    @Test
    fun v5FixturesCoverExpectedRedditSurfaces() {
        assertTrue(PostKind.entries.all { kind -> SampleData.posts.any { it.kind == kind } })
        assertTrue(SampleData.posts.count { it.kind == PostKind.GIF } >= 3)
        val galleries = SampleData.posts.filter { it.kind == PostKind.GALLERY }
        assertTrue(galleries.size >= 2)
        assertTrue(galleries.all { it.gallery.size == 4 })
        assertTrue(galleries.flatMap { it.gallery }.all { it.format == MediaFormat.GIF })
        assertTrue(SampleData.posts.any { it.pinned && it.moderatorPost })
        assertTrue(SampleData.posts.any { it.spoiler })
        assertTrue(SampleData.posts.any { it.nsfw })
        assertTrue(SampleData.posts.any { it.domain != null })
        assertTrue(SampleData.posts.any { it.crosspost != null })
        assertTrue(SampleData.posts.any { it.poll?.options?.size == 4 })
    }

    @Test
    fun commentsCoverInlineMediaCodeRolesAndEmoji() {
        val comments = SampleData.comments.flatMap { it.flatten() }
        assertTrue(comments.count { it.inlineMedia != null } >= 2)
        assertTrue(comments.any { it.codeSample?.language == "bash" })
        assertTrue(comments.any { it.codeSample?.language == "kotlin" })
        assertTrue(comments.any { it.isOp })
        assertTrue(comments.any { it.isModerator })
        assertTrue(comments.any { it.body.contains("🔥") })
        assertTrue(comments.any { it.body.contains("¯\\_(ツ)_/¯") })
    }

    @Test
    fun stateInteractionsAreReversibleAndContextAware() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val post = SampleData.posts.first {
            it.id !in SampleData.defaultSavedPostIds &&
                it.id !in SampleData.defaultUpvotedPostIds
        }
        val communityId = SampleData.communities.first { !it.joined }.id

        state.toggleSaved(post.id)
        assertTrue(post.id in state.savedPostIds)
        state.toggleSaved(post.id)
        assertFalse(post.id in state.savedPostIds)

        state.vote(post.id, 1)
        assertEquals(1, state.votes[post.id])
        state.vote(post.id, 1)
        assertFalse(post.id in state.votes)

        state.toggleCommunity(communityId)
        assertTrue(communityId in state.joinedCommunityIds)
        state.toggleCommunity(communityId)
        assertFalse(communityId in state.joinedCommunityIds)

        state.open(post)
        assertTrue(post.id in state.readPostIds)
        state.openMedia(post, 2)
        assertEquals(post, state.selectedMediaPost)
        assertEquals(2, state.selectedMediaIndex)
        state.closeMedia()
        assertNull(state.selectedMediaPost)

        val pollPost = SampleData.posts.single { it.kind == PostKind.POLL }
        state.votePoll(pollPost.id, "comments")
        assertEquals("comments", state.pollVotes[pollPost.id])

        state.revealMedia("spoiler-rack")
        assertTrue("spoiler-rack" in state.revealedMediaIds)
        assertTrue(state.feedVideoMuted)
        state.toggleVideoMuted()
        assertFalse(state.feedVideoMuted)
    }

    @Test
    fun directionalChromeStaysHiddenUntilUpwardIntent() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.updateTopBarBehavior(ChromeBehavior.HIDE_ON_SCROLL)
        state.updateBottomBarBehavior(ChromeBehavior.HIDE_ON_SCROLL)

        state.onMainContentScroll(deltaPx = 55f, atTop = false, hideThresholdPx = 56f, showThresholdPx = 40f)
        assertTrue(state.chromeVisible)
        state.onMainContentScroll(deltaPx = 1f, atTop = false, hideThresholdPx = 56f, showThresholdPx = 40f)
        assertFalse(state.chromeVisible)
        state.onMainContentScroll(deltaPx = 0f, atTop = false, hideThresholdPx = 56f, showThresholdPx = 40f)
        assertFalse(state.chromeVisible)
        state.onMainContentScroll(deltaPx = -39f, atTop = false, hideThresholdPx = 56f, showThresholdPx = 40f)
        assertFalse(state.chromeVisible)
        state.onMainContentScroll(deltaPx = -1f, atTop = false, hideThresholdPx = 56f, showThresholdPx = 40f)
        assertTrue(state.chromeVisible)
        state.onMainContentScroll(deltaPx = 1f, atTop = true, hideThresholdPx = 56f, showThresholdPx = 40f)
        assertTrue(state.chromeVisible)
    }

    @Test
    fun searchAndMediaDefaultsMatchTheProductDecision() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.openSearch()
        assertEquals(SearchArea.ALL_SUBSHELL, state.searchArea)
        assertEquals(SearchScope.ALL, state.searchScope)
        assertEquals("Search subshell", state.currentSearchHint)
        assertEquals("All subshell", state.currentSearchAreaLabel)
        state.closeSearch()

        state.navigate(MainDestination.SUBS)
        state.openSearch()
        assertEquals(SearchArea.MY_COMMUNITIES, state.searchArea)
        assertEquals(
            listOf(SearchScope.ALL, SearchScope.POSTS, SearchScope.COMMUNITIES),
            state.availableSearchScopes,
        )
        state.closeSearch()

        state.openSubreddit("linux")
        assertEquals(MainDestination.SUBS, state.destination)
        state.openSearch()
        assertEquals(SearchArea.CURRENT_COMMUNITY, state.searchArea)
        assertEquals("r/linux", state.currentSearchAreaLabel)
        assertTrue(state.matchesSearchArea("linux"))
        assertFalse(state.matchesSearchArea("android"))
        assertEquals(listOf(SearchScope.POSTS), state.availableSearchScopes)

        assertTrue(state.openKeyboardOnSearch)
        assertEquals(VideoAutoplay.ALWAYS, state.videoAutoplay)
        assertTrue(state.startVideosMuted)
        assertEquals(VideoQuality.HIGH, state.wifiVideoQuality)
        assertEquals(VideoQuality.MEDIUM, state.mobileVideoQuality)
        assertEquals(VideoTimelineSize.STANDARD, state.videoTimelineSize)
        assertTrue(state.landscapeVideoGesturesEnabled)
        assertTrue(state.landscapeBrightnessGestureEnabled)
        assertTrue(state.landscapeVolumeGestureEnabled)
        state.updateSearchScope(SearchScope.USERS)
        state.updateSearchQuery("spritewitch")
        state.submitSearch()
        assertEquals("spritewitch", state.recentSearches.first())
    }

    @Test
    fun closingSearchRestoresTheExactOriginState() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.feedSort = app.subshell.prototype.ui.FeedSort.CONTROVERSIAL
        state.openSubreddit("android")

        state.openSearch()
        state.updateSearchQuery("battery")
        state.updateSearchArea(SearchArea.ALL_SUBSHELL)
        state.closeSearch()

        assertEquals(MainDestination.HOME, state.destination)
        assertEquals("android", state.selectedSubreddit)
        assertEquals(app.subshell.prototype.ui.FeedSort.CONTROVERSIAL, state.feedSort)
        assertEquals("battery", state.searchQuery)
        assertFalse(state.searchVisible)
    }

    @Test
    fun communityNavigationRestoresItsFeedOrSearchOrigin() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        state.openSubreddit("subshell")
        assertEquals("subshell", state.selectedSubreddit)
        assertEquals(MainDestination.HOME, state.destination)
        state.closeSubreddit()
        assertNull(state.selectedSubreddit)
        assertEquals(MainDestination.HOME, state.destination)

        state.openSearch()
        state.updateSearchQuery("linux")
        state.openSubreddit("linux")
        assertFalse(state.searchVisible)
        state.closeSubreddit()
        assertTrue(state.searchVisible)
        assertEquals("linux", state.searchQuery)
    }

    @Test
    fun backUnwindsNestedNavigationAndExitsThroughHome() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        state.navigate(MainDestination.SUBS)
        state.openSubreddit("android")
        state.openSearch()
        state.updateSearchQuery("linux")
        state.openSubreddit("linux")
        state.openSubreddit("subshell")

        assertTrue(state.navigateBack())
        assertEquals("linux", state.selectedSubreddit)
        assertFalse(state.searchVisible)

        assertTrue(state.navigateBack())
        assertEquals("android", state.selectedSubreddit)
        assertTrue(state.searchVisible)
        assertEquals("linux", state.searchQuery)

        assertTrue(state.navigateBack())
        assertEquals("android", state.selectedSubreddit)
        assertTrue(state.searchVisible)
        assertEquals("", state.searchQuery)

        assertTrue(state.navigateBack())
        assertEquals("android", state.selectedSubreddit)
        assertFalse(state.searchVisible)

        assertTrue(state.navigateBack())
        assertNull(state.selectedSubreddit)
        assertEquals(MainDestination.SUBS, state.destination)

        assertTrue(state.navigateBack())
        assertEquals(MainDestination.HOME, state.destination)
        assertFalse(state.canNavigateBack)
        assertFalse(state.navigateBack())
    }

    @Test
    fun communityFixturesExerciseTheFiveRedditListingSorts() {
        val posts = SampleData.posts.filter { it.community == "subshell" }

        assertEquals(12, posts.size)
        assertTrue(PostKind.entries.all { kind -> posts.any { it.kind == kind } })
        assertEquals(
            mapOf(
                CommunitySort.HOT to "tap-target-thread",
                CommunitySort.NEW to "compose-baseline-fresh",
                CommunitySort.TOP to "old-design-reference",
                CommunitySort.CONTROVERSIAL to "controversial-comment-marathon",
            ),
            CommunitySort.entries
                .filterNot { it == CommunitySort.RISING }
                .associateWith { sort -> sortCommunityPosts(posts, sort).first().id },
        )
        assertTrue(
            CommunitySort.entries
                .map { sort -> sortCommunityPosts(posts, sort).map { it.id } }
                .distinct()
                .size >= 4,
        )
    }

    @Test
    fun userProfilesResolveEveryAuthorAndRestoreTheirOrigin() {
        val allAuthors = SampleData.posts.map { it.author } +
            SampleData.comments.flatMap { it.flatten() }.map { it.author } +
            SampleData.profileComments.map { it.author }
        assertTrue(allAuthors.all { SampleData.userFor(it).username == it })

        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val originPost = SampleData.posts.first()
        val profilePost = SampleData.posts.first { it.author == "packetmoss" && it.id != originPost.id }

        state.open(originPost)
        val originPostVisit = state.postVisitId
        state.openUser("packetmoss")
        assertNull(state.selectedPost)
        assertEquals("packetmoss", state.selectedUser)

        state.open(profilePost)
        assertNull(state.selectedUser)
        assertEquals(profilePost, state.selectedPost)
        state.closePost()
        assertEquals("packetmoss", state.selectedUser)

        state.closeUser()
        assertNull(state.selectedUser)
        assertEquals(originPost, state.selectedPost)
        assertEquals(originPostVisit, state.postVisitId)
    }

    @Test
    fun directPostOpenGetsFreshEntryAfterCommentDeepLink() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val target = SampleData.profileComments.first()
        val post = SampleData.posts.single { it.id == target.postId }

        state.open(post, target.id)
        val commentVisit = state.postVisitId
        assertEquals(target.id, state.focusedCommentId)

        state.closePost()
        state.open(post)
        assertEquals(commentVisit + 1, state.postVisitId)
        assertNull(state.focusedCommentId)
    }

    @Test
    fun shelluserRemainsACompleteExternalProfileFixture() {
        val user = SampleData.userFor("shelluser")
        val posts = SampleData.posts.filter { it.author == user.username }
        val comments = SampleData.profileComments.filter { it.author == user.username }

        assertTrue(user.bannerAsset != null)
        assertEquals(6, posts.size)
        assertTrue(posts.map { it.kind }.distinct().size >= 4)
        assertEquals(6, comments.size)
        assertTrue(comments.all { fixture -> SampleData.posts.any { it.id == fixture.postId } })
        assertEquals(listOf("Posts", "Comments"), profileTabsFor(user.username, SampleData.LOCAL_USERNAME))
        assertTrue(savedPostsForProfile(
            user.username,
            SampleData.LOCAL_USERNAME,
            SampleData.posts,
            SampleData.defaultSavedPostIds,
        ).isEmpty())
        assertTrue(upvotedPostsForProfile(
            user.username,
            SampleData.LOCAL_USERNAME,
            SampleData.posts,
            mapOf("terminal-widget" to 1),
        ).isEmpty())
    }

    @Test
    fun everyNamedProfileHasEnoughPublicContentAndThreadPlacement() {
        SampleData.users.forEach { user ->
            val posts = SampleData.posts.filter { it.author == user.username }
            val comments = SampleData.profileComments.filter { it.author == user.username }

            assertTrue("${user.username} should have a banner", user.bannerAsset != null)
            assertTrue("${user.username} should have at least three posts", posts.size >= 3)
            assertTrue("${user.username} should have at least three comments", comments.size >= 3)
            assertEquals(
                "${user.username} should exercise top, middle and bottom thread placement",
                ThreadPlacement.entries.toSet(),
                comments.map { it.placement }.toSet(),
            )
        }
    }

    @Test
    fun profileCommentsKeepTheirDeclaredThreadPlacement() {
        SampleData.profileComments.forEach { fixture ->
            val post = SampleData.posts.single { it.id == fixture.postId }
            val comments = postCommentsFor(post, repository)
            val index = comments.indexOfFirst { it.id == fixture.id }

            when (fixture.placement) {
                ThreadPlacement.TOP -> assertEquals("${fixture.id} should be first", 0, index)
                ThreadPlacement.MIDDLE -> assertTrue(
                    "${fixture.id} should be inside the thread",
                    index in 1 until comments.lastIndex,
                )
                ThreadPlacement.BOTTOM -> assertEquals(
                    "${fixture.id} should be last",
                    comments.lastIndex,
                    index,
                )
            }
        }
    }

    @Test
    fun everyCommentDeepLinkResolvesInsideItsPostThread() {
        val profileTargets = SampleData.profileComments.map { fixture -> fixture.postId to fixture.id }
        val inboxTargets = SampleData.inbox.mapNotNull { item ->
            val postId = item.targetPostId
            val commentId = item.targetCommentId
            if (postId != null && commentId != null) postId to commentId else null
        }

        (profileTargets + inboxTargets).forEach { (postId, commentId) ->
            val post = SampleData.posts.single { it.id == postId }
            assertTrue(
                "Comment $commentId should resolve inside post $postId",
                focusedCommentTopLevelIndex(postCommentsFor(post, repository), commentId) >= 0,
            )
        }
    }

    @Test
    fun authenticatedPostUsesOnlyMatchingLiveCommentState() {
        val post = SampleData.posts.first()
        val liveComment = SampleData.comments.first().copy(id = "t1_live03")
        var localRepositoryRead = false
        val comments = commentsForPostSession(
            post = post,
            accountStableId = "reddit:redreader_compatibility:t2_live",
            authenticated = true,
            redditCommentsState = RedditCommentsUiState(
                accountStableId = "reddit:redreader_compatibility:t2_live",
                postId = post.id,
                comments = listOf(liveComment),
            ),
            repositoryComments = {
                localRepositoryRead = true
                SampleData.comments
            },
        )

        assertEquals(listOf(liveComment), comments)
        assertFalse(localRepositoryRead)
        assertTrue(
            commentsForPostSession(
                post = post,
                accountStableId = "reddit:redreader_compatibility:t2_other",
                authenticated = true,
                redditCommentsState = RedditCommentsUiState(
                    accountStableId = "reddit:redreader_compatibility:t2_live",
                    postId = post.id,
                    comments = listOf(liveComment),
                ),
                repositoryComments = { SampleData.comments },
            ).isEmpty(),
        )
    }

    @Test
    fun followingAUserIsReversibleAndUpdatesExactFollowerCounts() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        assertFalse(state.isFollowingUser(SampleData.LOCAL_USERNAME))
        state.toggleFollowUser(SampleData.LOCAL_USERNAME.uppercase())
        assertFalse(state.isFollowingUser(SampleData.LOCAL_USERNAME))

        assertFalse(state.isFollowingUser("shelluser"))
        state.toggleFollowUser("ShellUser")
        assertTrue(state.isFollowingUser("shelluser"))
        assertEquals("215", followerCountLabel("214", following = true))
        assertEquals("1.2K", followerCountLabel("1.2K", following = true))

        state.toggleFollowUser("shelluser")
        assertFalse(state.isFollowingUser("SHELLUSER"))
        assertEquals("214", followerCountLabel("214", following = false))
    }

    @Test
    fun rootOwnsPrivateSavedAndUpvotedHistoryData() {
        assertEquals("root", SampleData.LOCAL_USERNAME)
        assertEquals("root", SampleData.userFor("ROOT").username)
        assertEquals(listOf("Posts", "Comments", "Saved", "Upvotes", "Downvotes", "Hidden", "Blocked", "Drafts", "Reading"), profileTabsFor("root", SampleData.LOCAL_USERNAME))

        val rootPosts = SampleData.posts.filter { it.author == SampleData.LOCAL_USERNAME }
        val rootComments = SampleData.profileComments.filter { it.author == SampleData.LOCAL_USERNAME }
        assertEquals(3, rootPosts.size)
        assertEquals(3, rootComments.size)
        assertEquals(ThreadPlacement.entries.toSet(), rootComments.map { it.placement }.toSet())

        val saved = savedPostsForProfile(
            "root",
            SampleData.LOCAL_USERNAME,
            SampleData.posts,
            SampleData.defaultSavedPostIds,
        )
        assertEquals(SampleData.defaultSavedPostIds, saved.mapTo(linkedSetOf()) { it.id })
        assertTrue(saved.any { it.author != SampleData.LOCAL_USERNAME })

        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val upvoted = upvotedPostsForProfile(
            "root",
            SampleData.LOCAL_USERNAME,
            state.posts,
            state.votes,
        )
        assertEquals(SampleData.defaultUpvotedPostIds, upvoted.mapTo(linkedSetOf()) { it.id })
        assertTrue(upvoted.all { state.votes[it.id] == 1 })

        val firstUpvoted = upvoted.first()
        state.vote(firstUpvoted.id, 1)
        assertFalse(firstUpvoted in upvotedPostsForProfile(
            "root",
            SampleData.LOCAL_USERNAME,
            state.posts,
            state.votes,
        ))
        state.vote(firstUpvoted.id, 1)
        assertTrue(firstUpvoted in upvotedPostsForProfile(
            "root",
            SampleData.LOCAL_USERNAME,
            state.posts,
            state.votes,
        ))
    }

    @Test
    fun inboxFixturesOpenPostsOrARealConversation() {
        assertTrue(SampleData.inbox.any { it.targetPostId != null })
        assertTrue(SampleData.inbox.any { it.conversation.size >= 3 })
        assertTrue(SampleData.inbox.all { !it.age.contains("Yesterday", ignoreCase = true) })
    }

    private fun Comment.flatten(): List<Comment> = listOf(this) + children.flatMap { it.flatten() }
}
