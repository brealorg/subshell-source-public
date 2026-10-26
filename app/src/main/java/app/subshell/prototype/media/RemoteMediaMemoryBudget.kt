package app.subshell.prototype.media

import kotlin.math.ceil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

/**
 * SECAUDIT13_MEDIA_BYTES_SHARE_ONE_MEMORY_BUDGET_V1 (security audit #13): a
 * displayed image or GIF is read whole into memory (images up to 40 MB, GIFs
 * up to 100 MB) and decoded from there, so a feed of large GIFs loading side
 * by side could exhaust the heap, and the out-of-memory error could land on
 * another thread. Every read and decode reserves its bytes from one budget
 * first; a load that does not fit waits until earlier ones finish. A load
 * larger than the whole budget runs alone.
 */
internal class RemoteMediaMemoryBudget(limitBytes: Long) {
    private val permits = (limitBytes / MIB).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    private val semaphore = Semaphore(permits)

    // One reservation collects its permits at a time, so two large loads never
    // each hold half of the budget and wait for the other.
    private val collecting = Mutex()

    suspend fun <T> withReservation(bytes: Long, block: suspend () -> T): T {
        val wanted = ceil(bytes.coerceAtLeast(1L) / MIB.toDouble()).toLong()
            .coerceIn(1L, permits.toLong()).toInt()
        var held = 0
        try {
            collecting.withLock {
                while (held < wanted) {
                    semaphore.acquire()
                    held += 1
                }
            }
            return block()
        } finally {
            repeat(held) { semaphore.release() }
        }
    }

    internal val availableMib: Int
        get() = semaphore.availablePermits

    companion object {
        private const val MIB = 1_024L * 1_024L

        /** A quarter of the app's heap, shared by every displayed image and GIF. */
        val shared: RemoteMediaMemoryBudget by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            RemoteMediaMemoryBudget(Runtime.getRuntime().maxMemory() / 4)
        }
    }
}
