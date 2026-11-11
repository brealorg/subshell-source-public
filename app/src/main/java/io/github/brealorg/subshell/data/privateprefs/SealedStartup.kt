package io.github.brealorg.subshell.data.privateprefs

import android.util.Log
import io.github.brealorg.subshell.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * PRIVATEPREFS05_START_DOES_NOT_WAIT_FOR_THE_KEYSTORE_V1 (#296): the first screen waits for the
 * start-up preferences, and every sealed value costs a Keystore call. On some phones (a Galaxy A36
 * after the update to 1.0.0 showed only the window background for ten seconds and more) those
 * calls are slow, and the first start after an update has the most to do.
 *
 * - Values that are already sealed are opened once, in memory, for the first reads on Main, but
 *   the start waits for that at most [maxWaitMs]. A value not yet open is opened by its first read.
 * - Values an older build stored in plain text read as they are, without the Keystore, so they are
 *   sealed afterwards, in the background, and nothing waits for that.
 */
internal object SealedStartup {
    const val DEFAULT_MAX_WAIT_MS = 2_000L

    suspend fun openThenSealInBackground(
        preferences: List<SealedKeysPreferences>,
        scope: CoroutineScope,
        maxWaitMs: Long = DEFAULT_MAX_WAIT_MS,
    ) {
        val startedAt = System.nanoTime()
        val opening = scope.async {
            var opened = 0
            preferences.forEach { file -> opened += runCatching { file.warm() }.getOrDefault(0) }
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "opened $opened sealed values in ${elapsedMs(startedAt)} ms")
        }
        val inTime = withTimeoutOrNull(maxWaitMs) { opening.await() } != null
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "start waited ${elapsedMs(startedAt)} ms for sealed values (in time: $inTime)")
        scope.launch {
            opening.join()
            val sealingStartedAt = System.nanoTime()
            var sealed = 0
            preferences.forEach { file -> sealed += runCatching { file.sealLegacyValues() }.getOrDefault(0) }
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "sealed $sealed plain values in the background in ${elapsedMs(sealingStartedAt)} ms")
        }
    }

    private fun elapsedMs(since: Long): Long = (System.nanoTime() - since) / 1_000_000

    /** Tag of the start-up timing lines (the lane log of a dev build keeps them; a release build logs nothing). */
    const val LOG_TAG = "SubshellStartup"
}
