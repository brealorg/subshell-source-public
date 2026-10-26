package app.subshell.prototype.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.model.Community
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ROOMWAIT01_ONE_COMMUNITY_READ_PER_LOAD_V1 (#243): building the state reads
 * the community table once for the joined communities. Main waits for that
 * read (the Room executor), and a second, redundant normalization read it
 * again on every start and every dark-mode rebuild.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RoomWait01CommunityReadsTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private class CountingRepository(
        private val delegate: SubshellRepository = FakeSubshellRepository(),
    ) : SubshellRepository by delegate {
        var communityReads = 0

        override fun communities(request: RepositoryPageRequest): RepositoryResult<List<Community>> {
            communityReads += 1
            return delegate.communities(request)
        }
    }

    @Test
    fun buildingTheStateReadsTheCommunitiesOnceForTheJoinedList() {
        val repository = CountingRepository()
        var state: SubshellState? = null

        composeRule.setContent { state = rememberSubshellState(repository = repository) }
        composeRule.waitForIdle()

        val joined = requireNotNull(state).joinedCommunityIds.toSet()
        assertEquals(defaultJoinedCommunityIds(FakeSubshellRepository()), joined)
        assertEquals(1, repository.communityReads)
    }
}
