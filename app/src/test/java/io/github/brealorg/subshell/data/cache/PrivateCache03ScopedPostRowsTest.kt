package io.github.brealorg.subshell.data.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RepositoryLoadOperation
import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.RepositoryQueryKey
import io.github.brealorg.subshell.data.network.NetworkPage
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRIVATECACHE03_ACCOUNT_SCOPED_POST_ROWS_ARE_SEALED_V1: a post row of an account-scoped feed carries
 * the account's vote and saved state, so it is stored sealed; a row of a public feed is not, and an
 * older build's plain rows are read and sealed once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivateCache03ScopedPostRowsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val clock = MutableCacheClock(1_000L)
    private val stores = mutableListOf<RoomSubshellCacheStore>()
    private val home = RepositoryQueryKey("account|authenticated|reddit:me|live02|home|best")
    private val publicFeed = RepositoryQueryKey("community|r/test|hot")

    private fun store(seal: CachePayloadSeal): RoomSubshellCacheStore {
        database.payloadSeal = seal
        return RoomSubshellCacheStore(database, clock = clock, ownsDatabase = false).also(stores::add)
    }

    @After
    fun close() {
        stores.forEach(RoomSubshellCacheStore::close)
        database.close()
    }

    private fun post(id: String, vote: Int = 0, saved: Boolean = false) = Post(
        id = id,
        community = "r/test",
        author = "someone",
        age = "1h",
        title = "A title that says what I read $id",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
        vote = vote,
        saved = saved,
    )

    private fun write(cache: RoomSubshellCacheStore, key: RepositoryQueryKey, posts: List<Post>) =
        cache.writeNetworkPostPage(
            RepositoryPageRequest(cursor = null, limit = 25, queryKey = key, operation = RepositoryLoadOperation.INITIAL),
            NetworkPage(posts, "c1"),
            receivedAtEpochMs = clock.epochMs,
        )

    private fun rows(): Map<String, String> =
        database.openHelper.readableDatabase.query("SELECT post_id, payload FROM cached_posts").use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }

    private fun realSeal(keyByte: Int) = CipherCachePayloadSeal(SoftwareAesCipher(keyByte))

    @Test
    fun theRowsOfAnAccountFeedAreSealedAndTheRowsOfAPublicFeedAreNot() {
        val cache = store(realSeal(1))
        write(cache, home, listOf(post("t3_mine", vote = 1, saved = true)))
        write(cache, publicFeed, listOf(post("t3_public")))

        val byKey = rows()
        val scoped = byKey.entries.single { it.key.contains("|post|") }
        val public = byKey.entries.single { it.key == "t3_public" }
        assertTrue(scoped.value.startsWith(CachePayloadSeal.SEALED_PREFIX))
        assertFalse(scoped.value.contains("what I read"))
        assertFalse(public.value.startsWith(CachePayloadSeal.SEALED_PREFIX))

        val mine = cache.readPostFeedSnapshot(home)!!.posts.single()
        assertEquals(1, mine.vote)
        assertTrue(mine.saved)
        assertEquals("t3_public", cache.readPostFeedSnapshot(publicFeed)!!.posts.single().id)
    }

    @Test
    fun anotherKeyMakesAnAccountFeedACacheMissButAPublicFeedStillReads() {
        val writer = store(realSeal(1))
        write(writer, home, listOf(post("t3_mine")))
        write(writer, publicFeed, listOf(post("t3_public")))

        val reader = store(realSeal(2))

        assertNull(reader.readPostFeedSnapshot(home))
        assertEquals("t3_public", reader.readPostFeedSnapshot(publicFeed)!!.posts.single().id)
    }

    @Test
    fun aVoteChangedLaterIsStoredSealedToo() {
        val cache = store(realSeal(1))
        write(cache, home, listOf(post("t3_mine")))

        cache.updateStoredPostLater("reddit:me", "t3_mine") { it.copy(vote = -1, saved = true) }
        cache.readPostFeedSnapshot(home) // the update runs on the store's thread, ahead of this read

        rows().filterKeys { it.contains("|post|") }.values
            .forEach { assertTrue(it.startsWith(CachePayloadSeal.SEALED_PREFIX)) }
        val mine = cache.readPostFeedSnapshot(home)!!.posts.single()
        assertEquals(-1, mine.vote)
        assertTrue(mine.saved)
    }

    @Test
    fun rowsAnOlderBuildWroteInPlainTextAreReadAndSealedOnce() {
        val cache = store(CachePayloadSeal.Plain)
        write(cache, home, listOf(post("t3_mine", vote = 1)))
        database.payloadSeal = realSeal(1)
        assertTrue(rows().values.none { it.startsWith(CachePayloadSeal.SEALED_PREFIX) })

        assertEquals(1, cache.readPostFeedSnapshot(home)!!.posts.single().vote)
        assertTrue(cache.sealLegacyPayloads() >= 1)

        rows().filterKeys { it.contains("|post|") }.values
            .forEach { assertTrue(it.startsWith(CachePayloadSeal.SEALED_PREFIX)) }
        assertEquals(1, cache.readPostFeedSnapshot(home)!!.posts.single().vote)
    }
}
