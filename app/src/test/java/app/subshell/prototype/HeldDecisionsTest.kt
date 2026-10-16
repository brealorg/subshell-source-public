package app.subshell.prototype

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.share.ShareIntoSubshellComponent
import app.subshell.prototype.share.isShareIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Owner decisions that replaced "kept as designed":
 * LOW_EXTERNAL_LAUNCHES_USE_SUBSHELLS_OWN_TASK_V1 (OCB-577),
 * LOW_PROFILE_HISTORY_GATE_FOLLOWS_THE_FEED_GATE_V1 (OCB-327).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HeldDecisionsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun shareThroughTarget() = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "https://example.com/story")
        .setComponent(ComponentName(context.packageName, ShareIntoSubshellComponent.ALIAS_CLASS))

    @Test
    fun aShareIsForwardedIntoSubshellsOwnTaskAndStillCountsAsAShare() {
        val controller = Robolectric.buildActivity(ExternalLaunchActivity::class.java, shareThroughTarget()).setup()
        val forwarded = shadowOf(controller.get()).nextStartedActivity

        assertEquals(MainActivity::class.java.name, forwarded.component?.className)
        assertTrue(forwarded.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(forwarded.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertEquals("https://example.com/story", forwarded.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(isShareIntent(forwarded))
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun aRedditLinkIsForwardedWithoutAShareToken() {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/r/subshell/"))
            .setComponent(ComponentName(context, ExternalLaunchActivity::class.java))
        val forwarded = externalLaunchForward(context, link)

        assertEquals(MainActivity::class.java.name, forwarded.component?.className)
        assertEquals("https://www.reddit.com/r/subshell/", forwarded.dataString)
        assertNull(forwarded.getStringExtra(EXTRA_EXTERNAL_LAUNCH_TOKEN))
    }

    @Test
    fun aShareSentStraightToMainActivityStillDoesNotCount() {
        val forged = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .setComponent(ComponentName(context, MainActivity::class.java))
            .putExtra(EXTRA_EXTERNAL_LAUNCH_TOKEN, "guessed")
        assertFalse(isShareIntent(forged))

        // A token another app put on a VIEW is dropped, not passed on.
        val view = Intent(Intent.ACTION_SEND)
            .setComponent(ComponentName(context, ExternalLaunchActivity::class.java))
            .putExtra(EXTRA_EXTERNAL_LAUNCH_TOKEN, ExternalLaunchToken.value)
        assertFalse(isShareIntent(externalLaunchForward(context, view)))
    }

    @Test
    fun anAccountSwitchLetsAHistoryRefreshAgain() {
        val state = app.subshell.prototype.ui.SubshellState()
        val key = RepositoryQueryKey("history|saved|me")
        assertTrue(state.claimProfileHistoryAutoRefresh(key))
        assertFalse(state.claimProfileHistoryAutoRefresh(key))

        state.invalidatePostPaging()

        assertTrue(state.claimProfileHistoryAutoRefresh(key))
    }
}
