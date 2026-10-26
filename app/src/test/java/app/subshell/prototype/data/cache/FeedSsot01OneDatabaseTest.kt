package app.subshell.prototype.data.cache

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.createPersistentSubshellRepository
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FEEDSSOT01_ONE_CACHE_DATABASE_PER_PROCESS_V1 (#244): the repository's store
 * and the account runtime's store share one Room instance, so an observer on
 * one sees what the other writes (Room notifies only within an instance).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeedSsot01OneDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val holder = Class.forName("app.subshell.prototype.data.ProductionPersistentRepositoryHolder")
        .getDeclaredField("repository")
        .apply { isAccessible = true }
    private var previous: Any? = null

    @Before
    fun clearHolder() {
        previous = holder.get(null)
        holder.set(null, null)
    }

    @After
    fun restoreHolder() {
        holder.set(null, previous)
    }

    private fun Any.field(name: String): Any? =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)

    @Test
    fun theRepositoryAndTheAccountRuntimeShareOneDatabase() {
        val repositoryStore = requireNotNull(createPersistentSubshellRepository(context).field("cacheStore"))
        val runtimeStore = processRuntimeCacheStore(context)

        assertSame(SubshellCacheDatabase.processShared(context), repositoryStore.field("database"))
        assertSame(SubshellCacheDatabase.processShared(context), runtimeStore.field("database"))
    }

    @Test
    fun anObserverSeesAWriteThroughTheOtherStore() = runBlocking {
        val runtimeStore = processRuntimeCacheStore(context)
        val otherStore = RoomSubshellCacheStore(database = SubshellCacheDatabase.processShared(context), ownsDatabase = false)
        val observing = java.util.concurrent.CountDownLatch(1)
        val seen = async(Dispatchers.IO) {
            withTimeout(5_000) {
                runtimeStore.observeComments("thread").first { thread ->
                    // The first (empty) read has happened before the write below.
                    observing.countDown()
                    thread.isNotEmpty()
                }
            }
        }
        check(observing.await(5, java.util.concurrent.TimeUnit.SECONDS))

        otherStore.cacheComments("thread", listOf(Comment(id = "t1_a", author = "a", age = "1h", body = "hi", score = 1, depth = 0, accent = 0L)))

        assertEquals(listOf("t1_a"), seen.await().map(Comment::id))
        // Not the owner: closing stops its thread and leaves the shared database open.
        otherStore.close()
        assertEquals(true, SubshellCacheDatabase.processShared(context).isOpen)
    }
}
