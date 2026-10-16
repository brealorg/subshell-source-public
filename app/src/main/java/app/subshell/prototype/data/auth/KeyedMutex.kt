package app.subshell.prototype.data.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// LOW3_LOCK_PER_KEY_V1 (OCB-469/608/610): one lock per key (an account, a feed
// or history query) instead of one lock for all of them, so a slow network
// call holds up only work on the same key. An entry lives only while someone
// holds or waits for it, so per-query keys (search terms) do not pile up.
internal class KeyedMutex {
    private class Entry {
        val mutex = Mutex()
        var users = 0
    }

    private val entries = HashMap<Any, Entry>()

    suspend fun <T> withLock(key: Any, block: suspend () -> T): T {
        val entry = synchronized(entries) {
            entries.getOrPut(key) { Entry() }.also { it.users++ }
        }
        try {
            return entry.mutex.withLock { block() }
        } finally {
            synchronized(entries) {
                entry.users--
                if (entry.users == 0) entries.remove(key)
            }
        }
    }
}
