package app.subshell.prototype.harness

import android.content.Context
import app.subshell.prototype.media.VideoPlaybackCoordinator
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowSystemClock

/**
 * MEDIA3TESTHANG01: releases [this] the way a phone would. Since Media3 1.11,
 * ExoPlayer.release() waits for its playback thread's answer with a timeout
 * measured on SystemClock. Late in a full suite run the playback thread
 * sometimes never answered while about 330 unreleased players shared the JVM
 * (since TESTVIDEOLEAK01 releases them, three plain-release runs were clean;
 * the exact trigger is not proven), and Robolectric's clock stands still
 * while the test thread
 * waits, so the 500 ms timeout never expires and the suite hangs for good.
 * A helper thread moves the clock on while release() waits; on a device the
 * wait ends the same way, after at most the release timeout.
 */
internal fun VideoPlaybackCoordinator.releaseWithoutHanging() {
    val released = AtomicBoolean(false)
    val clockMover = thread(isDaemon = true, name = "media3-test-release-clock") {
        while (!released.get()) {
            Thread.sleep(CLOCK_STEP_MS)
            if (!released.get()) ShadowSystemClock.advanceBy(Duration.ofMillis(CLOCK_STEP_MS))
        }
    }
    try {
        release()
    } finally {
        released.set(true)
        clockMover.join()
    }
}

private const val CLOCK_STEP_MS = 100L

/**
 * TESTVIDEOLEAK01: every test makes its video coordinator here. A coordinator
 * holds an ExoPlayer with its own playback thread; tests that never released
 * theirs left about 330 players running per full suite in one JVM. Robolectric
 * gives each test a new Application, so a coordinator made under an earlier
 * one belongs to a finished test and is released (without hanging) before the
 * next one is made. Coordinators of the running test are never touched.
 */
internal fun testVideoPlaybackCoordinator(context: Context): VideoPlaybackCoordinator {
    val current = currentTestApplication()
    val finished = synchronized(liveTestCoordinators) {
        liveTestCoordinators.filter { it.application !== current }.also { liveTestCoordinators.removeAll(it) }
    }
    finished.forEach { runCatching { it.coordinator.releaseWithoutHanging() } }
    return VideoPlaybackCoordinator(context).also { coordinator ->
        synchronized(liveTestCoordinators) { liveTestCoordinators += TestCoordinator(coordinator, current) }
    }
}

/** The running test's Application; TestVideoLeak01Test swaps it to act as the next test. */
internal var currentTestApplication: () -> Any = { RuntimeEnvironment.getApplication() }

private class TestCoordinator(val coordinator: VideoPlaybackCoordinator, val application: Any)

private val liveTestCoordinators = mutableListOf<TestCoordinator>()
