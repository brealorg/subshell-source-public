package app.subshell.prototype.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A4ModLogPresentationPolicyTest {
    @Test
    fun commonActionLabelsAreProductFacing() {
        assertTrue(moderationLogActionLabel("approvelink") == "Approved post")
        assertTrue(moderationLogActionLabel("removecomment") == "Removed comment")
        assertTrue(moderationLogActionLabel("ignorereports") == "Ignored reports")
    }
}
