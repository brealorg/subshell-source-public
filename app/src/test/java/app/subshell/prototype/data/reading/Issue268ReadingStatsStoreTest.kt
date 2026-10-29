package app.subshell.prototype.data.reading

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE268_READING_STATS_STAY_ON_THE_DEVICE_V1 (#268): counts per account and
 * community, kept for 30 days, nothing while off, deleted when turned off.
 */
class Issue268ReadingStatsStoreTest {
    private val directory: File = Files.createTempDirectory("reading").toFile()
    private var day = 20_000L

    private fun store() = ReadingStatsStore(directory, today = { day })

    @Test
    fun nothingIsRecordedWhileOff() {
        val store = store()
        store.load()
        assertEquals(false, store.enabled.value)

        store.record("reddit:a", "pics", opens = 1, seconds = 60)

        assertTrue(store.summary("reddit:a").communities.isEmpty())
        assertFalse(File(directory, "days-v1.log").exists())
    }

    @Test
    fun countsAddUpPerAccountAndCommunityAndSurviveANewStore() {
        store().setEnabled(true)
        store().apply {
            record("reddit:a", "r/Pics", opens = 1)
            record("reddit:a", "pics", postOpens = 2, seconds = 90)
            record("reddit:a", READING_STATS_HOME_TARGET, seconds = 600)
            record("reddit:a", "news", seconds = 30)
            record("reddit:b", "pics", opens = 5, seconds = 999)
        }

        val summary = ReadingStatsStore(directory, today = { day }).summary("reddit:a")

        assertEquals(listOf(CommunityReading("pics", 1, 2, 90), CommunityReading("news", 0, 0, 30)), summary.communities)
        assertEquals(600L, summary.homeSeconds)
        assertEquals(720L, summary.totalSeconds)
        val file = File(directory, "days-v1.log").readText()
        assertFalse("no account ID on disk", file.contains("reddit:a"))
    }

    @Test
    fun onlyTheLastThirtyDaysCount() {
        val store = store()
        store.setEnabled(true)
        store.record("reddit:a", "old", seconds = 60)
        day += READING_STATS_WINDOW_DAYS
        store.record("reddit:a", "new", seconds = 60)

        val summary = store().summary("reddit:a")

        assertEquals(listOf("new"), summary.communities.map { it.communityName })
        assertEquals(READING_STATS_WINDOW_DAYS, summary.days)
        assertFalse(File(directory, "days-v1.log").readText().contains("|old|"))
    }

    @Test
    fun suggestionsWaitFourteenDaysFromTurningOn() {
        val store = store()
        store.setEnabled(true)
        day += READING_STATS_SUGGESTION_MIN_DAYS - 2
        assertFalse(store.summary("reddit:a").suggestionsReady)
        day += 1
        assertTrue(store.summary("reddit:a").suggestionsReady)
    }

    @Test
    fun turningOffDeletesTheCountsAndClearStartsAgainToday() {
        val store = store()
        store.setEnabled(true)
        store.record("reddit:a", "pics", seconds = 60)
        day += 3

        store.clear()
        assertTrue(store.summary("reddit:a").communities.isEmpty())
        assertEquals(day, store.summary("reddit:a").sinceEpochDay)

        store.record("reddit:a", "pics", seconds = 60)
        store.setEnabled(false)
        assertFalse(File(directory, "days-v1.log").exists())
        store.setEnabled(true)
        assertTrue(store().summary("reddit:a").communities.isEmpty())
    }

    @Test
    fun aTargetThatIsNoCommunityNameIsNotStored() {
        val store = store()
        store.setEnabled(true)
        store.record("reddit:a", "has space", seconds = 60)
        store.record("reddit:a", "pipe|name", seconds = 60)

        assertTrue(store.summary("reddit:a").communities.isEmpty())
    }
}
