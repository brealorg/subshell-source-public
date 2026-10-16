package app.subshell.prototype.data.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.CommunityDiscoveryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionCacheInstrumentedTest {
    @Test
    fun subscriptionSnapshotsAreAccountScopedAndPersistEmptyLists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DATABASE_NAME)
        val clock = MutableCacheClock(1_000L)
        var store = RoomSubshellCacheStore(
            database = SubshellCacheDatabase.open(context, DATABASE_NAME),
            clock = clock,
        )
        val firstAccount = "reddit:redreader_compatibility:t2_first"
        val secondAccount = "reddit:redreader_compatibility:t2_second"
        val firstCommunities = SampleData.communities.take(2)
        val secondCommunities = SampleData.communities.drop(2).take(1)

        store.replaceSubscriptionSnapshot(firstAccount, firstCommunities)
        clock.epochMs = 2_000L
        store.replaceSubscriptionSnapshot(secondAccount, secondCommunities)

        assertEquals(
            firstCommunities.map { it.name },
            store.subscriptionSnapshot(firstAccount)?.communities?.map { it.name },
        )
        assertEquals(
            secondCommunities.map { it.name },
            store.subscriptionSnapshot(secondAccount)?.communities?.map { it.name },
        )

        clock.epochMs = 3_000L
        store.replaceSubscriptionSnapshot(firstAccount, emptyList())
        store.close()

        store = RoomSubshellCacheStore(
            database = SubshellCacheDatabase.open(context, DATABASE_NAME),
        )
        val emptySnapshot = store.subscriptionSnapshot(firstAccount)
        assertNotNull(emptySnapshot)
        assertTrue(emptySnapshot?.communities?.isEmpty() == true)
        assertEquals(
            secondCommunities.map { it.id },
            store.subscriptionSnapshot(secondAccount)?.communities?.map { it.id },
        )

        store.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun communityDiscoverySnapshotsPreserveOrderQuerySortIsolationAndNsfwState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DISCOVERY_DATABASE_NAME)
        val clock = MutableCacheClock(4_000L)
        var store = RoomSubshellCacheStore(
            database = SubshellCacheDatabase.open(context, DISCOVERY_DATABASE_NAME),
            clock = clock,
        )
        val popularKey = "account|reddit:test|community-discovery|popular"
        val relevanceKey =
            "account|reddit:test|community-discovery|search:relevance:android"
        val activityKey =
            "account|reddit:test|community-discovery|search:activity:android"
        val popularItems = listOf(
            CommunityDiscoveryItem(SampleData.communities[2], nsfw = false),
            CommunityDiscoveryItem(SampleData.communities[0], nsfw = true),
        )
        val relevanceItems = listOf(
            CommunityDiscoveryItem(SampleData.communities[1], nsfw = false),
        )
        val activityItems = listOf(
            CommunityDiscoveryItem(SampleData.communities[3], nsfw = false),
        )

        store.replaceCommunityDiscoverySnapshot(popularKey, popularItems)
        clock.epochMs = 5_000L
        store.replaceCommunityDiscoverySnapshot(relevanceKey, relevanceItems)
        clock.epochMs = 6_000L
        store.replaceCommunityDiscoverySnapshot(activityKey, activityItems)
        store.close()

        store = RoomSubshellCacheStore(
            database = SubshellCacheDatabase.open(context, DISCOVERY_DATABASE_NAME),
        )
        val restoredPopular = requireNotNull(
            store.communityDiscoverySnapshot(popularKey),
        )
        assertEquals(
            popularItems.map { item -> item.community.id },
            restoredPopular.items.map { item -> item.community.id },
        )
        assertEquals(listOf(false, true), restoredPopular.items.map { item -> item.nsfw })
        assertEquals(
            relevanceItems.map { item -> item.community.id },
            store.communityDiscoverySnapshot(relevanceKey)?.items?.map { item -> item.community.id },
        )
        assertEquals(
            activityItems.map { item -> item.community.id },
            store.communityDiscoverySnapshot(activityKey)?.items?.map { item -> item.community.id },
        )

        store.close()
        context.deleteDatabase(DISCOVERY_DATABASE_NAME)
    }

    companion object {
        private const val DATABASE_NAME = "subshell-subs01a-cache.db"
        private const val DISCOVERY_DATABASE_NAME = "subshell-subs01b-discovery-cache.db"
    }
}
