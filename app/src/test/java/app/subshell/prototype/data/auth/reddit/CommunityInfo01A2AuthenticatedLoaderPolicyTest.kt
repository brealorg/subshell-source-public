package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoSection
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityInfoTarget
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.reddit.RedditCommunityInfoSource
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.model.CommunityRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityInfo01A2AuthenticatedLoaderPolicyTest {
    @Test
    fun targetNormalizesCanonicalCommunityIdentity() {
        val target = AuthenticatedRedditCommunityInfoTarget.community(" /r/Norge ")
        assertEquals("norge", target.communityName)
        assertEquals("community-info:norge", target.requestKey)
    }

    @Test
    fun partialEndpointFailureKeepsSuccessfulCommunityContext() = runBlocking {
        val source = RecordingCommunityInfoSource(
            sidebarResult = success("**Sidebar**"),
            rulesResult = success(listOf(CommunityRule("Be civil", "No attacks."))),
            moderatorResult = NetworkResult.ProtocolFailure(
                NetworkProtocolFailureKind.MALFORMED_JSON,
            ),
        )
        val result = loadRedditCommunityInfo(
            source,
            AuthenticatedRedditCommunityInfoTarget.community("norge"),
        )
        assertTrue(result is AuthenticatedRedditCommunityInfoResult.Loaded)
        val loaded = result as AuthenticatedRedditCommunityInfoResult.Loaded
        assertEquals("**Sidebar**", loaded.snapshot.sidebarMarkdown)
        assertEquals(listOf("Be civil"), loaded.snapshot.rules.map { it.shortName })
        assertEquals(
            listOf(AuthenticatedRedditCommunityInfoSection.MODERATORS),
            loaded.sectionFailures.map { it.section },
        )
    }

    @Test
    fun allEndpointFailuresCollapseToOneTopLevelFailure() = runBlocking {
        val failure = NetworkResult.HttpFailure(
            statusCode = 503,
            retryable = true,
            metadata = metadata(503),
        )
        val result = loadRedditCommunityInfo(
            RecordingCommunityInfoSource(failure, failure, failure),
            AuthenticatedRedditCommunityInfoTarget.community("android"),
        )
        assertTrue(result is AuthenticatedRedditCommunityInfoResult.Failed)
        val failed = result as AuthenticatedRedditCommunityInfoResult.Failed
        assertTrue(failed.retryable)
        assertEquals(503, failed.httpStatusCode)
    }

    @Test
    fun emptySuccessfulSectionsRemainValidLoadedContent() = runBlocking {
        val result = loadRedditCommunityInfo(
            RecordingCommunityInfoSource(
                success(null),
                success(emptyList()),
                success(emptyList()),
            ),
            AuthenticatedRedditCommunityInfoTarget.community("test"),
        )
        assertTrue(result is AuthenticatedRedditCommunityInfoResult.Loaded)
        assertEquals(
            "test",
            (result as AuthenticatedRedditCommunityInfoResult.Loaded).snapshot.communityName,
        )
    }

    private fun <T> success(value: T): NetworkResult.Success<T> =
        NetworkResult.Success(value, metadata(200))

    private fun metadata(statusCode: Int) = NetworkResponseMetadata(
        statusCode = statusCode,
        receivedAtEpochMs = 1L,
        rateLimit = NetworkRateLimit(null, null, null),
        retryAfterMs = null,
    )
}

private class RecordingCommunityInfoSource(
    private val sidebarResult: NetworkResult<String?>,
    private val rulesResult: NetworkResult<List<CommunityRule>>,
    private val moderatorResult: NetworkResult<List<CommunityModerator>>,
) : RedditCommunityInfoSource {
    override suspend fun sidebar(communityName: String) = sidebarResult
    override suspend fun rules(communityName: String) = rulesResult
    override suspend fun moderators(communityName: String) = moderatorResult
}
