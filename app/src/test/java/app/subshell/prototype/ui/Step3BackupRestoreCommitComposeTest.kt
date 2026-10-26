package app.subshell.prototype.ui

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (quality01d-usektx): restoring a backup writes everything in one
 * synchronous commit to the real settings file (readable at once); if that
 * commit fails the restore stops with an error and the running app keeps its
 * settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3BackupRestoreCommitComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()

    private class CommitCountingPreferences(
        private val delegate: SharedPreferences,
        private val commitSucceeds: Boolean,
    ) : SharedPreferences by delegate {
        var commits = 0
        override fun edit(): SharedPreferences.Editor = CountingEditor(delegate.edit())

        private inner class CountingEditor(private val editor: SharedPreferences.Editor) : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?) = apply { editor.putString(key, value) }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { editor.putStringSet(key, values) }
            override fun putInt(key: String?, value: Int) = apply { editor.putInt(key, value) }
            override fun putLong(key: String?, value: Long) = apply { editor.putLong(key, value) }
            override fun putFloat(key: String?, value: Float) = apply { editor.putFloat(key, value) }
            override fun putBoolean(key: String?, value: Boolean) = apply { editor.putBoolean(key, value) }
            override fun remove(key: String?) = apply { editor.remove(key) }
            override fun clear() = apply { editor.clear() }
            override fun apply() = editor.apply()
            override fun commit(): Boolean {
                commits += 1
                return if (commitSucceeds) editor.commit() else false
            }
        }
    }

    private fun launch(commitSucceeds: Boolean): Pair<SubshellState, CommitCountingPreferences> {
        ensureWorkManagerInitialized(application)
        val real = application.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)
        val preferences = CommitCountingPreferences(real, commitSucceeds)
        // STRICTMODE02: the state reads the application's settings file, so the
        // counting file stands in for the application context too.
        val context = object : ContextWrapper(application) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
                if (name == "subshell_preferences") preferences else super.getSharedPreferences(name, mode)
        }
        var state: SubshellState? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides context) { state = rememberSubshellState() }
        }
        composeRule.waitForIdle()
        preferences.commits = 0
        return requireNotNull(state) to preferences
    }

    @Test
    fun aRestoreIsOneSynchronousCommitReadableAtOnce() {
        val (state, preferences) = launch(commitSucceeds = true)
        val restored = SubshellSettings().copy(themeMode = ThemeMode.LIGHT)

        composeRule.runOnIdle { state.restoreBackupState(restored, joinedCommunityIdsByAccount = null) }

        assertEquals(1, preferences.commits)
        assertEquals(ThemeMode.LIGHT, state.themeMode)
        val stored = application.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)
        assertEquals("LIGHT", stored.getString("theme_mode", null))
    }

    @Test
    fun aRestoreWhoseCommitFailsStopsAndKeepsTheRunningSettings() {
        val (state, _) = launch(commitSucceeds = false)
        val before = state.themeMode
        val restored = SubshellSettings().copy(themeMode = ThemeMode.entries.first { it != before })

        val failure = runCatching {
            composeRule.runOnIdle { state.restoreBackupState(restored, joinedCommunityIdsByAccount = null) }
        }.exceptionOrNull()

        assertTrue("the restore should fail", failure != null)
        assertEquals(before, state.themeMode)
        assertNotEquals(restored.themeMode, state.themeMode)
    }

    // COMMIT01_BACKUP_RESTORE_WRITES_OFF_MAIN_V1 (#243): the backup screen
    // prepares on Main, commits on a worker thread and applies afterwards; the
    // running settings change only once the commit is on disk.
    @Test
    fun aRestoreCommittedOffMainChangesTheAppOnlyWhenApplied() {
        val (state, preferences) = launch(commitSucceeds = true)
        val before = state.themeMode
        val restoredMode = ThemeMode.entries.first { it != before }
        val restored = SubshellSettings().copy(themeMode = restoredMode)
        val prepared = composeRule.runOnIdle { state.prepareBackupRestore(restored, joinedCommunityIdsByAccount = null) }

        val writer = Thread { state.persistBackupRestore(prepared) }
        writer.start()
        writer.join()

        assertEquals(1, preferences.commits)
        val stored = application.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)
        assertEquals(restoredMode.name, stored.getString("theme_mode", null))
        assertEquals(before, state.themeMode)

        composeRule.runOnIdle { state.applyBackupRestore(prepared) }
        assertEquals(restoredMode, state.themeMode)
    }

    @Test
    fun aFailedOffMainCommitThrowsAndLeavesTheAppUnchanged() {
        val (state, _) = launch(commitSucceeds = false)
        val before = state.themeMode
        val restored = SubshellSettings().copy(themeMode = ThemeMode.entries.first { it != before })
        val prepared = composeRule.runOnIdle { state.prepareBackupRestore(restored, joinedCommunityIdsByAccount = null) }

        var failure: Throwable? = null
        val writer = Thread { failure = runCatching { state.persistBackupRestore(prepared) }.exceptionOrNull() }
        writer.start()
        writer.join()

        assertTrue("the commit should fail", failure != null)
        assertEquals(before, state.themeMode)
    }
}
