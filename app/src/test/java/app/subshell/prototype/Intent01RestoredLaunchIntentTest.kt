package app.subshell.prototype

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.AuthRuntimePreparation
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.shortcuts.LAUNCHER_SHORTCUT_ACTION
import app.subshell.prototype.shortcuts.LAUNCHER_SHORTCUT_KIND_EXTRA
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * INTENT01 (#122): the launch-intent consumers mark an intent as handled only
 * on the in-process copy. After process death the restored activity is given
 * the original launch intent again, so a notification, shortcut, share, link
 * or auth token fired a second time over the restored screen. A restored
 * activity (savedInstanceState != null) no longer consumes it.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Intent01RestoredLaunchIntentTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun workManager() = ensureWorkManagerInitialized(application)

    // AUTHSTART01: a cold start hands the launch intent on only once the auth
    // runtime is ready on IO, so asserting right after create() raced that job
    // (it stopped three lands under load). With the runtime already prepared,
    // onCreate goes on at once and both tests see the start's real outcome.
    @Before
    fun prepareTheAuthRuntime() {
        AuthRuntimePreparation.clearForTests()
        runBlocking { runCatching { AuthRuntimePreparation.prepare(application).await() } }
    }

    @After
    fun forgetTheAuthRuntime() = AuthRuntimePreparation.clearForTests()

    private fun shortcutIntent() = Intent(application, MainActivity::class.java)
        .setAction(LAUNCHER_SHORTCUT_ACTION)
        .putExtra(LAUNCHER_SHORTCUT_KIND_EXTRA, "inbox")

    @Test
    fun aColdStartConsumesTheLaunchIntent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, shortcutIntent()).create()
        assertNull(controller.get().intent.getStringExtra(LAUNCHER_SHORTCUT_KIND_EXTRA))
        controller.destroy()
    }

    @Test
    fun aRestoredActivityDoesNotConsumeTheOriginalLaunchIntentAgain() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, shortcutIntent()).create(Bundle())
        assertEquals("inbox", controller.get().intent.getStringExtra(LAUNCHER_SHORTCUT_KIND_EXTRA))
        controller.destroy()
    }
}
