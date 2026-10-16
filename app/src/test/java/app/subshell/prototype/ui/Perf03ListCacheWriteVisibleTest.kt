package app.subshell.prototype.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.PersistentSubshellRepository
import app.subshell.prototype.data.cache.MutableCacheClock
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.createFixtureFreeLocalSubshellRepository
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.UserFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PERF03 (#69), device find: in production the community and user lists are
 * only what the Room cache holds, and identity lookups write it without a
 * repository revision. A list kept per revision hid those writes ("My
 * communities" stayed empty); the old code read them on the next access, and
 * so does the kept list now.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Perf03ListCacheWriteVisibleTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(application, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = PersistentSubshellRepository(
        delegate = createFixtureFreeLocalSubshellRepository(),
        cacheStore = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L)),
    )

    @After
    fun close() {
        repository.close()
    }

    @Test
    fun aCommunityIdentityWrittenAfterTheFirstReadIsListed() {
        val state = SubshellState(repository = repository)
        assertEquals(emptyList<Community>(), state.communities)

        repository.cacheCommunityIdentity(
            Community(id = "t5_abc", name = "kotlin", description = "", members = "1", accent = 0L),
        )

        assertEquals(listOf("kotlin"), state.communities.map { it.name })
    }

    @Test
    fun aUserIdentityWrittenAfterTheFirstReadIsListed() {
        val state = SubshellState(repository = repository)
        val before = state.users.size

        repository.cacheUserIdentity(sampleUser())

        assertEquals(before + 1, state.users.size)
    }

    private fun sampleUser(): UserFixture = UserFixture(username = "someone", description = "", karma = "1")
}
