package app.subshell.prototype.data.network.reddit

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class Moderation02A2ReportParserTest {
    @Test fun missingAndExplicitlyEmptyReportsStayDifferent() {
        val missing = moderatorReportDetails(null, null, null, null, null)
        assertNull(missing.userReports); assertNull(missing.moderatorReports); assertNull(missing.totalCount); assertNull(missing.ignored)
        val empty = moderatorReportDetails(null, JsonArray(emptyList()), JsonArray(emptyList()), JsonPrimitive(false), JsonPrimitive(true))
        assertEquals(emptyList<Any>(), empty.userReports); assertEquals(0, empty.totalCount); assertEquals(false, empty.ignored)
    }
    @Test fun tupleReasonsCountsAndSuppliedModeratorArePreserved() {
        val details = moderatorReportDetails(null,
            JsonArray(listOf(JsonArray(listOf(JsonPrimitive("Spam"), JsonPrimitive(4))))),
            JsonArray(listOf(JsonArray(listOf(JsonPrimitive("Rule 2"), JsonPrimitive("named_mod"))))),
            JsonPrimitive(true), JsonPrimitive(true))
        assertEquals("Spam", details.userReports?.single()?.reason)
        assertEquals(4, details.userReports?.single()?.count)
        assertNull(details.userReports?.single()?.moderatorName)
        assertEquals("named_mod", details.moderatorReports?.single()?.moderatorName)
        assertEquals(5, details.totalCount)
    }
    @Test fun unknownShapeOrStringBooleanIsNotCoercedToFalse() {
        val data = moderatorReportDetails(null, JsonNull, JsonPrimitive(false), JsonPrimitive("false"), JsonPrimitive("true"))
        assertNull(data.userReports); assertNull(data.moderatorReports); assertNull(data.ignored); assertNull(data.canModerate)
    }
    @Test fun malformedTuplePreservesKnownReasonWithoutInventingCount() {
        val data = moderatorReportDetails(null,
            JsonArray(listOf(JsonArray(listOf(JsonPrimitive("Known reason"), JsonPrimitive("five"))), JsonPrimitive(true))),
            JsonArray(emptyList()), JsonPrimitive(false), JsonPrimitive(true))
        assertEquals("Known reason", data.userReports?.single()?.reason)
        assertNull(data.userReports?.single()?.count); assertNull(data.totalCount); assertTrue(data.incomplete)
    }
    @Test fun serverTotalWinsButMissingSectionsAreStillUnknown() {
        val data = moderatorReportDetails(7, null, JsonArray(emptyList()), null, null)
        assertEquals(7, data.totalCount); assertNull(data.userReports)
    }
    @Test fun invalidCountsDoNotBecomeFakeZeroReports() {
        listOf(-1, 0, 10_001).forEach { count ->
            val data = moderatorReportDetails(null, JsonArray(listOf(JsonArray(listOf(JsonPrimitive("r"), JsonPrimitive(count))))), JsonArray(emptyList()), null, null)
            assertNull(data.totalCount); assertTrue(data.incomplete)
        }
    }
    @Test fun oversizedInputIsBoundedAndMarkedIncomplete() {
        val raw = JsonArray(List(101) { JsonArray(listOf(JsonPrimitive("x".repeat(2100)), JsonPrimitive(1))) })
        val data = moderatorReportDetails(null, raw, JsonArray(emptyList()), null, null)
        assertEquals(100, data.userReports?.size); assertEquals(2000, data.userReports?.first()?.reason?.length)
        assertTrue(data.incomplete); assertNull(data.totalCount)
    }
}
