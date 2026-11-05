package io.github.brealorg.subshell.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.auth.AuthCipher
import io.github.brealorg.subshell.data.auth.EncryptedAuthSessionStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #42 (package rename): the Keystore key got a new name, so the sign-in and Reddit setup an older
 * version stored cannot be read; the app used to stop on "Couldn't read Reddit setup". With the key
 * missing and files present they are discarded. ISSUE42_STORED_SIGN_IN_FROM_ANOTHER_KEY_IS_DISCARDED_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue42StoredSignInFromAnotherKeyTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private class Cipher(var hasKey: Boolean?, private val throws: Boolean = false) : AuthCipher {
        var lookups = 0
        var retiredDeletes = 0
        override fun encrypt(plaintext: ByteArray) = plaintext
        override fun decrypt(envelope: ByteArray) = envelope
        override fun deleteKey() = Unit
        override fun keyExists(): Boolean? {
            lookups += 1
            if (throws) throw java.security.KeyStoreException("issue56")
            return hasKey
        }
        override fun deleteRetiredKey() {
            retiredDeletes += 1
        }
    }

    @Before
    fun freshProcess() {
        EncryptedAuthSessionStore.resetKeyCheckForTests(context)
    }

    private fun storedFiles(): List<File> {
        val base = context.noBackupFilesDir
        return listOf(EncryptedAuthSessionStore.AUTH_DIRECTORY_NAME, EncryptedAuthSessionStore.AUTH_CONFIGURATION_DIRECTORY_NAME)
            .map { name -> File(base, name).apply { mkdirs() } }
            .map { directory -> File(directory, "old.v1").apply { writeBytes(byteArrayOf(1, 2, 3)) } }
    }

    @Test
    fun filesEncryptedWithAKeyThatIsGoneAreDiscarded() {
        val files = storedFiles()

        EncryptedAuthSessionStore.discardFilesFromAnotherKey(context, Cipher(hasKey = false))

        files.forEach { file -> assertFalse(file.path, file.exists()) }
    }

    @Test
    fun filesAreKeptWhileTheirKeyExists() {
        val files = storedFiles()

        EncryptedAuthSessionStore.discardFilesFromAnotherKey(context, Cipher(hasKey = true))

        files.forEach { file -> assertTrue(file.path, file.exists()) }
    }

    @Test
    fun aFreshInstallWithNoFilesIsLeftAlone() {
        File(context.noBackupFilesDir, EncryptedAuthSessionStore.AUTH_DIRECTORY_NAME).deleteRecursively()
        File(context.noBackupFilesDir, EncryptedAuthSessionStore.AUTH_CONFIGURATION_DIRECTORY_NAME).deleteRecursively()

        EncryptedAuthSessionStore.discardFilesFromAnotherKey(context, Cipher(hasKey = false))

        assertFalse(File(context.noBackupFilesDir, EncryptedAuthSessionStore.AUTH_DIRECTORY_NAME).exists())
    }

    /** ISSUE56_A_FAILED_KEY_LOOKUP_IS_UNKNOWN_V1 */
    @Test
    fun aKeyStoreThatCannotTellDiscardsNothingAndAsksAgainNextTime() {
        val files = storedFiles()
        val unknown = Cipher(hasKey = null)

        EncryptedAuthSessionStore.discardFilesFromAnotherKey(context, unknown)
        EncryptedAuthSessionStore.discardFilesFromAnotherKey(context, Cipher(hasKey = true, throws = true))

        files.forEach { file -> assertTrue(file.path, file.exists()) }
        assertEquals(0, unknown.retiredDeletes)
        val known = Cipher(hasKey = false)
        EncryptedAuthSessionStore.discardFilesFromAnotherKey(context, known)
        assertEquals(1, known.lookups)
        files.forEach { file -> assertFalse(file.path, file.exists()) }
    }

    /** ISSUE59_THE_RETIRED_KEY_IS_DELETED_ONCE_V1 */
    @Test
    fun theCheckRunsOnceDeletesTheRetiredKeyAndIsRememberedAcrossProcesses() {
        storedFiles()
        val first = Cipher(hasKey = true)

        repeat(3) { EncryptedAuthSessionStore.discardFilesFromAnotherKey(context, first) }

        assertEquals(1, first.lookups)
        assertEquals(1, first.retiredDeletes)
        val marker = File(File(context.noBackupFilesDir, "auth-state"), EncryptedAuthSessionStore.KEY_CHECK_MARKER_FILE)
        assertTrue(marker.isFile)

        // A new process: the marker answers, the key store is not asked.
        EncryptedAuthSessionStore.resetKeyCheckForTests(context)
        marker.parentFile?.mkdirs()
        marker.writeText("settled\n")
        val later = Cipher(hasKey = false)
        EncryptedAuthSessionStore.discardFilesFromAnotherKey(context, later)
        assertEquals(0, later.lookups)
        assertEquals(0, later.retiredDeletes)
    }
}
