package app.subshell.prototype.data.auth.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Crosspost01A1E2DestinationCrosspostablePolicyTest {
    @Test
    fun publicAboutCrosspostableIsAuthoritative() {
        val destination = RedditCommunityAboutData(
            crosspostable = true,
            allowCrossposts = false,
            subredditType = "public",
            userIsBanned = false,
            userCanSubmit = true,
        ).toCrosspostDestination(
            accountStableId = "account-1",
            community = "subshell",
        )

        assertEquals(true, destination.allowCrossposts)
    }

    @Test
    fun legacyAllowPostCrosspostsRemainsFallback() {
        val destination = RedditCommunityAboutData(
            crosspostable = null,
            allowCrossposts = true,
            subredditType = "public",
            userIsBanned = false,
            userCanSubmit = true,
        ).toCrosspostDestination(
            accountStableId = "account-1",
            community = "subshell",
        )

        assertEquals(true, destination.allowCrossposts)
    }

    @Test
    fun missingBothSignalsRemainsUnknownAndFailsClosedDownstream() {
        val destination = RedditCommunityAboutData(
            crosspostable = null,
            allowCrossposts = null,
            subredditType = "public",
            userIsBanned = false,
            userCanSubmit = true,
        ).toCrosspostDestination(
            accountStableId = "account-1",
            community = "subshell",
        )

        assertNull(destination.allowCrossposts)
    }
}
