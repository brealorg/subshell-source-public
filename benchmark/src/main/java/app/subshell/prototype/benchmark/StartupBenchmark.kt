package app.subshell.prototype.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF01_STARTUP_BENCHMARK_V1 (owner 2026-10-16): cold start time without any
 * ahead-of-time compilation (a fresh install without a profile) and with the
 * baseline profile the APK carries.
 *
 * ISSUE259_STARTUP_BENCHMARK_ON_THE_SIGNED_IN_LANE_APP_V1: signed out, a cold
 * start measures only the onboarding. Built with `-Psubshell.debugSlot=dev2`
 * (minified, so the profile applies) the benchmark app installs over the
 * signed-in lane app; pass `-e targetPackage io.github.brealorg.subshell.debug.dev2`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartWithoutProfile() = measureColdStart(CompilationMode.None())

    @Test
    fun coldStartWithBaselineProfile() =
        measureColdStart(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun measureColdStart(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = InstrumentationRegistry.getArguments().getString("targetPackage") ?: TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        // Home reports drawn (ReportDrawnWhen) once its feed shows; the trace must
        // also hold the frame after it, or StartupTimingMetric cannot read it.
        device.waitForIdle()
    }

    private companion object {
        const val TARGET_PACKAGE = "io.github.brealorg.subshell.benchmark"
        const val ITERATIONS = 10
    }
}
