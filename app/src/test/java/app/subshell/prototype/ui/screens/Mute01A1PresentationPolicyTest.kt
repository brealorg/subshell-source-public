package app.subshell.prototype.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mute01A1PresentationPolicyTest {
    private fun source(relative: String): String {
        val path = File("src/main/java/app/subshell/prototype/$relative")
        check(path.isFile) { "Missing source fixture: ${path.path}" }
        return path.readText()
    }

    @Test
    fun postOverflowUsesTimedMuteSheetInsteadOfPreviewToast() {
        val source = source("ui/components/Common.kt")

        assertTrue(source.contains("Mute ${'$'}{state.subredditLabel(post.community)}…"))
        assertTrue(source.contains("CommunityMuteSheet("))
        assertTrue(source.contains("state.muteCommunity(post.community, expiresAtEpochMillis)"))
        assertTrue(source.contains("state.unmuteCommunity(post.community)"))
        assertFalse(source.contains("Mute flow preview"))
    }

    @Test
    fun communitySurfaceOffersMuteAndUnmuteWithoutBlockingDirectAccess() {
        val source = source("ui/screens/CommunityScreen.kt")

        assertTrue(source.contains("\"Mute community…\""))
        assertTrue(source.contains("\"Unmute community\""))
        assertTrue(source.contains("CommunityMuteSheet("))
        assertTrue(source.contains("state.openSearch()"))
        assertTrue(source.contains("communityFeedPostsInDisplayOrder("))
    }

    @Test
    fun homeProjectionFiltersBeforeLazyItemsAndRefreshesAtExpiry() {
        val source = source("ui/screens/HomeScreen.kt")
        val projection = source.indexOf("filterMutedCommunityPosts(")
        // ISSUE234_FEED_POST_LAYOUTS_V1: the posts are emitted through feedPostItems now.
        val lazyItems = source.indexOf("feedPostItems(visiblePosts")

        assertTrue(projection >= 0)
        assertTrue(lazyItems > projection)
        assertTrue(source.contains("state.millisUntilNextCommunityMuteExpiry()"))
        assertTrue(source.contains("state.refreshCommunityMuteClock()"))
        assertTrue(source.contains("Muted communities fill this page"))
    }

    @Test
    fun durationSheetIncludesPresetsCustomTimeAndIndefiniteMute() {
        val source = source("ui/components/CommunityMuteSheet.kt")

        assertTrue(source.contains("CommunityMutePreset.entries"))
        assertTrue(source.contains("\"Until…\""))
        assertTrue(source.contains("DatePickerDialog("))
        assertTrue(source.contains("TimePickerDialog("))
        assertTrue(source.contains("CommunityMutePreset.INDEFINITE"))
    }
}
