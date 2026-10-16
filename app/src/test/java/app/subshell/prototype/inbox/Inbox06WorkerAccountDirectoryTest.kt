package app.subshell.prototype.inbox

import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * INBOX06 (fixes INBOX02's regression, audit OCB-329/334): the background
 * worker builds a new runtime whose account directory hydrates
 * asynchronously; its placeholder snapshot has no signed-in accounts. The
 * "account gone" check must use the hydrated snapshot, or it cancels every
 * sync (and with it inbox notifications).
 */
class Inbox06WorkerAccountDirectoryTest {
    private val local = AccountSessionSummary(
        stableId = "local:fixture",
        username = "fixture",
        source = AccountSessionSource.LOCAL_FIXTURE,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Local",
    )
    private val signedIn = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:t2_me",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private class HydratingDirectory(
        private val placeholder: AccountSessionDirectorySnapshot,
        private val hydrated: () -> AccountSessionDirectorySnapshot,
    ) : AccountSessionDirectory {
        override fun snapshot() = placeholder
        override fun selectAccount(stableId: String) = placeholder
        override suspend fun hydratedSnapshot() = hydrated()
    }

    private fun placeholder() = AccountSessionDirectorySnapshot(listOf(local), local.stableId)

    @Test
    fun aSignedInAccountIsSeenOnceTheDirectoryHasLoaded() = runBlocking {
        val directory = HydratingDirectory(placeholder()) {
            AccountSessionDirectorySnapshot(listOf(local, signedIn), signedIn.stableId)
        }

        assertEquals(true, inboxWorkerAccountSignedIn(directory, signedIn.stableId))
    }

    @Test
    fun aLoggedOutAccountIsGone() = runBlocking {
        val directory = HydratingDirectory(placeholder()) { placeholder() }

        assertEquals(false, inboxWorkerAccountSignedIn(directory, signedIn.stableId))
    }

    @Test
    fun aDirectoryThatCannotLoadNeverCancelsTheSync() = runBlocking {
        val directory = HydratingDirectory(placeholder()) { error("store unreadable") }

        assertNull(inboxWorkerAccountSignedIn(directory, signedIn.stableId))
        assertNull(inboxWorkerAccountSignedIn(null, signedIn.stableId))
    }
}
