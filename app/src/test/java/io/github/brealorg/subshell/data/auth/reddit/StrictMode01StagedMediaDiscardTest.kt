package io.github.brealorg.subshell.data.auth.reddit

import java.io.File
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * STRICTMODE01_STAGED_MEDIA_IS_DELETED_OFF_THE_MAIN_THREAD_V1: a submit's
 * staged copy is deleted on the IO dispatcher, not on the thread the submit
 * ran on, and also when the submit was cancelled.
 */
class StrictMode01StagedMediaDiscardTest {
    private class RecordingFile(path: String) : File(path) {
        @Volatile var deletedOn: Thread? = null

        override fun delete(): Boolean {
            deletedOn = Thread.currentThread()
            return super.delete()
        }
    }

    @Test
    fun aCancelledSubmitStillDeletesItsStagedCopyOffTheCallingThread() = runBlocking {
        val staged = RecordingFile(File.createTempFile("staged", ".gif").path)
        val caller = Thread.currentThread()
        withTimeout(10_000) {
            val submit = launch {
                try {
                    awaitCancellation()
                } finally {
                    discardStagedMedia(staged)
                }
            }
            yield()
            submit.cancel()
            submit.join()
        }

        assertFalse(staged.exists())
        assertNotNull(staged.deletedOn)
        assertNotEquals(caller, staged.deletedOn)
    }
}
