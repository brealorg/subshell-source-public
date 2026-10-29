package app.subshell.prototype.data.diagnostics

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE263_NO_NSFW_COMMUNITY_OFFERED_IS_RECORDED_V1 (#263): Reddit offering
 * no NSFW community is kept in the diagnostics log as counts and the
 * account's own NSFW preferences, reaches the report, and the log line keeps
 * its older 27-field shape readable.
 */
class Issue263NoneOfferedDiagnosticsTest {
    private val directory: File = Files.createTempDirectory("issue263").toFile()

    private fun store() = DiagnosticEventStore(directory = directory, maxEvents = 50, maxBytes = 64 * 1024)

    @Test
    fun theEventRoundTripsThroughTheLogAndTheReport() {
        store().recordNsfwCommunitiesNoneOffered(
            popularCommunities = 49,
            searchedCommunities = 25,
            accountOver18 = true,
            accountSearchOver18 = null,
            nowEpochMs = 1_000L,
        )

        val event = store().snapshot().events.single()

        assertEquals(DiagnosticOperation.NSFW_COMMUNITIES_NONE_OFFERED, event.operation)
        assertEquals(DiagnosticSubsystem.COMMUNITY, event.subsystem)
        assertEquals(49, event.popularCommunities)
        assertEquals(25, event.searchedCommunities)
        assertEquals(true, event.accountOver18)
        assertNull(event.accountSearchOver18)

        val report = buildDiagnosticReport(store().snapshot(), ENVIRONMENT, generatedAtEpochMs = 2_000L)
        val line = report.lines().single { it.contains("operation=nsfw_communities_none_offered") }
        assertTrue(line, line.contains(" popular_communities=49 searched_communities=25 account_over_18=true"))
        assertTrue(line, !line.contains("account_search_include_over_18"))
    }

    @Test
    fun aLineFromBeforeStillReadsWithoutTheNewFields() {
        store().recordJankSummary(JankSummary(frames = 10, jankFrames = 2, worstFrameMs = 40, worstScreen = "home"))
        val log = File(directory, "events-v1.log")
        val lines = log.readLines()
        val eventLine = lines.single { it.startsWith("E|") }
        assertEquals(31, eventLine.split('|').size)
        log.writeText(listOf(lines.first(), eventLine.split('|').take(27).joinToString("|")).joinToString("\n", postfix = "\n"))

        val snapshot = store().snapshot()

        assertEquals(0, snapshot.invalidRecordCount)
        val event = snapshot.events.single()
        assertEquals(2, event.jankFrames)
        assertNull(event.popularCommunities)
        assertNull(event.accountOver18)
    }

    private companion object {
        val ENVIRONMENT = DiagnosticReportEnvironment(
            appVersionName = "0.86-beta",
            appVersionCode = 250,
            buildType = "debug",
            debugSlot = "dev1",
            androidApi = 33,
            androidRelease = "13",
            deviceManufacturer = "Xiaomi",
            deviceModel = "11i",
            screenWidthDp = 393,
            screenHeightDp = 873,
            smallestScreenWidthDp = 393,
        )
    }
}
