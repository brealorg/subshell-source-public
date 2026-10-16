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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE65_POST_ACTIONS_WRITTEN_OFF_MAIN_V1 / ISSUE65_READ_MARKERS_CAPPED_V1:
 * every first open of a post committed the account's post actions (and with
 * them the whole settings file) on the main thread, a failed write crashed
 * the app, and the read markers grew for as long as the app was installed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue65PostActionsOffMainComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openingAPostDoesNotCommitTheSettingsFile() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(application)
        val preferences = CommitCountingPreferences(
            application.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE),
        )
        val context = object : ContextWrapper(application) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
                if (name == "subshell_preferences") preferences else super.getSharedPreferences(name, mode)
        }
        var state: SubshellState? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                state = rememberSubshellState()
            }
        }
        composeRule.waitForIdle()
        val launched = requireNotNull(state)
        preferences.commits = 0

        launched.markPostRead("issue65")

        assertEquals(0, preferences.commits)
        val readKey = accountPostActionsPreferenceKey(launched.activeAccountUsername, ACCOUNT_ACTION_FIELD_READ)
        assertTrue("issue65" in preferences.getStringSet(readKey, emptySet()).orEmpty())
    }

    @Test
    fun readMarkersKeepOnlyTheNewestPosts() {
        // Reddit post ids are base36 and grow with time.
        val postIds = (1..READ_POST_IDS_LIMIT + 2).map { it.toString(36) }.toSet()

        val read = normalizeAccountPostActions(AccountPostActions(readPostIds = postIds), "me").readPostIds

        assertEquals(READ_POST_IDS_LIMIT, read.size)
        assertFalse("1" in read)
        assertFalse("2" in read)
        assertTrue((READ_POST_IDS_LIMIT + 2).toString(36) in read)
    }

    private class CommitCountingPreferences(
        private val delegate: SharedPreferences,
    ) : SharedPreferences by delegate {
        var commits = 0

        override fun edit(): SharedPreferences.Editor = CountingEditor(delegate.edit())

        // Every setter returns this editor, so a chained commit() is counted too.
        private inner class CountingEditor(
            private val editor: SharedPreferences.Editor,
        ) : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?) = apply { editor.putString(key, value) }
            override fun putStringSet(key: String?, values: MutableSet<String>?) =
                apply { editor.putStringSet(key, values) }
            override fun putInt(key: String?, value: Int) = apply { editor.putInt(key, value) }
            override fun putLong(key: String?, value: Long) = apply { editor.putLong(key, value) }
            override fun putFloat(key: String?, value: Float) = apply { editor.putFloat(key, value) }
            override fun putBoolean(key: String?, value: Boolean) = apply { editor.putBoolean(key, value) }
            override fun remove(key: String?) = apply { editor.remove(key) }
            override fun clear() = apply { editor.clear() }
            override fun apply() = editor.apply()
            override fun commit(): Boolean {
                commits += 1
                return editor.commit()
            }
        }
    }
}
