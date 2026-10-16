package app.subshell.prototype.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtoClean01ProductionFixtureIsolationPolicyTest {
    @Test
    fun productionLocalBackendContainsNoSampleContent() {
        val repository = createFixtureFreeLocalSubshellRepository()

        assertEquals(listOf(FIXTURE_FREE_LOCAL_USERNAME), repository.accounts().itemsOrEmpty())
        assertTrue(repository.posts().itemsOrEmpty().isEmpty())
        assertTrue(repository.comments().itemsOrEmpty().isEmpty())
        assertTrue(repository.communities().itemsOrEmpty().isEmpty())
        assertTrue(repository.users().itemsOrEmpty().isEmpty())
        assertTrue(repository.inbox().itemsOrEmpty().isEmpty())
        assertTrue(repository.defaultSavedPostIds.isEmpty())
        assertTrue(repository.defaultUpvotedPostIds.isEmpty())
    }

    @Test
    fun data01FakeBackendRemainsAnExplicitFixtureBackend() {
        val production = createFixtureFreeLocalSubshellRepository()
        val fixture = createFakeSubshellRepository()

        assertNotEquals(production.localUsername, fixture.localUsername)
        assertTrue(fixture.posts().itemsOrEmpty().isNotEmpty())
        assertTrue(fixture.communities().itemsOrEmpty().isNotEmpty())
    }
}
