package app.subshell.prototype.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.cache.MutableCacheClock
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.reddit.IdentityMetadataDataSource
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.UserFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (error01a5): when Reddit cannot be reached and the cache answers
 * instead, the answer still says why — the same failure details reach the
 * screen for a feed page, one post (also for an account) and comments; and a
 * user's identity says whether the network or the device's storage failed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3RepositoryFailureCauseTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(application, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cacheStore = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))
    private val cause = FailureDetails(retryable = true, transportKind = NetworkTransportFailureKind.DNS)
    private val posts = SampleData.posts.take(2)
    private val comments = SampleData.comments.take(2)

    @After
    fun close() {
        database.close()
    }

    /** Answers from the sample data until [failing], then fails with [cause]. */
    private inner class Backend(private val base: SubshellRepository) : SubshellRepository by base {
        var failing = false
        var offline = false

        private fun <T> failure(): RepositoryResult<T> =
            if (offline) RepositoryResult.Offline(message = "offline", failureDetails = cause)
            else RepositoryResult.Error(message = "unreachable", failureDetails = cause)

        override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> =
            if (failing) failure() else RepositoryResult.Content(posts)

        override fun post(postId: String): RepositoryResult<Post> =
            if (failing) failure() else RepositoryResult.Content(posts.first { it.id == postId })

        override fun comments(request: RepositoryPageRequest): RepositoryResult<List<Comment>> =
            if (failing) failure() else RepositoryResult.Content(comments)
    }

    private fun warmed(offline: Boolean): Pair<PersistentSubshellRepository, Backend> {
        val backend = Backend(createFixtureFreeLocalSubshellRepository())
        val repository = PersistentSubshellRepository(backend, cacheStore)
        repository.posts(RepositoryPageRequest(operation = RepositoryLoadOperation.REFRESH))
        repository.comments(RepositoryPageRequest())
        backend.failing = true
        backend.offline = offline
        return repository to backend
    }

    private fun RepositoryResult<*>.details(): FailureDetails? = when (this) {
        is RepositoryResult.Error -> failureDetails
        is RepositoryResult.Offline -> failureDetails
        else -> null
    }

    private fun RepositoryResult<*>.cached(): Any? = when (this) {
        is RepositoryResult.Error -> cached
        is RepositoryResult.Offline -> cached
        else -> null
    }

    @Test
    fun aCachedAnswerKeepsTheReasonRedditWasUnreachable() {
        for (offline in listOf(false, true)) {
            val (repository, _) = warmed(offline)
            val postId = posts.first().id
            val answers = mapOf(
                "posts" to repository.posts(RepositoryPageRequest(operation = RepositoryLoadOperation.REFRESH)),
                "post" to repository.post(postId),
                "postForAccount" to repository.postForAccount(postId, null),
                "comments" to repository.comments(RepositoryPageRequest()),
            )

            answers.forEach { (name, answer) ->
                assertTrue("$name ($offline) came from the cache: $answer", answer.cached() != null)
                assertEquals("$name ($offline)", cause, answer.details())
            }
        }
    }

    private object EmptyIdentityCache : IdentityMetadataCacheRepository {
        override fun cachedCommunityIdentity(name: String): Community? = null
        override fun cacheCommunityIdentity(community: Community) = Unit
        override fun cachedUserIdentity(username: String): UserFixture? = null
        override fun cacheUserIdentity(user: UserFixture) = Unit
    }

    private class IdentitySource(private val user: NetworkResult<UserFixture>) : IdentityMetadataDataSource {
        override suspend fun user(username: String, fallback: UserFixture) = user
        override suspend fun community(communityName: String, fallback: Community): NetworkResult<Community> = error("unused")
        override suspend fun communities(fallbacks: List<Community>): NetworkResult<List<Community>> = error("unused")
        override suspend fun authorAvatars(fullnames: Collection<String>): NetworkResult<Map<String, String?>> = error("unused")
    }

    @Test
    fun aUsersIdentitySaysWhetherTheNetworkOrTheStorageFailed() = runBlocking {
        val user = SampleData.users.first()
        val unreachable = RoomBackedIdentityMetadataRepository(
            dataSource = IdentitySource(NetworkResult.TransportFailure(NetworkTransportFailureKind.DNS, retryable = true)),
            cache = EmptyIdentityCache,
        ).refreshUserIdentity(user.username, user)
        val unsaved = RoomBackedIdentityMetadataRepository(
            dataSource = IdentitySource(NetworkResult.Success(user, NetworkResponseMetadata(200, 0L, NetworkRateLimit(null, null, null), null))),
            cache = EmptyIdentityCache,
        ).refreshUserIdentity(user.username, user)

        assertEquals(NetworkTransportFailureKind.DNS, unreachable.details()?.transportKind)
        assertEquals(LocalFailureKind.STORAGE, unsaved.details()?.localKind)
    }
}
