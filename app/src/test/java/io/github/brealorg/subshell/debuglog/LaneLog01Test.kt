package io.github.brealorg.subshell.debuglog

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneLog01Test {
    private val directory: File = Files.createTempDirectory("lane-log").toFile()

    private fun logs() = directory.listFiles().orEmpty().sortedBy(File::getName)

    private fun allLines() = logs().flatMap(File::readLines)

    @Test
    fun aLineIsInTheFileAtOnceAndAFullFileRolls() {
        val files = LaneLogFiles(directory, maxFileBytes = 10, maxTotalBytes = 1_000, now = { 7L })

        files.append("12345678")
        assertEquals(listOf("12345678"), allLines())

        files.append("abcdefgh")

        assertEquals(2, logs().size)
        assertEquals(listOf("12345678", "abcdefgh"), allLines())
    }

    @Test
    fun theOldestFilesGoFirstAndTheTotalStaysUnderTheCap() {
        var clock = 0L
        val files = LaneLogFiles(directory, maxFileBytes = 10, maxTotalBytes = 30, now = { ++clock })

        (1..20).forEach { files.append("line-%03d".format(it)) }

        assertTrue(logs().sumOf(File::length) <= 30)
        assertEquals("line-020", allLines().last())
        assertFalse("line-001" in allLines())
    }

    @Test
    fun aNewStartKeepsWhatTheLastProcessWrote() {
        LaneLogFiles(directory, now = { 5L }).append("before")

        LaneLogFiles(directory, now = { 5L }).append("after")

        assertEquals(listOf("before", "after"), allLines())
    }

    @Test
    fun everyLineOfTheReaderIsCopied() {
        val files = LaneLogFiles(directory)

        copyLaneLog("one\ntwo\n".reader().buffered(), files)

        assertEquals(listOf("one", "two"), allLines())
    }

    @Test
    fun onlyALaneBuildOnARealDeviceKeepsALog() {
        assertTrue(laneLogRuns("dev2", "google/oriole/oriole:16"))
        assertFalse(laneLogRuns("", "google/oriole/oriole:16"))
        assertFalse(laneLogRuns("dev2", "robolectric"))
    }
}
