package app.subshell.prototype.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Next01A5SubscriptionsCommunityPolicyTest {

    @Test
    fun subscriptionsCommunityWorkspaceRequiresExpandedSupportedSplitMode() {
        assertTrue(
            useExpandedSubscriptionsCommunityListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        assertFalse(
            useExpandedSubscriptionsCommunityListDetail(
                expandedWindow = false,
                supportedOrigin = true,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        assertFalse(
            useExpandedSubscriptionsCommunityListDetail(
                expandedWindow = true,
                supportedOrigin = false,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        val nonSplitMode =
            TabletLayoutMode.values().firstOrNull { mode ->
                mode != TabletLayoutMode.SPLIT
            }

        assertNotNull(nonSplitMode)

        assertFalse(
            useExpandedSubscriptionsCommunityListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                tabletLayoutMode = requireNotNull(nonSplitMode),
            ),
        )
    }
}
