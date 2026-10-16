package app.subshell.prototype.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF01_BASELINE_PROFILE_V1 (owner 2026-10-16): collects the code a cold start
 * runs (process start, Compose, theme and the first screen) into a baseline
 * profile. The profile must name source classes, so the target is the
 * benchmark build without R8 (R8 merges classes even with -dontobfuscate):
 *
 *   ./gradlew :app:assembleBenchmark :benchmark:assembleBenchmark -Psubshell.baselineProfileSource
 *   adb install -r -t app/build/outputs/apk/benchmark/app-benchmark.apk
 *   adb install -r -t benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk
 *   adb shell am instrument -w -e class app.subshell.prototype.benchmark.BaselineProfileGenerator \
 *     -e androidx.benchmark.suppressErrors EMULATOR \
 *     app.subshell.prototype.benchmark/androidx.test.runner.AndroidJUnitRunner
 *   adb pull /sdcard/Android/media/app.subshell.prototype.benchmark/BaselineProfileGenerator_coldStart-startup-prof.txt \
 *     app/src/main/baseline-prof.txt
 *
 * Run it where the benchmark app is signed in to Reddit, so the cold start
 * reaches Home: signed out it records only the onboarding screen. `adb install
 * -r` over a signed-in benchmark app keeps the sign-in. The 2026-10-16 profile
 * came from the owner's Pixel 6 (Home, post cards, image loading).
 *
 * (Gradle's connectedBenchmarkAndroidTest resolves the tested app with JVM
 * variants under AGP 9.4 and fails offline, so the run goes through adb. On the
 * 16 KB-page API 36 emulator Perfetto cannot parse traces, so StartupBenchmark
 * needs a device; `am start-activity -W -S` timings work anywhere.)
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun coldStart() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    private companion object {
        const val TARGET_PACKAGE = "io.github.brealorg.subshell.benchmark"
    }
}
