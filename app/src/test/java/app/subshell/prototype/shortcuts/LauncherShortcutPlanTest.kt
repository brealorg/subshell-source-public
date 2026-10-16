package app.subshell.prototype.shortcuts

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShortcutPlanTest {
    @Test
    fun offPublishesNoDynamicShortcuts() {
        val plan = planLauncherShortcuts(
            enabled = false,
            favoriteCommunityNames = listOf("android", "kotlin"),
            hiddenCommunityNames = emptyList(),
            pinned = emptyList(),
        )

        assertEquals(emptyList<LauncherShortcutEntry>(), plan.dynamic)
    }

    @Test
    fun onPublishesFavouritesByNameWithoutHiddenCappedAtFour() {
        val plan = planLauncherShortcuts(
            enabled = true,
            favoriteCommunityNames = listOf("android", "Secret", "kotlin", "linux", "homelab", "rust"),
            hiddenCommunityNames = listOf("secret"),
            pinned = emptyList(),
        )

        assertEquals(
            listOf("r/android", "r/homelab", "r/kotlin", "r/linux"),
            plan.dynamic.map(LauncherShortcutEntry::label),
        )
    }

    @Test
    fun aPinnedShortcutIsDisabledWhenHiddenEvenWithTheSettingOff() {
        val plan = planLauncherShortcuts(
            enabled = false,
            favoriteCommunityNames = emptyList(),
            hiddenCommunityNames = listOf("Secret"),
            pinned = listOf(
                PinnedLauncherShortcut(communityShortcutId("secret"), enabled = true),
                PinnedLauncherShortcut(communityShortcutId("android"), enabled = true),
            ),
        )

        assertEquals(listOf(communityShortcutId("secret")), plan.disablePinnedIds)
        assertEquals(emptyList<String>(), plan.enableCommunities)
    }

    @Test
    fun aDisabledPinIsEnabledAgainOnceNotHidden() {
        val plan = planLauncherShortcuts(
            enabled = true,
            favoriteCommunityNames = emptyList(),
            hiddenCommunityNames = emptyList(),
            pinned = listOf(
                PinnedLauncherShortcut(communityShortcutId("secret"), enabled = false),
                PinnedLauncherShortcut("something-else", enabled = false),
            ),
        )

        assertEquals(listOf("secret"), plan.enableCommunities)
        assertEquals(emptyList<String>(), plan.disablePinnedIds)
    }

    @Test
    fun aReenabledPinKeepsTheFavouritesDisplayName() {
        val plan = planLauncherShortcuts(
            enabled = true,
            favoriteCommunityNames = listOf("Android"),
            hiddenCommunityNames = emptyList(),
            pinned = listOf(PinnedLauncherShortcut(communityShortcutId("android"), enabled = false)),
        )

        assertEquals(listOf("Android"), plan.enableCommunities)
    }

    @Test
    fun inboxComesFirstThenCommunitiesAndFeedsByNameWithinFourSlots() {
        val plan = planLauncherShortcuts(
            enabled = true,
            favoriteCommunityNames = listOf("android", "zig"),
            hiddenCommunityNames = emptyList(),
            pinned = emptyList(),
            favoriteCustomFeeds = listOf(
                LauncherShortcutEntry.CustomFeed("reddit:alice", "/user/alice/m/cats", "Cats"),
                LauncherShortcutEntry.CustomFeed("reddit:alice", "/user/alice/m/news", "News"),
            ),
            inbox = LauncherShortcutEntry.Inbox("reddit:alice"),
        )

        assertEquals(
            listOf("Inbox", "r/android", "Cats", "News"),
            plan.dynamic.map(LauncherShortcutEntry::label),
        )
    }

    @Test
    fun feedAndInboxIdsBelongToTheirAccount() {
        assertEquals("inbox:reddit:alice", LauncherShortcutEntry.Inbox("reddit:alice").id)
        assertEquals(
            "feed:reddit:alice:/user/alice/m/cats",
            LauncherShortcutEntry.CustomFeed("reddit:alice", "/user/Alice/m/Cats", "Cats").id,
        )
        assertEquals(
            LauncherShortcutEntry.CustomFeed("reddit:alice", "/user/alice/m/cats", "Cats").id,
            LauncherShortcutEntry.CustomFeed("reddit:alice", "/user/alice/m/cats/", "Cats").id,
        )
        assertEquals("cats", customFeedShortcutLabel("/user/alice/m/cats", null))
        assertEquals("Cats", customFeedShortcutLabel("/user/alice/m/cats", "Cats"))
    }

    @Test
    fun pinsOfFeedsAndInboxAreLeftAlone() {
        val plan = planLauncherShortcuts(
            enabled = false,
            favoriteCommunityNames = emptyList(),
            hiddenCommunityNames = emptyList(),
            pinned = listOf(
                PinnedLauncherShortcut("inbox:reddit:bob", enabled = false),
                PinnedLauncherShortcut("feed:reddit:bob:/user/bob/m/x", enabled = true),
            ),
        )

        assertEquals(emptyList<String>(), plan.enableCommunities)
        assertEquals(emptyList<String>(), plan.disablePinnedIds)
    }
}
