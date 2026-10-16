package app.subshell.prototype.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue16PostSaveIntentQueuePolicyTest {
    @Test
    fun controlAllowsIdleAndSameTargetButKeepsOtherTargetSerialized() {
        assertTrue(
            postSaveTargetControlEnabled(
                activeAccountIsAuthenticated = true,
                inFlightFullname = null,
                targetFullname = "t3_alpha",
            ),
        )
        assertTrue(
            postSaveTargetControlEnabled(
                activeAccountIsAuthenticated = true,
                inFlightFullname = "t3_alpha",
                targetFullname = "t3_alpha",
            ),
        )
        assertFalse(
            postSaveTargetControlEnabled(
                activeAccountIsAuthenticated = true,
                inFlightFullname = "t3_other",
                targetFullname = "t3_alpha",
            ),
        )
        assertFalse(
            postSaveTargetControlEnabled(
                activeAccountIsAuthenticated = false,
                inFlightFullname = null,
                targetFullname = "t3_alpha",
            ),
        )
    }

    @Test
    fun successDispatchesOnlyAQueuedDesiredStateThatStillDiffers() {
        assertEquals(
            false,
            queuedPostSaveFollowUp(
                acknowledgedSaved = true,
                queuedDesiredSaved = false,
            ),
        )
        assertEquals(
            true,
            queuedPostSaveFollowUp(
                acknowledgedSaved = false,
                queuedDesiredSaved = true,
            ),
        )
        assertNull(
            queuedPostSaveFollowUp(
                acknowledgedSaved = true,
                queuedDesiredSaved = true,
            ),
        )
        assertNull(
            queuedPostSaveFollowUp(
                acknowledgedSaved = false,
                queuedDesiredSaved = null,
            ),
        )
    }

    @Test
    fun integrationUsesOneRemoteRequestAtATimeAndLastIntentWins() {
        val state = source("ui/SubshellState.kt") + source("ui/state/PostSaveState.kt")
        val common = source("ui/components/Common.kt")
        val swipe = source("ui/components/SwipeActionSurface.kt")

        assertTrue(state.contains("ISSUE16_SAVE_LAST_INTENT_QUEUE_V1"))
        assertTrue(state.contains("authenticatedPostSaveQueuedDesired[postFullname] = targetSaved"))
        assertTrue(state.contains("authenticatedPostSaveQueuedDesired[commentFullname] = targetSaved"))
        assertTrue(state.contains("if (currentInFlight != null && currentInFlight != postFullname) return"))
        assertTrue(state.contains("if (currentInFlight == postFullname)"))
        assertTrue(state.contains("val queuedFollowUp = queuedPostSaveFollowUp("))
        assertTrue(state.contains("authenticatedPostSaveRollback[postFullname] = saved"))
        assertTrue(state.contains("authenticatedPostSaveQueuedDesired.remove(postFullname)"))
        assertFalse(state.contains("if (authenticatedPostSaveInFlight != null) return"))

        assertTrue(common.contains("saveActionsEnabled = state.postSaveControlEnabled(post)"))
        assertTrue(swipe.contains("state.postSaveControlEnabled(post)"))
        assertFalse(common.contains("saveActionsEnabled = state.localMutationsAllowed || state.authenticatedPostSavingEnabled"))
    }

    private fun source(relative: String): String =
        File("src/main/java/app/subshell/prototype/$relative").readText()
}
