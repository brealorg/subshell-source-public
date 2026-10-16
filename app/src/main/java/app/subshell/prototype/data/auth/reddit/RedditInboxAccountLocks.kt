package app.subshell.prototype.data.auth.reddit

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// INBOX03_ONE_INBOX_LOCK_PER_ACCOUNT_V1: the inbox loader and the read writer
// write the same live-inbox rows, and the background worker builds its own
// runtime (its own loader and writer) in the same process. One process-wide
// lock per account orders them, so a refresh fetched before a read can no
// longer store the item unread again after the read. Always taken after the
// instance's own mutex, never before it.
internal object RedditInboxAccountLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun forAccount(accountStableId: String): Mutex =
        locks.computeIfAbsent(accountStableId.trim()) { Mutex() }
}

internal suspend fun <T> Mutex.withInboxAccountLock(
    accountStableId: String,
    block: suspend () -> T,
): T = withLock {
    RedditInboxAccountLocks.forAccount(accountStableId).withLock { block() }
}
