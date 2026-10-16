package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CommunitySortPreferencesTest {
    @Test
    fun eachCommunityRestoresItsOwnPersistedSort() {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            persistPreference = { key, value -> persisted[key] = value },
        )

        state.openSubreddit("android")
        assertEquals(CommunitySort.HOT, state.communitySort)
        state.updateCommunitySort(CommunitySort.NEW)
        state.closeSubreddit()

        state.openSubreddit("homelab")
        assertEquals(CommunitySort.HOT, state.communitySort)
        state.updateCommunitySort(CommunitySort.TOP)
        state.closeSubreddit()

        state.openSubreddit("/r/ANDROID")
        assertEquals(CommunitySort.NEW, state.communitySort)
        assertEquals(
            "android=NEW\nhomelab=TOP",
            persisted.getValue(SubshellSettings.COMMUNITY_SORT_PREFERENCES),
        )

        val recreatedSettings = SubshellSettings.fromStorageMap(
            SubshellSettings().toStorageMap() + persisted,
        )
        val recreated = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialCommunitySortPreferences = recreatedSettings.communitySortPreferences,
        )

        recreated.openSubreddit("homelab")
        assertEquals(CommunitySort.TOP, recreated.communitySort)
        recreated.closeSubreddit()
        recreated.openSubreddit("linux")
        assertEquals(CommunitySort.HOT, recreated.communitySort)
    }

    @Test
    fun preferenceCodecNormalizesNamesAndIgnoresInvalidEntries() {
        assertEquals(
            mapOf(
                "android" to CommunitySort.HOT,
                "legacy" to CommunitySort.HOT,
            ),
            decodeCommunitySortPreferences(
                "r/Android=HOT\nlegacy=BEST\ninvalid community=NEW\nlinux=UNKNOWN\nbroken",
            ),
        )
    }
}
