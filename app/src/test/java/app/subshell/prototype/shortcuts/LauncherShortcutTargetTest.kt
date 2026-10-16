package app.subshell.prototype.shortcuts

import app.subshell.prototype.data.account.CustomFeedFailureKind
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherShortcutTargetTest {
    private val cats = CustomFeedSummary(
        path = "/user/alice/m/Cats/",
        displayName = "Cats",
        description = "",
        visibility = "private",
        subredditNames = emptyList(),
    )
    private val feedTarget = LauncherShortcutTarget.CustomFeed("reddit:alice", "/user/alice/m/cats", 1L)
    private val loaded = CustomFeedsUiState(
        accountStableId = "reddit:alice",
        feeds = listOf(cats),
        contentRevision = 1L,
    )

    private fun resolve(
        target: LauncherShortcutTarget,
        active: String = "reddit:alice",
        feeds: CustomFeedsUiState = loaded,
    ) = resolveLauncherShortcutTarget(
        target = target,
        activeAccountId = active,
        signedInAccountIds = listOf("reddit:alice", "reddit:bob"),
        customFeeds = feeds,
    )

    @Test
    fun parsesOnlyItsOwnActionWithAnAccountAndAValidFeedPath() {
        assertEquals(
            LauncherShortcutTarget.CustomFeed("reddit:alice", "/user/alice/m/cats", 3L),
            launcherShortcutTargetOrNull(LAUNCHER_SHORTCUT_ACTION, "custom_feed", "reddit:alice", "user/alice/m/cats/", 3L),
        )
        assertNull(launcherShortcutTargetOrNull("android.intent.action.VIEW", "inbox", "reddit:alice", null, 3L))
        assertNull(launcherShortcutTargetOrNull(LAUNCHER_SHORTCUT_ACTION, "inbox", " ", null, 3L))
        assertNull(launcherShortcutTargetOrNull(LAUNCHER_SHORTCUT_ACTION, "custom_feed", "reddit:alice", "/r/cats", 3L))
        assertNull(launcherShortcutTargetOrNull(LAUNCHER_SHORTCUT_ACTION, "other", "reddit:alice", null, 3L))
    }

    @Test
    fun anotherSignedInAccountIsNamedAndAMissingOneIsGone() {
        assertEquals(
            LauncherShortcutResolution.OtherAccount("reddit:alice"),
            resolve(feedTarget, active = "reddit:bob"),
        )
        assertEquals(
            LauncherShortcutResolution.AccountGone,
            resolve(LauncherShortcutTarget.Inbox("reddit:carol", 1L), active = "reddit:bob"),
        )
    }

    @Test
    fun aFeedWaitsForItsAccountsListThenOpensWithTheListsPath() {
        assertEquals(LauncherShortcutResolution.LoadFeeds, resolve(feedTarget, feeds = CustomFeedsUiState()))
        assertEquals(
            LauncherShortcutResolution.LoadFeeds,
            resolve(feedTarget, feeds = loaded.copy(accountStableId = "reddit:bob")),
        )
        assertEquals(LauncherShortcutResolution.LoadFeeds, resolve(feedTarget, feeds = loaded.copy(loading = true)))
        // Reddit's path, trailing slash and all: the feed is shown by the list's path.
        assertEquals(LauncherShortcutResolution.OpenFeed("/user/alice/m/Cats/"), resolve(feedTarget))
    }

    @Test
    fun aFailedListOrAMissingFeedIsReported() {
        assertEquals(
            LauncherShortcutResolution.FeedsFailed,
            resolve(feedTarget, feeds = loaded.copy(failureKind = CustomFeedFailureKind.entries.first())),
        )
        assertEquals(
            LauncherShortcutResolution.FeedMissing,
            resolve(feedTarget, feeds = loaded.copy(feeds = emptyList())),
        )
    }

    @Test
    fun inboxOpensForItsAccount() {
        assertEquals(LauncherShortcutResolution.OpenInbox, resolve(LauncherShortcutTarget.Inbox("reddit:alice", 1L)))
    }
}
