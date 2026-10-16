package app.subshell.prototype.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue17FeedEntryRefreshPolicyTest {
    private val repositoryRoot =
        File(requireNotNull(System.getProperty("user.dir"))).let { appDir ->
            appDir.parentFile ?: appDir
        }

    @Test
    fun authenticatedHomeEntryRevalidatesEvenWhenCacheAlreadyHasPosts() {
        assertEntryRevalidationPolicy(
            relativePath =
                "app/src/main/java/app/subshell/prototype/ui/screens/HomeScreen.kt",
            surfaceMarker = "entry-refresh surface=home",
        )
    }

    @Test
    fun authenticatedCommunityEntryRevalidatesEvenWhenCacheAlreadyHasPosts() {
        assertEntryRevalidationPolicy(
            relativePath =
                "app/src/main/java/app/subshell/prototype/ui/screens/CommunityScreen.kt",
            surfaceMarker = "entry-refresh surface=community",
        )
    }

    private fun assertEntryRevalidationPolicy(
        relativePath: String,
        surfaceMarker: String,
    ) {
        val source = File(repositoryRoot, relativePath).readText()
        val start = source.indexOf(
            "// ISSUE17_AUTHENTICATED_FEED_ENTRY_REVALIDATION_V1",
        )
        assertTrue("entry request gate must exist", start >= 0)

        val end = source.indexOf(
            "val pagingSnapshot = state.postPagingSnapshot(pagingKey)",
            start,
        )
        assertTrue("entry effect boundary must exist", end > start)

        val entryEffect = source.substring(start, end)
        // The claim is owned by state (FeedEntryRefreshGate), not by a
        // composition-scoped remember: returning from a post is not an entry.
        // Issue #40 successor: the same claim, told whether the reader is
        // past the first page.
        assertTrue(
            "entry effect must keep one-request claim",
            entryEffect.contains("val decision = state.claimFeedEntryRefresh(pagingKey)") ||
                (
                    entryEffect.contains("val decision = state.claimFeedEntryRefresh(") &&
                        entryEffect.contains(".isDeepForAutomaticRefresh(),")
                ),
        )
        assertFalse(
            "entry claim must not live in composition",
            source.contains("initialLiveLoadRequested"),
        )
        assertTrue(
            "entry effect must invoke authoritative refresh",
            entryEffect.contains("if (decision.execute) {"),
        )
        assertTrue(
            "entry effect must invoke authoritative refresh",
            entryEffect.contains("onRefreshRedditFeed("),
        )
        assertTrue(
            "entry effect must expose issue17 runtime evidence",
            entryEffect.contains(surfaceMarker),
        )
        assertFalse(
            "cached posts must not suppress entry revalidation",
            entryEffect.contains(
                "state.postPagingSnapshot(pagingKey).items.isEmpty()",
            ),
        )
    }
}
