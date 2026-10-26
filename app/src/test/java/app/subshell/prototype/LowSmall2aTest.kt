package app.subshell.prototype

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.applock.AppLockDelayDialog
import app.subshell.prototype.share.ShareIntoSubshellComponent
import app.subshell.prototype.share.ShareIntoSubshellTest
import app.subshell.prototype.share.isShareIntent
import app.subshell.prototype.share.readSharedPost
import app.subshell.prototype.shortcuts.LAUNCHER_SHORTCUT_ACCOUNT_EXTRA
import app.subshell.prototype.shortcuts.LAUNCHER_SHORTCUT_ACTION
import app.subshell.prototype.shortcuts.LAUNCHER_SHORTCUT_KIND_EXTRA
import app.subshell.prototype.shortcuts.takeLauncherShortcutTarget
import app.subshell.prototype.update.UpdateManifestRelease
import app.subshell.prototype.update.UpdatePresentationCoordinator
import app.subshell.prototype.update.UpdatePresentationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SMALL2a (low findings: app lock, share, shortcuts, update): the Lock after
 * options are radio buttons (OCB-395), only a share through the share target
 * counts (OCB-570) and copies stop at the gallery limit (OCB-571), a shortcut
 * relaunched from Recents is not replayed (OCB-572), and a withdrawn release
 * drops an automatic offer (OCB-656).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowSmall2aTest {
    @org.junit.Before
    @org.junit.After
    fun forgetFileProviderRoots() = app.subshell.prototype.harness.forgetFileProviderRoots()

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun theLockAfterOptionsAreRadioButtons() {
        compose.setContent {
            AppLockDelayDialog(selectedMinutes = 0, onSelect = {}, onDismiss = {})
        }
        compose.onNodeWithText("Immediately").assertIsSelected()
        val radios = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .fetchSemanticsNodes()
        assertTrue("radio options: ${radios.size}", radios.size >= 2)
    }

    @Test
    fun onlyAShareThroughTheShareTargetCounts() {
        val direct = Intent(Intent.ACTION_SEND).setComponent(ComponentName(context, MainActivity::class.java))
        assertFalse(isShareIntent(direct))
        val throughAlias = Intent(Intent.ACTION_SEND)
            .setComponent(ComponentName(context.packageName, ShareIntoSubshellComponent.ALIAS_CLASS))
        assertTrue(isShareIntent(throughAlias))
    }

    @Test
    fun copiesStopAtTheGalleryLimit() {
        Robolectric.buildContentProvider(ShareIntoSubshellTest.OtherAppProvider::class.java).create("other.app.files")
        val streams = ArrayList((1..25).map { index -> Uri.parse("content://other.app.files/$index.png") })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams)
        val before = app.subshell.prototype.share.sharedMediaDirectory(context).listFiles().orEmpty().size

        val post = readSharedPost(context, intent)!!

        assertEquals(20, post.media.size)
        assertEquals(5, post.skippedCount)
        assertEquals(20, app.subshell.prototype.share.sharedMediaDirectory(context).listFiles().orEmpty().size - before)
    }

    @Test
    fun aShortcutRelaunchedFromRecentsIsNotReplayed() {
        fun shortcut() = Intent(LAUNCHER_SHORTCUT_ACTION)
            .putExtra(LAUNCHER_SHORTCUT_KIND_EXTRA, "inbox")
            .putExtra(LAUNCHER_SHORTCUT_ACCOUNT_EXTRA, "reddit:redreader_compatibility:t2_me")
        assertNotNull(takeLauncherShortcutTarget(shortcut(), requestId = 1L))
        val fromRecents = shortcut().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(takeLauncherShortcutTarget(fromRecents, requestId = 2L))
        assertFalse(fromRecents.hasExtra(LAUNCHER_SHORTCUT_KIND_EXTRA))
    }

    @Test
    fun aWithdrawnReleaseDropsAnAutomaticOfferButNotOneAskedFor() {
        val release = UpdateManifestRelease(
            schemaVersion = 1,
            channel = "beta",
            versionCode = 9_999,
            versionName = "9.9.9-beta.1",
            publishedAt = "2026-10-07T10:00:00Z",
            releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v9.9.9-beta.1",
            apkSha256 = "a".repeat(64),
            headline = "A Subshell beta update",
        )
        try {
            UpdatePresentationCoordinator.offerAvailable(release)
            UpdatePresentationCoordinator.withdrawAvailable()
            assertNull(UpdatePresentationCoordinator.request.value)

            UpdatePresentationCoordinator.openAvailable(release)
            UpdatePresentationCoordinator.withdrawAvailable()
            assertTrue(UpdatePresentationCoordinator.request.value is UpdatePresentationRequest.UpdateAvailable)
        } finally {
            UpdatePresentationCoordinator.clear()
        }
    }
}
