package app.subshell.prototype.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.defaultSubshellProcessRestoreCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (protoclean01a31): the app's own repository and the process-restore
 * catalog are built on the fixture-free local backend, so a fresh install
 * shows no sample posts, communities or accounts, and a restored route never
 * counts a sample post as real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProtoClean01ProductionDefaultsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    // The production repository is kept for the process; start each test
    // without it so this test builds it, and put back whatever was there.
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

    @Test
    fun theAppsRepositoryOnAFreshInstallHoldsNoSampleContent() {
        val repository = createPersistentSubshellRepository(context)

        assertEquals(listOf(FIXTURE_FREE_LOCAL_USERNAME), repository.accounts().itemsOrEmpty())
        val postIds = repository.posts().itemsOrEmpty().map { it.id }.toSet()
        assertTrue("no sample post: $postIds", postIds.intersect(SampleData.posts.map { it.id }.toSet()).isEmpty())
        val communityNames = repository.communities().itemsOrEmpty().map { it.name }.toSet()
        assertTrue(
            "no sample community: $communityNames",
            communityNames.intersect(SampleData.communities.map { it.name }.toSet()).isEmpty(),
        )
    }

    @Test
    fun aRestoredRouteNeverCountsASamplePostAsReal() {
        val catalog = defaultSubshellProcessRestoreCatalog()
        val samplePost = SampleData.posts.first()

        assertFalse(catalog.containsPost(samplePost.id))
        assertFalse(catalog.containsMediaPost(SampleData.posts.first { it.kind == PostKind.VIDEO }.id))
    }
}
