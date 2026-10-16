package app.subshell.prototype.shortcuts

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.MainActivity
import app.subshell.prototype.ui.RedditTarget
import app.subshell.prototype.ui.redditViewIntentLaunchOrNull
import app.subshell.prototype.ui.resolveRedditTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidLauncherShortcutPublisherTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun theSettingIsOffByDefaultAndStoredOnTheDevice() {
        assertFalse(LauncherShortcutsEnabledStore.read(application))

        LauncherShortcutsEnabledStore.write(application, true)

        assertTrue(LauncherShortcutsEnabledStore.read(application))
    }

    @Test
    fun dynamicShortcutsOpenTheCommunityThroughTheRedditLinkPath() {
        val publisher = AndroidLauncherShortcutPublisher(application)

        publisher.apply(
            LauncherShortcutPlan(
                dynamic = listOf(
                    LauncherShortcutEntry.Community("android"),
                    LauncherShortcutEntry.Community("kotlin"),
                ),
                enableCommunities = emptyList(),
                disablePinnedIds = emptyList(),
            ),
        )

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(application)
            .sortedBy { shortcut -> shortcut.rank }
        assertEquals(
            listOf(communityShortcutId("android"), communityShortcutId("kotlin")),
            shortcuts.map { shortcut -> shortcut.id },
        )
        assertEquals("r/android", shortcuts.first().shortLabel.toString())
        val intent = shortcuts.first().intent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertNotNull(redditViewIntentLaunchOrNull(intent.action, intent.dataString, requestId = 1L))
        assertEquals(RedditTarget.Community("android"), resolveRedditTarget(intent.dataString))
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }

    @Test
    fun anEmptyPlanRemovesEveryDynamicShortcut() {
        val publisher = AndroidLauncherShortcutPublisher(application)
        publisher.apply(
            LauncherShortcutPlan(listOf(LauncherShortcutEntry.Community("android")), emptyList(), emptyList()),
        )

        publisher.apply(LauncherShortcutPlan(emptyList(), emptyList(), emptyList()))

        assertEquals(0, ShortcutManagerCompat.getDynamicShortcuts(application).size)
    }

    @Test
    fun theLabelKeepsRedditsCasingAndTheIdDoesNot() {
        val shortcut = communityShortcut(application, "r/Android")

        assertEquals(communityShortcutId("android"), shortcut.id)
        assertEquals("r/Android", shortcut.shortLabel.toString())
        assertEquals(RedditTarget.Community("Android"), resolveRedditTarget(shortcut.intent.dataString))
    }

    @Test
    fun feedAndInboxShortcutsCarryTheirTargetOnceAndOnlyOnce() {
        val feed = launcherShortcut(
            application,
            LauncherShortcutEntry.CustomFeed("reddit:alice", "/user/alice/m/cats", "Cats"),
        )
        val feedIntent = Intent(feed.intent)
        assertEquals(MainActivity::class.java.name, feedIntent.component?.className)
        assertEquals("Cats", feed.shortLabel.toString())

        assertEquals(
            LauncherShortcutTarget.CustomFeed("reddit:alice", "/user/alice/m/cats", requestId = 7L),
            takeLauncherShortcutTarget(feedIntent, requestId = 7L),
        )
        assertNull(takeLauncherShortcutTarget(feedIntent, requestId = 8L))

        val inbox = launcherShortcut(application, LauncherShortcutEntry.Inbox("reddit:alice"))
        assertEquals(
            LauncherShortcutTarget.Inbox("reddit:alice", requestId = 9L),
            takeLauncherShortcutTarget(Intent(inbox.intent), requestId = 9L),
        )
        assertNull(takeLauncherShortcutTarget(Intent(Intent.ACTION_VIEW), requestId = 10L))
    }
}
