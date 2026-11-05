package io.github.brealorg.subshell.auth

import android.content.Context
import io.github.brealorg.subshell.data.auth.AndroidKeystoreAesGcmCipher
import io.github.brealorg.subshell.data.auth.AuthCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * AUTHSTART01_RUNTIME_PREPARED_OFF_MAIN_V1 (OCB-474): building the auth runtime
 * reads the stored OAuth client selection (Keystore decrypt + file read), and
 * the runtime's shape depends on it. MainActivity gets it here on IO before it
 * makes its AuthConnectViewModel and hands it to the ViewModel's factory.
 *
 * AUTHSTART01_EVERY_WINDOW_BUILDS_ITS_RUNTIME_OFF_MAIN_V1: [obtain] never builds
 * on the caller's thread. A failed build is tried once more on IO and then
 * fails; a runtime another window took first is built again on IO. (Before, a
 * failed preparation, a second window or a quick close-and-reopen built it on
 * the main thread in the ViewModel.)
 */
internal object AuthRuntimePreparation {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Deferred<AuthApplicationRuntime>? = null

    @Synchronized
    fun prepare(
        context: Context,
        cipher: () -> AuthCipher = { AndroidKeystoreAesGcmCipher() },
    ): Deferred<AuthApplicationRuntime> =
        pending?.takeUnless { it.isCompleted && (it.isCancelled || it.failed()) }
            ?: scope.async {
                AuthApplicationRuntimeFactory.create(context.applicationContext, cipher())
            }.also { pending = it }

    /** A runtime for one window: the prepared one, or one built on IO. Never on the caller's thread. */
    suspend fun obtain(
        context: Context,
        cipher: () -> AuthCipher = { AndroidKeystoreAesGcmCipher() },
    ): AuthApplicationRuntime {
        var lastFailure: Exception? = null
        repeat(2) {
            val deferred = prepare(context, cipher)
            val runtime = try {
                deferred.await()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (!deferred.isCancelled) throw cancelled
                null
            } catch (failure: Exception) {
                lastFailure = failure
                forget(deferred)
                null
            }
            if (runtime != null) {
                if (take(deferred)) return runtime
                // Another window took this one; build this window's own on IO.
                return withContext(Dispatchers.IO) {
                    AuthApplicationRuntimeFactory.create(context.applicationContext, cipher())
                }
            }
        }
        throw lastFailure ?: IllegalStateException("The auth runtime could not be built")
    }

    @Synchronized
    private fun take(deferred: Deferred<AuthApplicationRuntime>): Boolean {
        if (pending !== deferred) return false
        pending = null
        return true
    }

    @Synchronized
    private fun forget(deferred: Deferred<AuthApplicationRuntime>) {
        if (pending === deferred) pending = null
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun Deferred<*>.failed(): Boolean = getCompletionExceptionOrNull() != null

    @Synchronized
    internal fun clearForTests() {
        pending = null
    }
}

/**
 * AUTHSTART01_EVERY_WINDOW_BUILDS_ITS_RUNTIME_OFF_MAIN_V1: lives in the window's own
 * ViewModel store, so it says whether this window already has its AuthConnectViewModel
 * (a recreated activity) — a process-wide count could not tell a second window or a
 * window opened before the last one's ViewModel was cleared.
 */
internal class AuthStartGate : androidx.lifecycle.ViewModel() {
    @Volatile
    var authViewModelMade = false
}
