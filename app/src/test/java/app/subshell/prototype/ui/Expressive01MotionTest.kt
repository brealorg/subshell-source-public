package app.subshell.prototype.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * EXPRESSIVE01_SPRING_MOTION_V1 (owner 2026-10-18, after a phone check of the
 * test build): the app animates with M3 Expressive's spring motion scheme;
 * Reduce motion keeps the standard scheme, which does not overshoot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Expressive01MotionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun spatialSpec(reduceMotion: Boolean): FiniteAnimationSpec<Float> {
        lateinit var spec: FiniteAnimationSpec<Float>
        composeRule.setContent {
            SubshellTheme(reduceMotion = reduceMotion) {
                spec = MaterialTheme.motionScheme.defaultSpatialSpec()
            }
        }
        composeRule.waitForIdle()
        return spec
    }

    @Test
    fun theAppMovesWithExpressiveSprings() {
        assertEquals(MotionScheme.expressive().defaultSpatialSpec<Float>(), spatialSpec(reduceMotion = false))
    }

    @Test
    fun reduceMotionKeepsTheStandardScheme() {
        assertEquals(MotionScheme.standard().defaultSpatialSpec<Float>(), spatialSpec(reduceMotion = true))
    }
}
