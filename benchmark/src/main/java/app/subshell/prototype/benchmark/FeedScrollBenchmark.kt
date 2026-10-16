package app.subshell.prototype.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollFreshInstallCode() = measureScroll(
        compilationMode = CompilationMode.None(),
        iterations = 5,
    )

    @Test
    fun scrollWarmedCode() = measureScroll(
        compilationMode = CompilationMode.Partial(warmupIterations = 3),
        iterations = 10,
    )

    private fun measureScroll(
        compilationMode: CompilationMode,
        iterations: Int,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = iterations,
        startupMode = null,
        setupBlock = {
            killProcess()
            startActivityAndWait()
            dismissFirstRunSurfaces()
            feedList().setGestureMargin(device.displayWidth / 5)
            device.waitForIdle()
            Thread.sleep(SETTLE_DELAY_MS)
        },
    ) {
        val feed = feedList()
        feed.setGestureMargin(device.displayWidth / 5)
        repeat(SCROLLS_PER_ITERATION) {
            feed.scroll(Direction.DOWN, SCROLL_DISTANCE)
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.dismissFirstRunSurfaces() {
        device.waitForIdle()
        device.findObject(By.text("Use Right"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Later"))?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.feedList(): UiObject2 {
        val feedContainer = checkNotNull(
            device.wait(
                Until.findObject(By.res(HOME_FEED_TEST_TAG)),
                UI_TIMEOUT_MS,
            ),
        ) {
            "Home feed did not expose the Compose benchmark tag"
        }


        return checkNotNull(
            feedContainer.wait(
                Until.findObject(By.scrollable(true)),
                UI_TIMEOUT_MS,
            ),
        ) {
            "Home feed did not expose a scrollable list"
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "io.github.brealorg.subshell.benchmark"
        const val HOME_FEED_TEST_TAG = "subshell_home_feed"
        const val UI_TIMEOUT_MS = 10_000L
        const val SCROLLS_PER_ITERATION = 8
        const val SCROLL_DISTANCE = 0.8f
        const val SETTLE_DELAY_MS = 750L
    }
}
