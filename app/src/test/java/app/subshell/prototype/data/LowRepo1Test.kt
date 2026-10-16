package app.subshell.prototype.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.cache.MutableCacheClock
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REPO1 (low repository findings): the fixture-free backend gets no replay at
 * construction (OCB-457), and an empty default feed is not written back when
 * nothing was cached (OCB-459).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowRepo1Test {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(application, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cacheStore = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))

    @After
    fun close() {
        database.close()
    }

    @Test
    fun theFixtureFreeBackendGetsNoReplayAtConstruction() {
        cacheStore.persistLocalMutation(
            mutationKey = "vote:t3_x",
            kind = "vote",
            subjectId = "t3_x",
            payload = "{}",
        )

        val repository = PersistentSubshellRepository(createFixtureFreeLocalSubshellRepository(), cacheStore)

        assertTrue(repository.localMutationReplaySummary().outcomes.isEmpty())
    }

    @Test
    fun anEmptyDefaultFeedIsNotWrittenBackOverNothing() {
        val repository = PersistentSubshellRepository(createFixtureFreeLocalSubshellRepository(), cacheStore)

        repository.posts(RepositoryPageRequest())

        assertNull(repository.cacheQueryMetadata(RepositoryQueryKey.Default))
    }
}
