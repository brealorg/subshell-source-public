package app.subshell.prototype.harness

import app.subshell.prototype.auth.reading
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.AuthConnectViewModel
import app.subshell.prototype.auth.unavailableAuthApplicationRuntime
import app.subshell.prototype.data.RedditCommentSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HARNESS02 smoke: AuthConnectViewModel can be built on the JVM with an
 * injected runtime, so its guarantees can become behaviour tests. With
 * every capability absent, comment loading is a no-op and the state stays
 * initial.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelSeamSmokeTest {
    @Test
    fun aViewModelBuiltWithTheUnavailableRuntimeStaysInert() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = AuthConnectViewModel(application, unavailableAuthApplicationRuntime())

        val initial = viewModel.reading().redditCommentsUiState.value
        assertNull(initial.postId)
        assertFalse(initial.loading)

        viewModel.reading().loadMoreRedditComments(
            accountStableId = "reddit:test",
            postId = "abc123",
            sort = RedditCommentSort.BEST,
            moreNodeId = "more-1",
        )
        assertEquals(initial, viewModel.reading().redditCommentsUiState.value)
    }
}
