package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Crosspost01A1E3DestinationSupportPolicyTest {
    @Test
    fun explicitFalseStillBlocks() {
        val destination = ComposerCrosspostDestination(
            accountStableId = "account-1",
            community = "subshell",
            allowCrossposts = false,
        )

        assertEquals(
            "This community does not allow crossposts",
            crosspostDestinationSupportBlockingMessage(destination),
        )
    }

    @Test
    fun explicitTrueDoesNotBlock() {
        val destination = ComposerCrosspostDestination(
            accountStableId = "account-1",
            community = "subshell",
            allowCrossposts = true,
        )

        assertNull(
            crosspostDestinationSupportBlockingMessage(destination),
        )
    }

    @Test
    fun missingLegacySignalIsUnknownNotDenied() {
        val destination = ComposerCrosspostDestination(
            accountStableId = "account-1",
            community = "subshell",
            allowCrossposts = null,
        )

        assertNull(
            crosspostDestinationSupportBlockingMessage(destination),
        )
    }
}
