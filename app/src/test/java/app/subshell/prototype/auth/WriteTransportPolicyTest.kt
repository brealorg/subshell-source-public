package app.subshell.prototype.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.StoredOAuthClientSelection
import app.subshell.prototype.data.network.SubshellNetworkTransport
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import okhttp3.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DATALAYER01: mutations never use the retrying HTTP client. The shared
 * client has retryOnConnectionFailure(true), so OkHttp may send a request
 * again after a dropped connection — a second modmail reply, vote or post.
 * Every writer the real composition root builds must reach Reddit only
 * through a non-retrying call factory; a field named for reads may share
 * the retrying one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WriteTransportPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val writerField = Regex(
        "(Writer|Submitter|Deleter|Editor|Saver|Voter|Follower|Manager)$",
    )

    /**
     * Not a writer's own request path: the auth machinery behind it (token
     * refresh, identity, bootstrap, reached directly or through a captured
     * lambda) and read collaborators it calls afterwards (loaders, readers,
     * history updaters, which only GET).
     */
    private val notTheWritePath =
        Regex("(?i)capability|authoriz|coordinator|tokenClient|identityClient|bootstrapper|^arg\\$\\d+$|(loader|reader|updater)$")

    @Before
    fun nativeClientIsNotConfiguredInThisBuild() {
        assumeFalse(BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED)
    }

    @Test
    fun everyWriterReachesRedditOnlyThroughANonRetryingClient() {
        EncryptedAuthSessionStore(context, IdentityCipher()).saveOAuthClientSelection(
            StoredOAuthClientSelection(
                provider = OAuthProvider.REDDIT,
                mode = OAuthClientMode.REDREADER_COMPATIBILITY,
                clientId = "harness-client-01",
            ),
        )
        val runtime = AuthApplicationRuntimeFactory.create(context, IdentityCipher())

        val writers = instanceFields(runtime)
            .filter { (name, value) -> value != null && writerField.containsMatchIn(name) }
        assertTrue("the compatibility runtime builds writers", writers.size >= 20)

        val retrying = writers.flatMap { (name, value) ->
            callFactories(requireNotNull(value), path = name, seen = IdentityHashMap())
                .filter { (path, factory) ->
                    factory === SubshellNetworkTransport.sharedCallFactory &&
                        !path.substringAfterLast('.').contains("read", ignoreCase = true)
                }
                .map { (path, _) -> path }
        }

        assertEquals(emptyList<String>(), retrying.sorted())
    }

    private fun instanceFields(target: Any): List<Pair<String, Any?>> =
        generateSequence(target.javaClass as Class<*>?) { it.superclass }
            .takeWhile { it.name.startsWith("app.subshell.") }
            .flatMap { it.declaredFields.asSequence() }
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }
            .map { it.name to it.get(target) }
            .toList()

    /** Call factories reachable from [target] through its own (app) objects. */
    private fun callFactories(
        target: Any,
        path: String,
        seen: IdentityHashMap<Any, Unit>,
        depth: Int = 0,
    ): List<Pair<String, Call.Factory>> {
        if (depth > 3 || seen.put(target, Unit) != null) return emptyList()
        return instanceFields(target).flatMap { (name, value) ->
            when {
                value is Call.Factory -> listOf("$path.$name" to value)
                notTheWritePath.containsMatchIn(name) -> emptyList()
                value != null && value !is Enum<*> && value.javaClass.name.startsWith("app.subshell.") ->
                    callFactories(value, "$path.$name", seen, depth + 1)
                else -> emptyList()
            }
        }
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
