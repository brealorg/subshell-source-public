package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenSubscriptionsPersistenceTest {
    @Test
    fun togglePersistsNormalizedCommunityNameForStableAccountId() {
        val writes = mutableListOf<Pair<String, Set<String>>>()
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            persistHiddenCommunityNames = { accountId, names ->
                writes += accountId to names
            },
        )

        state.toggleHiddenCommunity("r/Android")

        assertEquals(listOf("android"), state.hiddenCommunityNames)
        assertEquals(
            "local:root" to setOf("android"),
            writes.single(),
        )
        assertTrue(state.isCommunityHidden("/r/android"))
    }

    @Test
    fun accountSwitchReloadsIndependentHiddenSubscriptions() {
        val stored = mutableMapOf(
            "local:root" to setOf("android"),
            "local:packetmoss" to setOf("linux"),
        )
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadHiddenCommunityNames = { accountId -> stored[accountId].orEmpty() },
            persistHiddenCommunityNames = { accountId, names -> stored[accountId] = names },
        )

        assertEquals(listOf("android"), state.hiddenCommunityNames)

        state.switchAccount("packetmoss")
        assertEquals(listOf("linux"), state.hiddenCommunityNames)

        state.toggleHiddenCommunity("homelab")
        state.switchAccount("root")

        assertEquals(listOf("android"), state.hiddenCommunityNames)
        assertEquals(
            setOf("homelab", "linux"),
            stored.getValue("local:packetmoss"),
        )
    }

    @Test
    fun invalidCommunityNameIsIgnoredWithoutPersistenceWrite() {
        var writes = 0
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            persistHiddenCommunityNames = { _, _ -> writes += 1 },
        )

        state.toggleHiddenCommunity("not a subreddit")

        assertTrue(state.hiddenCommunityNames.isEmpty())
        assertEquals(0, writes)
        assertFalse(state.isCommunityHidden("not a subreddit"))
    }

    @Test
    fun hidingDoesNotRemoveFavoriteState() {
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadFavoriteCommunityNames = { setOf("android") },
        )

        state.toggleHiddenCommunity("android")

        assertTrue(state.isCommunityHidden("android"))
        assertTrue(state.isCommunityFavorite("android"))
    }
    @Test
    fun deviceUnlockRequirementIsOptInAndPersistsLocally() {
        val writes = mutableMapOf<String, String>()
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            persistPreference = { key, value ->
                writes[key] = value
            },
        )

        assertFalse(state.requireDeviceUnlockForHiddenSubscriptions)

        state.updateRequireDeviceUnlockForHiddenSubscriptions(true)

        assertTrue(state.requireDeviceUnlockForHiddenSubscriptions)
        assertEquals(
            "true",
            writes["require_device_unlock_hidden_subscriptions"],
        )
    }

}
