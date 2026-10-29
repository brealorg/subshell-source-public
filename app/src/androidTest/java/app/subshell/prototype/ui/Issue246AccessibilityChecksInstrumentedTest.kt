package app.subshell.prototype.ui

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.theme.AppUiScale
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ISSUE246_ACCESSIBILITY_CHECKS_IN_COMPOSE_TESTS_V1 (#246): Google's
 * Accessibility Test Framework over the app's main surfaces (labels, touch
 * targets, contrast, traversal). It does not evaluate Compose content under
 * Robolectric, so it runs here, on an emulator (`adb -s` keeps it off the
 * phone, which a connected* task would also use):
 *
 *   ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest   (offline, as always)
 *   adb -s emulator-5580 install -r -t app/build/outputs/apk/debug/app-debug.apk
 *   adb -s emulator-5580 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
 *   adb -s emulator-5580 shell am instrument -w -r \
 *     -e class app.subshell.prototype.ui.Issue246AccessibilityChecksInstrumentedTest \
 *     <package>.test/androidx.test.runner.AndroidJUnitRunner
 *
 * (subshell-api36 booted as tools/emulator-smoke.sh does: -memory 4096.)
 * Errors fail the surface; warnings are logged under SubshellA11y.
 *
 * Every surface is opened from a freshly composed SubshellApp with the
 * built-in sample data, then checked from the root view; all errors of one
 * surface are listed together.
 *
 * ISSUE278: `-e subshellUiScale SMALLEST|SMALLER|DEFAULT|LARGER` runs the same
 * checks at that UI size (default: DEFAULT, Android's own size).
 */
@RunWith(AndroidJUnit4::class)
class Issue246AccessibilityChecksInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var video: VideoPlaybackCoordinator? = null

    @After
    fun releaseVideo() {
        composeRule.runOnIdle { video?.release() }
    }

    private fun checkSurface(open: (SubshellState) -> Unit) {
        val state = SubshellState(initialInteractionSideConfigured = true)
        state.updateAppUiScale(uiScale)
        val coordinator = composeRule.runOnUiThread { VideoPlaybackCoordinator(composeRule.activity) }
        video = coordinator
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = coordinator, state = state)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { open(state) }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()

        val errors = composeRule.runOnIdle { accessibilityErrors() }
        // Warnings do not fail the test; they are logged for review.
        composeRule.runOnIdle { accessibilityResults(AccessibilityCheckResultType.WARNING) }
            .forEach { result ->
                Log.i(A11Y_LOG_TAG, "WARNING ${result.sourceCheckClass.simpleName}: ${result.message} — ${result.view}")
            }
        assertTrue(
            errors.joinToString(separator = "\n", prefix = "${errors.size} accessibility errors:\n") { result ->
                "${result.sourceCheckClass.simpleName}: ${result.message} — ${result.view}"
            },
            errors.isEmpty(),
        )
    }

    // The control: the checks do read Compose content here (under Robolectric
    // they reported "Composable content is not evaluated in this environment").
    @Test
    fun anUnlabelledTinyControlIsReported() {
        composeRule.setContent { Box(Modifier.size(20.dp).clickable {}) }
        composeRule.waitForIdle()

        val errors = composeRule.runOnIdle { accessibilityErrors() }

        assertTrue("no errors for an unlabelled 20 dp control", errors.isNotEmpty())
    }

    private fun accessibilityErrors() = accessibilityResults(AccessibilityCheckResultType.ERROR)

    private fun accessibilityResults(type: AccessibilityCheckResultType) =
        AccessibilityValidator()
            .setRunChecksFromRootView(true)
            .setThrowExceptionFor(null)
            .checkAndReturnResults(composeRule.activity.window.decorView)
            .filter { result -> result.type == type }

    private val uiScale: AppUiScale =
        InstrumentationRegistry.getArguments().getString(UI_SCALE_ARGUMENT)?.let(AppUiScale::valueOf) ?: AppUiScale.DEFAULT

    private fun firstPost(kind: PostKind) = SampleData.posts.first { post -> post.kind == kind }

    @Test
    fun home() = checkSurface {}

    @Test
    fun drawer() = checkSurface { state -> state.requestDrawer() }

    @Test
    fun textPost() = checkSurface { state -> state.openFromMainFeed(firstPost(PostKind.TEXT)) }

    @Test
    fun imagePost() = checkSurface { state -> state.openFromMainFeed(firstPost(PostKind.IMAGE)) }

    @Test
    fun commentComposer() = checkSurface { state ->
        val post = firstPost(PostKind.TEXT)
        state.openFromMainFeed(post)
        state.openCommentComposer(post)
    }

    @Test
    fun community() = checkSurface { state -> state.openSubreddit(SampleData.communities.first().name) }

    @Test
    fun communities() = checkSurface { state -> state.navigate(MainDestination.SUBS) }

    @Test
    fun search() = checkSurface { state -> state.openSearch(refocus = false) }

    @Test
    fun inbox() = checkSurface { state -> state.navigate(MainDestination.INBOX) }

    @Test
    fun conversation() = checkSurface { state ->
        state.navigate(MainDestination.INBOX)
        state.openInboxItem(SampleData.inbox.first { item -> item.kind == ActivityKind.MESSAGE })
    }

    @Test
    fun you() = checkSurface { state -> state.navigate(MainDestination.YOU) }

    @Test
    fun settings() = checkSurface { state -> state.openSecondary(SecondaryDestination.SETTINGS) }

    @Test
    fun postComposer() = checkSurface { state -> state.openPostComposer() }

    private companion object {
        const val SETTLE_MILLIS = 1_500L
        const val A11Y_LOG_TAG = "SubshellA11y"
        const val UI_SCALE_ARGUMENT = "subshellUiScale"
    }
}
