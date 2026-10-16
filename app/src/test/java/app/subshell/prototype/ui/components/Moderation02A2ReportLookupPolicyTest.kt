package app.subshell.prototype.ui.components

import app.subshell.prototype.data.account.ModeratorReportTarget
import app.subshell.prototype.data.account.ModeratorReportsUiState
import app.subshell.prototype.data.account.RedditModerationUiFailureKind
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import org.junit.Assert.*
import org.junit.Test

class Moderation02A2ReportLookupPolicyTest {
    private val owner = "moderator-account"
    private fun row(id: String, community: String = "subshell") = RedditModeratorQueueItem(
        fullname = id,
        kind = if (id.startsWith("t3_")) RedditModeratorQueueKind.POST else RedditModeratorQueueKind.COMMENT,
        communityName = community,
        author = "test-author",
        summary = "Test item",
    )
    private fun queue(vararg items: RedditModeratorQueueItem) = RedditModerationUiState(
        accountStableId = owner,
        moderatedCommunities = setOf("subshell"),
        directoryLoaded = true,
        queueCommunityName = "mod",
        queueLoaded = true,
        queueItems = items.toList(),
    )
    private fun reports(vararg targets: ModeratorReportTarget) = ModeratorReportsUiState(
        accountStableId = owner,
        recentTargets = targets.toList(),
    )

    @Test fun firstLookupShowsLoadedPostsAndCommentsWithoutAnyPreviousVisit() {
        val rows = listOf(row("t3_test"), row("t1_first"), row("t1_second"))
        val history = reports()
        val result = moderatorReportLookupContent(owner, queue(*rows.toTypedArray()), history)
        assertEquals(rows, result.queueItems)
        assertTrue(result.recentTargets.isEmpty())
        assertTrue(history.recentTargets.isEmpty())
        assertNull(history.item)
        assertNull(history.target)
    }
    @Test fun queueRowsNeverSeedHistoryOrTheAuthoritativeReportItem() {
        val history = reports(ModeratorReportTarget("t3_old", "subshell"))
        val before = history.copy()
        repeat(3) { moderatorReportLookupContent(owner, queue(row("t3_current")), history) }
        assertEquals(before, history)
    }
    @Test fun queueAndRecentSectionsDoNotDuplicateTheSameItem() {
        val recent = ModeratorReportTarget("t3_same", "subshell")
        val other = ModeratorReportTarget("t1_else", "subshell")
        val result = moderatorReportLookupContent(owner, queue(row("t3_same")), reports(recent, other, other))
        assertEquals(listOf("t3_same"), result.queueItems.map { it.fullname })
        assertEquals(listOf(other), result.recentTargets)
    }
    @Test fun targetRemovedFromQueueRemainsReopenableThroughSessionHistory() {
        val target = ModeratorReportTarget("t3_gone", "subshell")
        val result = moderatorReportLookupContent(owner, queue(), reports(target))
        assertTrue(result.queueItems.isEmpty())
        assertEquals(listOf(target), result.recentTargets)
        assertNotNull(result.queueMessage)
    }
    @Test fun anotherAccountCannotSeeOldQueueOrRecentChoices() {
        val history = reports(ModeratorReportTarget("t1_private", "subshell"))
        val result = moderatorReportLookupContent("other-account", queue(row("t3_private")), history)
        assertTrue(result.queueItems.isEmpty())
        assertTrue(result.recentTargets.isEmpty())
        assertTrue(result.queueMessage.orEmpty().contains("this account"))
    }
    @Test fun recentHistoryOwnerIsCheckedIndependentlyOfQueueOwner() {
        val foreign = reports(ModeratorReportTarget("t1_private", "subshell")).copy(accountStableId = "other")
        val result = moderatorReportLookupContent(owner, queue(row("t3_public")), foreign)
        assertEquals(1, result.queueItems.size)
        assertTrue(result.recentTargets.isEmpty())
    }
    @Test fun onlyModeratedCommunitiesAppearInEitherSection() {
        val result = moderatorReportLookupContent(
            owner,
            queue(row("t3_allowed"), row("t1_denied", "elsewhere")),
            reports(ModeratorReportTarget("t3_denied", "elsewhere"), ModeratorReportTarget("t1_unknown")),
        )
        assertEquals(listOf("t3_allowed"), result.queueItems.map { it.fullname })
        assertTrue(result.recentTargets.isEmpty())
    }
    @Test fun communitySpecificQueueRejectsRowsFromAnotherQueueContext() {
        val state = queue(row("t3_one"), row("t1_two", "othermod"))
            .copy(moderatedCommunities = setOf("subshell", "othermod"), queueCommunityName = "subshell")
        val result = moderatorReportLookupContent(owner, state, reports())
        assertEquals(listOf("t3_one"), result.queueItems.map { it.fullname })
    }
    @Test fun unloadedQueueIsNotPresentedAsNoReportsOrAsKnownContent() {
        val state = queue(row("t3_old")).copy(queueLoaded = false)
        val result = moderatorReportLookupContent(owner, state, reports())
        assertTrue(result.queueItems.isEmpty())
        assertTrue(result.queueMessage.orEmpty().contains("No queue has been loaded"))
        assertFalse(result.queueMessage.orEmpty().contains("No reports"))
    }
    @Test fun loadingAndFailedStatesAreNotPresentedAsAnEmptySuccessfulQueue() {
        val loading = moderatorReportLookupContent(owner, queue().copy(queueLoading = true), reports())
        val failed = moderatorReportLookupContent(
            owner, queue(row("t3_old")).copy(queueFailureKind = RedditModerationUiFailureKind.HTTP), reports(),
        )
        assertTrue(loading.queueMessage.orEmpty().contains("Loading"))
        assertTrue(failed.queueMessage.orEmpty().contains("last successful load"))
        assertEquals(1, failed.queueItems.size)
    }
    @Test fun loadedPageWithCursorIsNotCalledTheCompleteQueue() {
        val result = moderatorReportLookupContent(owner, queue(row("t3_one")).copy(queueNextCursor = "t3_next"), reports())
        assertTrue(result.queueMessage.orEmpty().contains("loaded items only"))
    }
    @Test fun duplicateOrMismatchedAndOversizedTargetRowsAreRejected() {
        val good = row("t3_good")
        val wrongKind = row("t3_mismatch").copy(kind = RedditModeratorQueueKind.COMMENT)
        val tooLong = row("t3_" + "x".repeat(33))
        val result = moderatorReportLookupContent(owner, queue(good, good, wrongKind, tooLong), reports())
        assertEquals(listOf(good), result.queueItems)
    }
    @Test fun reportQuantityHasSingularAndPluralForms() {
        assertEquals("0 reports", moderatorReportQuantity(0))
        assertEquals("1 report", moderatorReportQuantity(1))
        assertEquals("2 reports", moderatorReportQuantity(2))
        assertEquals("100 reports", moderatorReportQuantity(100))
    }
}
