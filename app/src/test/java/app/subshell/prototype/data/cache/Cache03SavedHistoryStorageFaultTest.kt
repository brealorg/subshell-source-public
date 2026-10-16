package app.subshell.prototype.data.cache

import app.subshell.prototype.data.RepositoryQueryKey
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * CACHE03: saved-history cache faults are storage faults. A write that
 * cannot create its directory or replace its file throws IOException (the
 * loader reports STORAGE, retryable) instead of IllegalStateException
 * (reported as a non-retryable PROTOCOL failure); an unreadable file is
 * removed instead of blocking every later append.
 */
class Cache03SavedHistoryStorageFaultTest {
    private val key = RepositoryQueryKey("account|authenticated|reddit:me|saved|mixed")

    @Test
    fun aDirectoryThatCannotBeCreatedIsAnIoFault() {
        val parent = Files.createTempDirectory("cache03").toFile()
        try {
            val blocker = File(parent, "saved").apply { writeText("not a directory") }
            val store = RedditSavedHistoryCacheStore(File(blocker, "history"))

            assertThrows(IOException::class.java) {
                store.replace(queryKey = key, items = emptyList(), nextCursor = null)
            }
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun anUnreadableFileReadsAsNotCachedAndIsRemoved() {
        val directory = Files.createTempDirectory("cache03").toFile()
        try {
            val store = RedditSavedHistoryCacheStore(directory)
            store.replace(queryKey = key, items = emptyList(), nextCursor = "t3_after")
            val file = directory.listFiles()!!.single()
            file.writeText("{ not json")

            assertNull(store.read(key))
            assertFalse(file.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
