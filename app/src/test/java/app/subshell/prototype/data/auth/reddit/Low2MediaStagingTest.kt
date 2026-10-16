package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteTokenSource
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOW2: media staging leaks and lost reasons (OCB-494, OCB-504/522, OCB-524).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Low2MediaStagingTest {
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "t2me")
    private var requests = 0
    private val network = OkHttpClient.Builder().addInterceptor { chain ->
        requests++
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(500).message("unused")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }.build()
    private val capability = object : AuthCommentWriteTokenSource {
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.Authenticated(
                StoredOAuthSession(
                    provider = OAuthProvider.REDDIT,
                    tokens = OAuthTokenSet("access", "refresh", "bearer", RedditOAuth.liveCommentEditScopes.joinToString(" "), Long.MAX_VALUE),
                    identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                    clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                ),
            )
    }

    @Test
    fun aCommentImageThatIsTooLargeSaysSo() = runBlocking {
        val submitter = ConfiguredRedditCommentSubmitter(
            capability = capability,
            callFactory = network,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            mediaSource = { _, _ -> RedditMediaStageResult.TooLarge },
            mediaUploadCallFactory = network,
            mediaWebSocketClient = network,
        )

        val result = submitter.submit(
            AuthenticatedCommentSubmitRequest(
                accountStableId = account,
                parentFullname = "t3_post",
                text = "look",
                media = AuthenticatedCommentSubmitMediaAttachment("content://x/1", "image/png"),
            ),
        )

        assertEquals(AuthenticatedCommentSubmitFailureKind.MEDIA_TOO_LARGE, (result as AuthenticatedCommentSubmitResult.Failed).kind)
    }

    @Test
    fun aDispatchMarkThatCannotBeSavedSendsNothingAndIsRetryable() = runBlocking {
        val submitter = ConfiguredRedditTextPostSubmitter(
            capability = capability,
            callFactory = network,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            onFinalSubmitDispatched = { _, _ -> throw IllegalStateException("commit failed") },
        )

        val result = submitter.submit(
            AuthenticatedTextPostSubmitRequest(
                accountStableId = account,
                community = "subshell",
                title = "Title",
                text = "Body",
                requestKey = "key-1",
            ),
        )

        result as AuthenticatedTextPostSubmitResult.Failed
        assertEquals(AuthenticatedTextPostSubmitFailureKind.SESSION, result.kind)
        assertTrue(result.retryable)
        assertEquals(0, requests)
    }

    @Test
    fun stagedFilesLeftOverAreSweptAndFreshOnesKept() {
        val directory = createTempDirectory("staging").toFile()
        val now = 100_000_000L
        val stale = File(directory, "old.mp4").apply { writeText("x"); setLastModified(now - 7L * 60L * 60L * 1000L) }
        val fresh = File(directory, "new.mp4").apply { writeText("x"); setLastModified(now - 60_000L) }

        pruneStaleStagedMediaFiles(directory, now)

        assertFalse(stale.exists())
        assertTrue(fresh.exists())
        directory.deleteRecursively()
    }
}
