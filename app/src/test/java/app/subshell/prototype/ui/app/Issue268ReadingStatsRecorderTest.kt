package app.subshell.prototype.ui.app

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.reading.READING_STATS_HOME_TARGET
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE268_READING_STATS_COUNT_OPENS_AND_TIME_V1 and
 * ISSUE268_READING_TARGET_FOLLOWS_THE_SCREEN_V1 (#268): a new page is one
 * open, coming back is not, and time goes to what was in front.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue268ReadingStatsRecorderTest {
    private data class Write(val account: String, val target: String, val opens: Int, val postOpens: Int, val seconds: Long)

    private val writes = mutableListOf<Write>()
    private val recorder = ReadingStatsRecorder { account, target, opens, postOpens, seconds ->
        writes += Write(account, target, opens, postOpens, seconds)
    }

    private val pics = ReadingTarget("pics", ReadingTargetKind.COMMUNITY, "community:1:pics")
    private val picsPost = ReadingTarget("pics", ReadingTargetKind.POST, "post:7")
    private val home = ReadingTarget(READING_STATS_HOME_TARGET, ReadingTargetKind.HOME, "home")

    @Test
    fun aNewPageIsOneOpenAndComingBackIsNot() {
        recorder.update("a", pics, nowMs = 0)
        recorder.update("a", picsPost, nowMs = 10_000)
        recorder.update("a", pics, nowMs = 25_000)
        recorder.update("a", null, nowMs = 30_000) // app paused
        recorder.update("a", pics, nowMs = 90_000) // resumed on the same page

        assertEquals(
            listOf(
                Write("a", "pics", 1, 0, 0),
                Write("a", "pics", 0, 0, 10),
                Write("a", "pics", 0, 1, 0),
                Write("a", "pics", 0, 0, 15),
                Write("a", "pics", 0, 0, 5),
            ),
            writes,
        )
    }

    @Test
    fun homeCountsTimeButNoOpenAndAStretchIsCapped() {
        recorder.update("a", home, nowMs = 0)
        recorder.update("a", null, nowMs = 2 * 60 * 60 * 1_000L)

        assertEquals(listOf(Write("a", READING_STATS_HOME_TARGET, 0, 0, READING_STRETCH_MAX_SECONDS)), writes)
    }

    @Test
    fun anotherAccountIsAnotherVisitAndAFlushKeepsTheRest() {
        recorder.update("a", pics, nowMs = 0)
        recorder.flush(nowMs = 61_500)
        recorder.update("b", pics, nowMs = 70_000)

        assertEquals(
            listOf(
                Write("a", "pics", 1, 0, 0),
                Write("a", "pics", 0, 0, 61),
                Write("a", "pics", 0, 0, 9),
                Write("b", "pics", 1, 0, 0),
            ),
            writes,
        )
    }

    @Test
    fun theStateNamesHomeACommunityAndAPostsCommunity() {
        val state = SubshellState()
        assertEquals(ReadingTargetKind.HOME, state.readingTarget?.kind)

        state.openSubreddit("pics")
        val community = requireNotNull(state.readingTarget)
        assertEquals(ReadingTargetKind.COMMUNITY, community.kind)
        assertEquals("pics", community.communityName)

        val post = SampleData.posts.first()
        state.open(post)
        val opened = requireNotNull(state.readingTarget)
        assertEquals(ReadingTargetKind.POST, opened.kind)
        assertEquals(post.community, opened.communityName)

        state.closePost()
        assertEquals(community, state.readingTarget)
        state.navigateBack()
        state.openSubreddit("pics")
        assertNotEquals("opening the community again is a new visit", community.visitKey, state.readingTarget?.visitKey)
        state.open(post)
        assertNotEquals("a second open of the post is a new visit", opened.visitKey, state.readingTarget?.visitKey)
        assertEquals(ReadingTargetKind.POST, state.readingTarget?.kind)
    }
}
