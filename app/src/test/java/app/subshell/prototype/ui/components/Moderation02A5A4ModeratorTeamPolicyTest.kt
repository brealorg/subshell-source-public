package app.subshell.prototype.ui.components

import app.subshell.prototype.data.account.RedditCommunityInfoSection
import app.subshell.prototype.data.account.RedditCommunityInfoUiFailureKind
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityModerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A4ModeratorTeamPolicyTest {
    private val moderators = listOf(
        CommunityModerator("SecondInAlphabet", listOf("all")),
        CommunityModerator("FirstInAlphabet", listOf("posts", "mail")),
    )

    private fun loaded() = RedditCommunityInfoUiState(
        accountStableId = "account-a",
        communityName = "subshell",
        snapshot = CommunityInfoSnapshot("subshell", moderators = moderators),
        loaded = true,
    )

    @Test
    fun loadedModeratorsKeepReportedOrderAndPermissions() {
        val view = moderatorTeamProjection(loaded(), "account-a", "subshell", true)
        assertTrue(view.ready)
        assertFalse(view.loading)
        assertEquals(moderators, view.moderators)
    }

    @Test
    fun anotherAccountCannotExposeCachedModerators() {
        val view = moderatorTeamProjection(loaded(), "account-b", "subshell", true)
        assertTrue(view.moderators.isEmpty())
        assertFalse(view.ready)
    }

    @Test
    fun anotherCommunityCannotExposeCachedModerators() {
        val view = moderatorTeamProjection(loaded(), "account-a", "other", true)
        assertTrue(view.moderators.isEmpty())
        assertFalse(view.ready)
    }

    @Test
    fun mismatchedSnapshotIsNotTrustedEvenWhenStateOwnerMatches() {
        val state = loaded().copy(snapshot = CommunityInfoSnapshot("other", moderators = moderators))
        val view = moderatorTeamProjection(state, "account-a", "subshell", true)
        assertTrue(view.moderators.isEmpty())
        assertEquals(ModeratorTeamNotice.MODERATORS_UNAVAILABLE, view.notice)
    }

    @Test
    fun accessLossHidesCachedData() {
        val view = moderatorTeamProjection(loaded(), "account-a", "subshell", false)
        assertTrue(view.moderators.isEmpty())
        assertEquals(ModeratorTeamNotice.ACCESS_UNAVAILABLE, view.notice)
    }

    @Test
    fun failedModeratorSectionIsNotAnEmptySuccessfulList() {
        val state = loaded().copy(unavailableSections = setOf(RedditCommunityInfoSection.MODERATORS))
        val view = moderatorTeamProjection(state, "account-a", "subshell", true)
        assertFalse(view.ready)
        assertTrue(view.moderators.isEmpty())
        assertEquals(ModeratorTeamNotice.MODERATORS_UNAVAILABLE, view.notice)
    }

    @Test
    fun sidebarOrRuleFailureDoesNotHideSuccessfulModeratorSection() {
        val state = loaded().copy(unavailableSections = setOf(
            RedditCommunityInfoSection.SIDEBAR, RedditCommunityInfoSection.RULES,
        ))
        val view = moderatorTeamProjection(state, "account-a", "subshell", true)
        assertTrue(view.ready)
        assertEquals(moderators, view.moderators)
    }

    @Test
    fun failedRefreshKeepsMatchingCachedRowsWithExplicitNotice() {
        val view = moderatorTeamProjection(
            loaded().copy(failureKind = RedditCommunityInfoUiFailureKind.TRANSPORT),
            "account-a", "subshell", true,
        )
        assertEquals(moderators, view.moderators)
        assertEquals(ModeratorTeamNotice.REFRESH_FAILED, view.notice)
    }

    @Test
    fun successfulEmptyModeratorResponseIsRepresentedAsReady() {
        val view = moderatorTeamProjection(
            loaded().copy(snapshot = CommunityInfoSnapshot("subshell")),
            "account-a", "subshell", true,
        )
        assertTrue(view.ready)
        assertTrue(view.moderators.isEmpty())
    }

    @Test
    fun emptyPermissionDetailsDoNotImplyAllOrNoPermissions() {
        assertTrue(moderatorTeamPermissionLabels(emptyList()).isEmpty())
        assertTrue(moderatorTeamPermissionLabels(listOf(" ")).isEmpty())
        assertEquals(listOf("All permissions"), moderatorTeamPermissionLabels(listOf("all")))
    }

    @Test
    fun unknownPermissionCodesRemainVisibleAndAreNotPromotedToAll() {
        assertEquals(
            listOf("Posts", "custom_future_permission", "Modmail"),
            moderatorTeamPermissionLabels(listOf("posts", "custom_future_permission", "mail")),
        )
    }

    @Test
    fun filteringIsLocalCaseInsensitiveAndOrderPreserving() {
        assertEquals(moderators, moderatorTeamFilteredRows(moderators, "inalphabet"))
        assertEquals(listOf(moderators[1]), moderatorTeamFilteredRows(moderators, "u/FIRST"))
        assertEquals(moderators, moderatorTeamFilteredRows(moderators, ""))
        assertTrue(moderatorTeamFilteredRows(moderators, "not_in_list").isEmpty())
    }

    @Test
    fun currentAccountBadgeOnlyMatchesTheUsername() {
        assertTrue(moderatorTeamIsCurrentUser("Moderator", "u/moderator"))
        assertFalse(moderatorTeamIsCurrentUser("Moderator", "other"))
        assertFalse(moderatorTeamIsCurrentUser("Moderator", ""))
    }
}
