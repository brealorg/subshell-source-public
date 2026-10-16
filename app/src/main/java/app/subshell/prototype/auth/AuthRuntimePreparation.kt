package app.subshell.prototype.auth

import android.content.Context
import app.subshell.prototype.data.auth.AndroidKeystoreAesGcmCipher
import app.subshell.prototype.data.auth.AuthCipher
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * AUTHSTART01_RUNTIME_PREPARED_OFF_MAIN_V1 (OCB-474): building the auth runtime
 * reads the stored OAuth client selection (Keystore decrypt + file read), and
 * the runtime's shape depends on it. MainActivity has it built here on IO
 * before it first touches the AuthConnectViewModel; the ViewModel takes the
 * prepared runtime and only builds one itself when none is ready (tests,
 * a second window).
 */
internal object AuthRuntimePreparation {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Deferred<AuthApplicationRuntime>? = null

    /** AuthConnectViewModel instances alive in this process. */
    val liveViewModels = AtomicInteger(0)

    @Synchronized
    fun prepare(
        context: Context,
        cipher: () -> AuthCipher = { AndroidKeystoreAesGcmCipher() },
    ): Deferred<AuthApplicationRuntime> =
        pending ?: scope.async {
            AuthApplicationRuntimeFactory.create(context.applicationContext, cipher())
        }.also { pending = it }

    /** The prepared runtime, once, if it is ready. */
    @Synchronized
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun takePrepared(): AuthApplicationRuntime? {
        val ready = pending?.takeIf { it.isCompleted && !it.isCancelled } ?: return null
        pending = null
        return runCatching { ready.getCompleted() }.getOrNull()
    }

    @Synchronized
    internal fun clearForTests() {
        pending = null
    }
}
