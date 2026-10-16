package app.subshell.prototype.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE71_RESTORE_APPLIES_COMMENT_COLLAPSE_MODE_V1: a restore wrote
 * commentCollapseMode to disk but left the live state on the old mode, and the
 * next automatic backup captured the stale value. Every setting a restore
 * brings must be the live value afterwards; a field added later without a live
 * assignment fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue71RestoreAppliesEverySettingTest {
    /** Defaults with every Boolean flipped and every enum moved to its next constant. */
    private fun everyFlagAndChoiceChanged(): SubshellSettings {
        val settings = SubshellSettings()
        SubshellSettings::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .forEach { field ->
                field.isAccessible = true
                when (val value = field.get(settings)) {
                    is Boolean -> field.set(settings, !value)
                    is Enum<*> -> {
                        val constants = requireNotNull(value.javaClass.enumConstants)
                        field.set(settings, constants[(value.ordinal + 1) % constants.size])
                    }
                }
            }
        return settings
    }

    @Test
    fun theCollapseModeIsLiveAfterARestore() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(persistSettings = {})
        val restored = state.captureSettings().copy(commentCollapseMode = CommentCollapseMode.COMMENT)

        state.restoreSettings(restored)

        assertEquals(CommentCollapseMode.COMMENT, state.commentCollapseMode)
        assertEquals(CommentCollapseMode.COMMENT, state.captureSettings().commentCollapseMode)
    }

    @Test
    fun everyRestoredFlagAndChoiceIsLive() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(persistSettings = {})
        val restored = everyFlagAndChoiceChanged()

        state.restoreSettings(restored)

        assertEquals(restored, state.captureSettings())
    }
}
