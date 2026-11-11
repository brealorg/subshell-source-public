package io.github.brealorg.subshell.debugkeystore

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import io.github.brealorg.subshell.data.cache.AndroidKeystoreSealCipher
import io.github.brealorg.subshell.data.cache.CachePayloadSeal
import io.github.brealorg.subshell.data.cache.SealCipher
import java.io.File

/**
 * SLOWSEAL01_A_DEV_BUILD_CAN_HAVE_A_SLOW_KEYSTORE_V1 (#296): some phones answer a Keystore call in tens
 * of milliseconds, not three. A dev build adds that delay to every seal and open when the file
 * `files/seal-delay-ms` holds a number (write it with `run-as`, then restart the app), so the start-up
 * and the cache sweep can be watched on a phone with a fast Keystore. All of it is in src/debug.
 */
internal const val SLOW_SEAL_DELAY_FILE = "seal-delay-ms"

/** The word that makes every Keystore call hang for good (a Keystore that never answers). */
internal const val HANG_FOREVER = "hang"

/**
 * The delay in milliseconds a file's text asks for; 0 (none) for anything that is not a number, at most
 * 5 000; [HANG_FOREVER] is [Long.MAX_VALUE].
 */
internal fun parseSealDelayMs(text: String?): Long {
    val trimmed = text?.trim() ?: return 0L
    if (trimmed == HANG_FOREVER) return Long.MAX_VALUE
    return trimmed.toLongOrNull()?.coerceIn(0L, 5_000L) ?: 0L
}

internal class DelayedSealCipher(private val inner: SealCipher, private val delayMs: Long) : SealCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        Thread.sleep(delayMs)
        return inner.encrypt(plaintext, associatedData)
    }

    override fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray {
        Thread.sleep(delayMs)
        return inner.decrypt(envelope, associatedData)
    }
}

/** Android starts a provider with the process, before the application, so main needs no line for this. */
class SlowSealProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val appContext = context ?: return false
        val delayMs = runCatching { parseSealDelayMs(File(appContext.filesDir, SLOW_SEAL_DELAY_FILE).readText()) }
            .getOrDefault(0L)
        if (delayMs > 0) {
            // The delay is on the Keystore cipher only (the data key's unwrapping and the e1 values), so
            // it shows what the e2 seal saves: the software AES under the data key is not delayed.
            CachePayloadSeal.production = { context ->
                CachePayloadSeal.envelopeSealFor(
                    context,
                    DelayedSealCipher(AndroidKeystoreSealCipher(CachePayloadSeal.CACHE_KEY_ALIAS), delayMs),
                )
            }
            android.util.Log.i("SubshellStartup", "debug: every seal and open is delayed by $delayMs ms")
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
