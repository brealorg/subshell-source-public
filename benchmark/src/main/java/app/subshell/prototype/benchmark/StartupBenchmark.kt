package app.subshell.prototype.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF01_STARTUP_BENCHMARK_V1 (owner 2026-10-16): cold start time without any
 * ahead-of-time compilation (a fresh install without a profile) and with the
 * baseline profile the APK carries.
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
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private companion object {
        const val TARGET_PACKAGE = "io.github.brealorg.subshell.benchmark"
        const val ITERATIONS = 10
    }
}
