package io.github.brealorg.subshell.data.cache

import android.os.Looper
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** The Android Keystore did not answer in time. It says nothing about the value: nothing is replaced or marked lost for it. */
internal class KeystoreNotAnsweringException(message: String) : IllegalStateException(message)

/**
 * PRIVATEPREFS06_NO_THREAD_WAITS_LONG_FOR_THE_KEYSTORE_V1 (#296, 1.0.1): a Keystore call that never answers
 * (a hung system service) must not freeze the main thread, nor the cache thread the main thread waits for.
 *
 * Keystore calls run on one daemon thread of their own. A caller on the main thread or on the cache
 * thread waits at most [shortWaitMs]; any other caller (the start-up job, the background sweep) at most
 * [longWaitMs]. A call that does not answer in time throws [KeystoreNotAnsweringException]; the call itself
 * cannot be cancelled and keeps its thread, so while it has not answered every new call fails at once
 * (no waiting, no queue behind it) until it does. Callers read that as "cannot be opened now": a value
 * reads as missing, a write over it keeps the old value (SealedKeysPreferences), a cache row is a miss.
 */
internal object BoundedKeystoreCall {
    @Volatile
    internal var shortWaitMs: Long = 1_500L

    @Volatile
    internal var longWaitMs: Long = 8_000L

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "subshell-keystore").apply { isDaemon = true }
    }

    @Volatile
    private var unanswered: Future<*>? = null

    /** When the Keystore call that is running now started (System.nanoTime), 0 while none runs. */
    @Volatile
    private var runningSinceNanos: Long = 0L

    /**
     * Whether a thread that must not wait long should not even try: a Keystore call has run longer than
     * that thread would wait, so asking now can only end in waiting for nothing. Many reads on the main
     * thread each waiting their full time would add up to ANR.
     */
    fun shouldFailAtOnce(): Boolean {
        if (!isShortWaiter(Thread.currentThread())) return false
        if (unanswered?.isDone == false) return true
        val since = runningSinceNanos
        return since != 0L && (System.nanoTime() - since) / 1_000_000 > shortWaitMs
    }

    fun <T> call(block: () -> T): T {
        val thread = Thread.currentThread()
        if (thread.name == "subshell-keystore") return block()
        unanswered?.let { stuck ->
            if (!stuck.isDone) throw KeystoreNotAnsweringException("An earlier Keystore call has not answered")
            unanswered = null
        }
        if (shouldFailAtOnce()) throw KeystoreNotAnsweringException("A Keystore call has been running for a while")
        val future = synchronized(this) {
            executor.submit<T> {
                runningSinceNanos = System.nanoTime()
                try {
                    block()
                } finally {
                    runningSinceNanos = 0L
                }
            }
        }
        val waitMs = waitMsFor(thread)
        try {
            return future.get(waitMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            unanswered = future
            throw KeystoreNotAnsweringException("The Keystore did not answer in $waitMs ms")
        } catch (error: ExecutionException) {
            throw (error.cause as? RuntimeException) ?: IllegalStateException(error.cause)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw KeystoreNotAnsweringException("Interrupted while waiting for the Keystore")
        }
    }

    /** How long the calling thread may wait for the Keystore (or for a lock held by a thread that waits for it). */
    fun currentWaitMs(): Long = waitMsFor(Thread.currentThread())

    private fun isShortWaiter(thread: Thread): Boolean = isMainThread(thread) || thread.name == CACHE_THREAD_NAME

    private fun waitMsFor(thread: Thread): Long = if (isShortWaiter(thread)) shortWaitMs else longWaitMs

    private fun isMainThread(thread: Thread): Boolean =
        runCatching { Looper.getMainLooper()?.thread === thread }.getOrDefault(false)

    private const val CACHE_THREAD_NAME = "subshell-data03-room"
}

/** [inner] (the Android Keystore cipher) with every call bounded by [BoundedKeystoreCall]. */
internal class BoundedSealCipher(private val inner: SealCipher) : SealCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        BoundedKeystoreCall.call { inner.encrypt(plaintext, associatedData) }

    override fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray =
        BoundedKeystoreCall.call { inner.decrypt(envelope, associatedData) }
}
