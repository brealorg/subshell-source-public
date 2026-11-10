package io.github.brealorg.subshell.data.cache

import io.github.brealorg.subshell.data.RepositoryQueryKey
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.network.reddit.RedditSavedHistoryItem
import io.github.brealorg.subshell.data.reading.ReadingStatsStore
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRIVATECACHE02_SAVED_HISTORY_FILES_ARE_SEALED_V1 and PRIVATECACHE02_READING_COUNTS_ARE_SEALED_V1:
 * what the reader saved, voted on and hid, and which communities they open, is stored sealed.
 */
class PrivateCache02SealedFilesTest {
    private val directory: File = Files.createTempDirectory("privatecache02").toFile()
    private val key = RepositoryQueryKey("account|authenticated|reddit:me|saved|mixed")
    private val post = SampleData.posts.first()
    private val items = listOf<RedditSavedHistoryItem>(RedditSavedHistoryItem.PostItem(post))
    private var day = 20_000L

    @After
    fun clean() {
        directory.deleteRecursively()
    }

    private fun realSeal(keyByte: Int) = CipherCachePayloadSeal(SoftwareAesCipher(keyByte))

    private fun savedFile(): File = directory.listFiles { file -> file.extension == "json" }!!.single()

    @Test
    fun savedHistoryIsStoredSealedAndReadsAgain() {
        val store = RedditSavedHistoryCacheStore(directory, seal = realSeal(1))

        store.replace(key, items, nextCursor = "t3_after")

        val raw = savedFile().readText()
        assertTrue(raw.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertFalse(raw.contains(post.title))
        val read = store.read(key)
        assertEquals(1, read?.items?.size)
        assertEquals("t3_after", read?.nextCursor)
    }

    @Test
    fun savedHistoryReadWithAnotherKeyIsAMissAndTheFileIsRemoved() {
        RedditSavedHistoryCacheStore(directory, seal = realSeal(1)).replace(key, items, nextCursor = null)

        assertNull(RedditSavedHistoryCacheStore(directory, seal = realSeal(2)).read(key))
        assertTrue(directory.listFiles { file -> file.extension == "json" }!!.isEmpty())
    }

    @Test
    fun aSavedHistoryFileFromAnOlderBuildIsReadAndSealed() {
        RedditSavedHistoryCacheStore(directory).replace(key, items, nextCursor = "t3_after")
        assertFalse(savedFile().readText().startsWith(CachePayloadSeal.SEALED_PREFIX))

        val read = RedditSavedHistoryCacheStore(directory, seal = realSeal(1)).read(key)

        assertNotNull(read)
        assertEquals(1, read?.items?.size)
        assertTrue(savedFile().readText().startsWith(CachePayloadSeal.SEALED_PREFIX))
    }

    @Test
    fun readingCountsAreStoredSealedAndLoadAgain() {
        val store = ReadingStatsStore(directory, today = { day }, sealOf = { realSeal(1) })
        store.load()
        store.setEnabled(true)
        store.record("reddit:a", "subshell", opens = 2, seconds = 90)

        val raw = File(directory, "days-v1.log").readText()
        assertTrue(raw.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertFalse(raw.contains("subshell"))
        val again = ReadingStatsStore(directory, today = { day }, sealOf = { realSeal(1) })
        again.load()
        assertEquals("subshell", again.summary("reddit:a").communities.single().communityName)
    }

    @Test
    fun readingCountsThatCannotBeOpenedAreLeftAloneNotReplaced() {
        val first = ReadingStatsStore(directory, today = { day }, sealOf = { realSeal(1) })
        first.load()
        first.setEnabled(true)
        first.record("reddit:a", "subshell", opens = 2, seconds = 90)
        val before = File(directory, "days-v1.log").readText()

        // The key is not there (right after boot, or gone): nothing is read, and nothing is lost.
        val unreadable = ReadingStatsStore(directory, today = { day }, sealOf = { realSeal(2) })
        unreadable.load()
        unreadable.record("reddit:a", "pics", opens = 1, seconds = 10)

        assertEquals(before, File(directory, "days-v1.log").readText())
        // With the right key again, the earlier counts are still there.
        val back = ReadingStatsStore(directory, today = { day }, sealOf = { realSeal(1) })
        back.load()
        assertEquals("subshell", back.summary("reddit:a").communities.single().communityName)
    }

    @Test
    fun readingCountsFromAnOlderBuildAreReadAndSealedOnTheNextWrite() {
        val older = ReadingStatsStore(directory, today = { day })
        older.load()
        older.setEnabled(true)
        older.record("reddit:a", "subshell", opens = 1, seconds = 30)
        assertTrue(File(directory, "days-v1.log").readText().contains("subshell"))

        val store = ReadingStatsStore(directory, today = { day }, sealOf = { realSeal(1) })
        store.load()
        assertEquals("subshell", store.summary("reddit:a").communities.single().communityName)
        store.record("reddit:a", "subshell", opens = 1, seconds = 30)

        assertTrue(File(directory, "days-v1.log").readText().startsWith(CachePayloadSeal.SEALED_PREFIX))
    }
}
