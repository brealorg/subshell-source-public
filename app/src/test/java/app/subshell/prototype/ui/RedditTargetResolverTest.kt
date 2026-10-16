package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditTargetResolverTest {
    @Test
    fun resolvesCanonicalRedditPostPermalinksAcrossSupportedHosts() {
        val expected = RedditTarget.Post("1abcxyz")

        assertEquals(
            expected,
            resolveRedditTarget(
                "https://www.reddit.com/r/android/comments/1abcxyz/example_title/",
            ),
        )
        assertEquals(
            expected,
            resolveRedditTarget(
                "https://old.reddit.com/r/android/comments/1abcxyz/example_title/",
            ),
        )
        assertEquals(
            expected,
            resolveRedditTarget("/r/android/comments/1abcxyz/example_title/"),
        )
        assertEquals(expected, resolveRedditTarget("t3_1abcxyz"))
        assertEquals(expected, resolveRedditTarget("https://redd.it/1abcxyz"))
    }

    @Test
    fun resolvesExactCommentPermalinkWithoutLosingPostIdentity() {
        assertEquals(
            RedditTarget.Comment(
                postId = "1abcxyz",
                commentId = "p3j14qt",
            ),
            resolveRedditTarget(
                "https://www.reddit.com/r/android/comments/1abcxyz/example_title/p3j14qt/?context=3",
            ),
        )
    }

    @Test
    fun resolvesCommunityAndUserTargets() {
        assertEquals(
            RedditTarget.Community("BoostForReddit"),
            resolveRedditTarget("https://www.reddit.com/r/BoostForReddit/"),
        )
        assertEquals(
            RedditTarget.User("brealorg"),
            resolveRedditTarget("https://reddit.com/user/brealorg/"),
        )
        assertEquals(
            RedditTarget.User("brealorg"),
            resolveRedditTarget("/u/brealorg"),
        )
    }

    @Test
    fun resolvesPostsInAUserProfile() {
        // REDDITURL01: rejected until 2026-09-25; a post in a user profile.
        assertEquals(
            RedditTarget.Post("1abcxyz"),
            resolveRedditTarget("https://www.reddit.com/user/brealorg/comments/1abcxyz"),
        )
        // REDDITURL04: rejected until 2026-09-26; the profile's comments tab.
        assertEquals(
            RedditTarget.User("brealorg", UserProfileLinkTab.COMMENTS),
            resolveRedditTarget("https://www.reddit.com/user/brealorg/comments"),
        )
    }

    @Test
    fun rejectsExternalAndUnsupportedRedditPaths() {
        assertNull(resolveRedditTarget("https://github.com/brealorg/subshell"))
        assertNull(resolveRedditTarget("https://youtube.com/watch?v=abc"))
        assertNull(resolveRedditTarget("https://redd.it/1abcxyz/extra"))
        assertNull(resolveRedditTarget("javascript:alert(1)"))
    }

    @Test
    fun actionViewLaunchUsesTheSameCanonicalResolver() {
        val launch = redditViewIntentLaunchOrNull(
            action = "android.intent.action.VIEW",
            rawUri = "https://www.reddit.com/r/android/comments/1abcxyz/title/p3j14qt/",
            requestId = 42L,
        )

        assertEquals(42L, launch?.requestId)
        assertEquals(
            "https://www.reddit.com/r/android/comments/1abcxyz/title/p3j14qt/",
            launch?.rawUri,
        )
        assertEquals(
            RedditTarget.Comment(postId = "1abcxyz", commentId = "p3j14qt"),
            launch?.rawUri?.let(::resolveRedditTarget),
        )
        assertNull(
            redditViewIntentLaunchOrNull(
                action = "android.intent.action.SEND",
                rawUri = "https://www.reddit.com/r/android/",
                requestId = 43L,
            ),
        )
        assertNull(
            redditViewIntentLaunchOrNull(
                action = "android.intent.action.VIEW",
                rawUri = "https://github.com/brealorg/subshell",
                requestId = 44L,
            ),
        )
    }

    @Test
    fun postTargetIdentityCanBeExtractedForRemoteResolution() {
        assertEquals("1abcxyz", RedditTarget.Post("1abcxyz").postIdOrNull())
        assertEquals(
            "1abcxyz",
            RedditTarget.Comment("1abcxyz", "p3j14qt").postIdOrNull(),
        )
        assertNull(RedditTarget.Community("android").postIdOrNull())
        assertNull(RedditTarget.User("brealorg").postIdOrNull())
    }

    @Test
    fun postMatchingNormalizesRedditFullnames() {
        assertTrue(redditTargetMatchesPost("1abcxyz", "t3_1ABCXYZ"))
        assertTrue(redditTargetMatchesPost("t3_1abcxyz", "1abcxyz"))
        assertFalse(redditTargetMatchesPost("1abcxyz", "t3_other"))
    }
}
