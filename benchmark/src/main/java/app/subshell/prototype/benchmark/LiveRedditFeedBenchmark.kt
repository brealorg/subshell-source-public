package app.subshell.prototype.benchmark

import android.os.SystemClock
import android.util.Log
import androidx.benchmark.macro.ArtMetric
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class LiveRedditFeedBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun liveColdProcessPagingStress() = measureLiveStress(
        coldProcess = true,
        compilationMode = CompilationMode.None(),
        iterations = COLD_ITERATIONS,
    )

    @Test
    fun liveWarmCachePagingStress() = measureLiveStress(
        coldProcess = false,
        compilationMode = CompilationMode.Partial(warmupIterations = WARMUP_ITERATIONS),
        iterations = WARM_ITERATIONS,
    )

    private fun measureLiveStress(
        coldProcess: Boolean,
        compilationMode: CompilationMode,
        iterations: Int,
    ) {
        var measurementRevisionBaseline = 0L
        val evidence = mutableListOf<String>()

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
                ArtMetric(),
            ),
            compilationMode = compilationMode,
            iterations = iterations,
            startupMode = null,
            setupBlock = {
                if (coldProcess) killProcess()
                startActivityAndWait()
                dismissFirstRunSurfaces()
                openHomeFeed()

                val initial = waitForLiveProbe(UI_TIMEOUT_MS) { probe ->
                    probe.source == "reddit" &&
                        probe.feed == "home" &&
                        probe.stored > 0 &&
                        !probe.inProgress &&
                        probe.failure == "none"
                }
                feedList().apply {
                    setGestureMarginPercentage(GESTURE_MARGIN_PERCENT)
                    moveToTop()
                }
                val refreshLogBaseline = appliedPages().size

                feedList().apply {
                    setGestureMarginPercentage(GESTURE_MARGIN_PERCENT)
                    swipe(Direction.DOWN, REFRESH_SWIPE_PERCENT, REFRESH_SWIPE_SPEED)
                }
                val refreshed = waitForLiveProbe(NETWORK_TIMEOUT_MS) { probe ->
                    probe.source == "reddit" &&
                        probe.feed == "home" &&
                        probe.generation > initial.generation &&
                        probe.operation == "refresh" &&
                        probe.stored > 0 &&
                        !probe.inProgress &&
                        probe.failure == "none"
                }
                device.waitForIdle()
                Thread.sleep(SETTLE_DELAY_MS)
                val refreshPage = waitForAppliedNetworkPage(
                    operation = "refresh",
                    baseline = refreshLogBaseline,
                )
                val settledRefresh = readLiveProbe()
                check(
                    settledRefresh.source == "reddit" &&
                        settledRefresh.feed == "home" &&
                        settledRefresh.generation >= refreshed.generation &&
                        settledRefresh.revision >= refreshed.revision &&
                        settledRefresh.operation == "refresh" &&
                        !settledRefresh.inProgress &&
                        settledRefresh.failure == "none",
                ) {
                    "Home refresh did not settle after a live Reddit network page: " +
                        "page=$refreshPage probe=${settledRefresh.raw} logs=${feedLogTail()}"
                }
                measurementRevisionBaseline = settledRefresh.revision
            },
        ) {
            var probe = readLiveProbe()
            var observedAppendPageCount = measuredAppendPageCount(
                measurementRevisionBaseline = measurementRevisionBaseline,
                probe = probe,
            )

            while (observedAppendPageCount < APPEND_PAGES_PER_ITERATION) {
                val requiredPageCount = observedAppendPageCount + 1
                probe = flingUntilAppendPageCount(
                    measurementRevisionBaseline = measurementRevisionBaseline,
                    requiredPageCount = requiredPageCount,
                )
                observedAppendPageCount = measuredAppendPageCount(
                    measurementRevisionBaseline = measurementRevisionBaseline,
                    probe = probe,
                )
                check(probe.visible >= MINIMUM_VISIBLE_POSTS) {
                    "Live Reddit paging left only ${probe.visible} visible posts after " +
                        "$observedAppendPageCount append pages: " +
                        probe.raw
                }
                check(observedAppendPageCount >= APPEND_PAGES_PER_ITERATION || probe.next) {
                    "Reddit reported end-of-listing before the stress sample reached " +
                        "$APPEND_PAGES_PER_ITERATION append pages: ${probe.raw}"
                }
                Thread.sleep(PAGING_GOVERNOR_GAP_MS)
            }

            val measuredLogs = feedLogs()
            check("failed operation=" !in measuredLogs) {
                "Live Reddit reported a paging failure: ${feedLogTail()}"
            }
            val appendPages = appliedPages(measuredLogs)
                .filter { page -> page.operation == "append" }
                .takeLast(APPEND_PAGES_PER_ITERATION)
            check(appendPages.size >= APPEND_PAGES_PER_ITERATION) {
                "Expected at least $APPEND_PAGES_PER_ITERATION live append pages, " +
                    "found ${appendPages.size}: ${feedLogTail()}"
            }
            check(appendPages.takeLast(APPEND_PAGES_PER_ITERATION).all { page -> page.network > 0 }) {
                "A measured append did not contain live Reddit network data: $appendPages"
            }
            check(
                appendPages.takeLast(APPEND_PAGES_PER_ITERATION)
                    .zipWithNext()
                    .all { (before, after) -> after.stored > before.stored },
            ) {
                "Stored post count did not grow across live append pages: $appendPages"
            }
            check(probe.visibleRemote > 0) {
                "The live stress sample contained no visible remote media: ${probe.raw}"
            }

            val mode = if (coldProcess) "cold_process" else "warm_cache"
            val line = buildString {
                append("mode=").append(mode)
                append(" iteration=").append(iteration ?: -1)
                append(" pages=").append(observedAppendPageCount)
                append(" final_stored=").append(probe.stored)
                append(" final_visible=").append(probe.visible)
                append(" visible_remote=").append(probe.visibleRemote)
                append(" visible_kinds=").append(probe.visibleKinds)
                append(" network=").append(appendPages.joinToString(",") { it.network.toString() })
            }
            evidence += line
            Log.i(BENCHMARK_EVIDENCE_LOG_TAG, line)
        }

        check(evidence.isNotEmpty()) { "Live Reddit benchmark produced no runtime evidence" }
        println("FEED04B_LIVE_EVIDENCE_BEGIN")
        evidence.forEach(::println)
        println("FEED04B_LIVE_EVIDENCE_END")
    }

    private fun MacrobenchmarkScope.dismissFirstRunSurfaces() {
        device.waitForIdle()
        device.findObject(By.text("Use Right"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Later"))?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.openHomeFeed() {
        device.waitForIdle()
        if (device.hasObject(By.descContains(PROBE_PREFIX))) return

        repeat(MAX_BACK_ATTEMPTS) {
            device.findObject(By.desc(OPEN_NAVIGATION_DESCRIPTION))?.let { openNavigation ->
                openNavigation.click()
                val home = checkNotNull(
                    device.wait(Until.findObject(By.text(HOME_FEED_LABEL)), UI_TIMEOUT_MS),
                ) {
                    "Navigation drawer did not expose the Home feed"
                }
                home.click()
                device.waitForIdle()
                return
            }

            device.pressBack()
            device.waitForIdle()
            if (device.hasObject(By.descContains(PROBE_PREFIX))) return
        }

        error("Could not return to the Home feed before live feed setup")
    }

    private fun MacrobenchmarkScope.feedList(): UiObject2 {
        checkNotNull(
            device.wait(
                Until.findObject(By.scrollable(true)),
                UI_TIMEOUT_MS,
            ),
        ) {
            "Home feed did not expose any scrollable live Reddit content"
        }

        val candidates = device.findObjects(By.scrollable(true))
            .mapNotNull { candidate ->
                runCatching {
                    val bounds = candidate.visibleBounds
                    candidate to bounds
                }.getOrNull()
            }
            .filter { (_, bounds) -> bounds.height() > bounds.width() }

        return checkNotNull(
            candidates.maxByOrNull { (_, bounds) -> bounds.width() * bounds.height() }?.first,
        ) {
            "Home feed did not expose a vertical scrollable list; " +
                "scrollable_candidates=${device.findObjects(By.scrollable(true)).size}"
        }
    }

    private fun MacrobenchmarkScope.currentTargetProbeLogLines(
        maxLines: Int,
    ): List<String> {
        val pid = device.executeShellCommand("pidof -s $TARGET_PACKAGE").trim()
        if (pid.isEmpty() || !pid.all { character -> character.isDigit() }) {
            return emptyList()
        }
        return device.executeShellCommand(
            "logcat -d -t $maxLines -v raw --pid=$pid -s $BENCHMARK_PROBE_LOG_TAG:I",
        ).lineSequence()
            .filter { line -> line.contains(PROBE_PREFIX) }
            .toList()
    }

    private fun MacrobenchmarkScope.readLiveProbe(): FeedProbe {
        val description = currentTargetProbeLogLines(maxLines = 1).lastOrNull()
            ?: device.findObject(By.descContains(PROBE_PREFIX))?.contentDescription
        check(!description.isNullOrBlank()) {
            "DEV1 benchmark build did not expose the FEED04B probe marker"
        }
        return FeedProbe.parse(description)
    }

    private fun MacrobenchmarkScope.waitForLiveProbe(
        timeoutMs: Long,
        predicate: (FeedProbe) -> Boolean,
    ): FeedProbe {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var latest: FeedProbe? = null
        var lastProbeError = "none"
        do {
            latest = runCatching { readLiveProbe() }
                .onFailure { error ->
                    lastProbeError =
                        "${error.javaClass.simpleName}:${error.message.orEmpty()}"
                }
                .getOrNull()
            if (latest != null && predicate(latest)) return latest
            Thread.sleep(PROBE_POLL_INTERVAL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)

        error(
            "Timed out waiting for live Reddit feed state; " +
                "latest=${latest?.raw ?: "unavailable"} " +
                "last_probe_error=$lastProbeError " +
                "probe_logs=${probeLogTail()} feed_logs=${feedLogTail()}",
        )
    }

    private fun measuredAppendPageCount(
        measurementRevisionBaseline: Long,
        probe: FeedProbe,
    ): Int {
        check(probe.revision >= measurementRevisionBaseline) {
            "Live Reddit content revision moved backwards: " +
                "baseline=$measurementRevisionBaseline current=${probe.revision}"
        }
        return (probe.revision - measurementRevisionBaseline).toInt()
    }

    private fun MacrobenchmarkScope.flingUntilAppendPageCount(
        measurementRevisionBaseline: Long,
        requiredPageCount: Int,
    ): FeedProbe {
        val deadline = SystemClock.elapsedRealtime() + NETWORK_TIMEOUT_MS
        var latest = readLiveProbe()
        var burstCount = 0

        do {
            repeat(FLINGS_PER_BURST) {
                flingFeedDown()
            }
            burstCount += 1

            // Each UiAutomator fling can become a distinct scroll session, so
            // one burst may append multiple pages. Count pages actually applied.
            device.waitForIdle()
            val observationDeadline = minOf(
                deadline,
                SystemClock.elapsedRealtime() + FLING_BURST_OBSERVATION_MS,
            )
            do {
                latest = runCatching { readLiveProbe() }.getOrElse { latest }
                if (
                    latest.source == "reddit" &&
                    latest.feed == "home" &&
                    !latest.inProgress &&
                    latest.failure == "none" &&
                    measuredAppendPageCount(
                        measurementRevisionBaseline = measurementRevisionBaseline,
                        probe = latest,
                    ) >= requiredPageCount
                ) {
                    return latest
                }
                Thread.sleep(PROBE_POLL_INTERVAL_MS)
            } while (SystemClock.elapsedRealtime() < observationDeadline)
        } while (SystemClock.elapsedRealtime() < deadline)

        error(
            "Timed out after $burstCount distinct fling bursts waiting for " +
                "$requiredPageCount live Reddit append pages; " +
                "observed=${measuredAppendPageCount(measurementRevisionBaseline, latest)} " +
                "latest=${latest.raw} logs=${feedLogTail()}",
        )
    }

    private fun MacrobenchmarkScope.flingFeedDown() {
        repeat(STALE_OBJECT_RETRIES) {
            try {
                feedList().apply {
                    setGestureMarginPercentage(GESTURE_MARGIN_PERCENT)
                    fling(Direction.DOWN, FLING_SPEED_PX_PER_SECOND)
                }
                return
            } catch (_: StaleObjectException) {
                device.waitForIdle()
            }
        }
        error("Home feed remained stale after $STALE_OBJECT_RETRIES reacquisition attempts")
    }

    private fun UiObject2.moveToTop() {
        repeat(MAX_RESET_FLINGS) {
            if (!scroll(Direction.UP, RESET_SCROLL_PERCENT, RESET_SCROLL_SPEED)) return
        }
        error("Home feed did not return to the top after $MAX_RESET_FLINGS reset gestures")
    }

    private fun MacrobenchmarkScope.appliedPages(): List<AppliedPage> =
        appliedPages(feedLogs())

    private fun MacrobenchmarkScope.waitForAppliedNetworkPage(
        operation: String,
        baseline: Int,
    ): AppliedPage {
        val deadline = SystemClock.elapsedRealtime() + NETWORK_TIMEOUT_MS
        var latest: AppliedPage? = null
        do {
            latest = appliedPages().drop(baseline)
                .lastOrNull { page -> page.operation == operation }
            if (latest != null && latest.network > 0) return latest
            Thread.sleep(PROBE_POLL_INTERVAL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)

        error(
            "Timed out waiting for an applied $operation network page after baseline=$baseline; " +
                "latest=$latest logs=${feedLogTail()}",
        )
    }

    private fun appliedPages(logs: String): List<AppliedPage> =
        APPLIED_PAGE_REGEX.findAll(logs).map { match ->
            AppliedPage(
                operation = match.groupValues[1],
                network = match.groupValues[2].toInt(),
                stored = match.groupValues[3].toInt(),
                endReached = match.groupValues[4].toBooleanStrict(),
            )
        }.toList()

    private fun MacrobenchmarkScope.feedLogs(): String =
        device.executeShellCommand("logcat -d -v raw -s subshell-feed:D")

    private fun MacrobenchmarkScope.feedLogTail(): String =
        feedLogs().lineSequence().filter(String::isNotBlank).toList().takeLast(LOG_TAIL_LINES)
            .joinToString(" | ")

    private fun MacrobenchmarkScope.probeLogTail(): String =
        currentTargetProbeLogLines(maxLines = LOG_TAIL_LINES)
            .takeLast(LOG_TAIL_LINES)
            .joinToString(" | ")

    private data class AppliedPage(
        val operation: String,
        val network: Int,
        val stored: Int,
        val endReached: Boolean,
    )

    private data class FeedProbe(
        val raw: String,
        val source: String,
        val feed: String,
        val generation: Int,
        val revision: Long,
        val stored: Int,
        val visible: Int,
        val inProgress: Boolean,
        val operation: String,
        val failure: String,
        val next: Boolean,
        val visibleRemote: Int,
        val visibleKinds: String,
    ) {
        companion object {
            fun parse(raw: String): FeedProbe {
                val markerAt = raw.indexOf(PROBE_PREFIX)
                require(markerAt >= 0) { "Malformed FEED04B probe: $raw" }
                val fields = raw.substring(markerAt)
                    .split(' ')
                    .drop(1)
                    .mapNotNull { token ->
                        val separator = token.indexOf('=')
                        if (separator <= 0) null else {
                            token.substring(0, separator) to token.substring(separator + 1)
                        }
                    }
                    .toMap()

                fun required(name: String): String =
                    requireNotNull(fields[name]) { "FEED04B probe omitted $name: $raw" }

                return FeedProbe(
                    raw = raw,
                    source = required("source"),
                    feed = required("feed"),
                    generation = required("generation").toInt(),
                    revision = required("revision").toLong(),
                    stored = required("stored").toInt(),
                    visible = required("visible").toInt(),
                    inProgress = required("in_progress").toBooleanStrict(),
                    operation = required("operation"),
                    failure = required("failure"),
                    next = required("next").toBooleanStrict(),
                    visibleRemote = required("visible_remote").toInt(),
                    visibleKinds = required("visible_kinds"),
                )
            }
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "io.github.brealorg.subshell.debug.dev1"
        const val HOME_FEED_LABEL = "Home"
        const val OPEN_NAVIGATION_DESCRIPTION = "Open navigation"
        const val PROBE_PREFIX = "subshell_feed_probe"
        const val BENCHMARK_PROBE_LOG_TAG = "subshell-feed04b-probe"
        const val BENCHMARK_EVIDENCE_LOG_TAG = "subshell-feed04b-benchmark"

        const val COLD_ITERATIONS = 3
        const val WARMUP_ITERATIONS = 2
        const val WARM_ITERATIONS = 5
        const val APPEND_PAGES_PER_ITERATION = 3
        const val FLINGS_PER_BURST = 5
        const val MINIMUM_VISIBLE_POSTS = 10

        const val UI_TIMEOUT_MS = 15_000L
        const val NETWORK_TIMEOUT_MS = 30_000L
        const val SETTLE_DELAY_MS = 500L
        const val FLING_BURST_OBSERVATION_MS = 1_000L
        const val PAGING_GOVERNOR_GAP_MS = 1_600L
        const val PROBE_POLL_INTERVAL_MS = 75L
        const val MAX_BACK_ATTEMPTS = 4
        const val MAX_RESET_FLINGS = 30
        const val STALE_OBJECT_RETRIES = 3
        const val LOG_TAIL_LINES = 12

        const val GESTURE_MARGIN_PERCENT = 0.15f
        const val REFRESH_SWIPE_PERCENT = 0.85f
        const val REFRESH_SWIPE_SPEED = 1_500
        const val RESET_SCROLL_PERCENT = 1f
        const val RESET_SCROLL_SPEED = 12_000
        const val FLING_SPEED_PX_PER_SECOND = 18_000

        val APPLIED_PAGE_REGEX = Regex(
            "applied operation=(\\w+) network=(\\d+) stored=(\\d+) end=(true|false)",
        )
    }
}
