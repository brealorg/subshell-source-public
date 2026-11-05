package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.RepositoryQueryKey
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE290_FEEDS_RESUME_WHERE_YOU_LEFT_OFF_V1: with the setting on, Home and
 * community positions are kept in a no-backup file per account and come back
 * at a cold start, within the chosen age; "When the app starts" decides
 * whether the first entry refreshes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue290ResumeFeedPositionTest {
    private val hour = 60L * 60L * 1000L
    private val now = 1_800_000_000_000L
    private val home = mainFeedScrollKey(FeedSource.HOME, FeedSort.BEST)
    private val norge = communityFeedScrollKey("Norge", CommunitySort.TOP, RedditListingTimePeriod.WEEK)

    private fun position(
        key: FeedScrollKey,
        itemKey: String = "t3_a",
        account: String = "hash-a",
        usedAt: Long = now - hour,
    ) = StoredFeedPosition(account, key, FeedScrollAnchor(itemKey, 40, 12), usedAt)

    @Test
    fun thePositionFileRoundTripsAndDropsWhatIsTooOldOrUnreadable() {
        val text = encodeFeedPositions(
            listOf(
                position(home),
                position(norge, usedAt = now - 13 * hour),
                position(youFeedScrollKey(0)),
                position(home, itemKey = "bad\tkey", account = "hash-b"),
            ),
        )

        val all = decodeFeedPositions(text, now, maxAgeMillis = null)
        assertEquals(listOf(position(home), position(norge, usedAt = now - 13 * hour)), all)
        assertEquals(
            listOf(position(home)),
            decodeFeedPositions(text, now, FeedPositionAge.TWELVE_HOURS.maxAgeMillis),
        )
        assertEquals(emptyList<StoredFeedPosition>(), decodeFeedPositions("other-v9\n" + text.lines().drop(1).joinToString("\n"), now, null))
        assertEquals(
            listOf(position(home)),
            decodeFeedPositions(text + "\nhash-a\tMAIN\tNOPE\tBEST\tALL\tt3_x\t1\t0\t1", now, null).take(1),
        )
    }

    @Test
    fun resumedPositionsBelongToTheirAccountAndOnlyOneEntryIsResumed() {
        var account = "reddit:a"
        val store = FeedScrollAnchorStore(activeAccount = { account }, clock = { now })
        store.seedResumedPositions(
            listOf(position(home, account = feedPositionAccountHash("reddit:b"))),
            FeedPositionStart.SHOW_STORED,
        )

        assertEquals(FeedScrollAnchor.Start, store.anchor(home))
        account = "reddit:b"
        assertEquals(FeedScrollAnchor("t3_a", 40, 12), store.anchor(home))
        assertEquals(FeedPositionStart.SHOW_STORED, store.resumeFor(home))
        store.consumeResume(home)
        assertNull(store.resumeFor(home))
    }

    @Test
    fun loadNewWaitsForTheSearchAndNotFoundStartsAtTheTop() {
        val store = FeedScrollAnchorStore(activeAccount = { "reddit:a" }, clock = { now })
        store.seedResumedPositions(
            listOf(position(home, account = feedPositionAccountHash("reddit:a")), position(norge, account = feedPositionAccountHash("reddit:a"))),
            FeedPositionStart.LOAD_NEW,
        )

        assertEquals("t3_a", store.awaitingFind(home))
        assertEquals(0, store.anchor(home).resolveIndex((0 until 60).map { "t3_$it" } + "t3_a"))
        store.endFind(home, keepPosition = true)
        assertNull(store.awaitingFind(home))
        assertEquals(FeedScrollAnchor("t3_a", 40, 12), store.anchor(home))
        store.endFind(norge, keepPosition = false)
        assertEquals(FeedScrollAnchor.Start, store.anchor(norge))
    }

    @Test
    fun onlyKnownAccountsHomeAndCommunityPositionsAreSavedNewestFirst() {
        var account = "reddit:a"
        var clock = now
        val store = FeedScrollAnchorStore(activeAccount = { account }, clock = { clock })
        store.seedResumedPositions(
            listOf(position(norge, account = feedPositionAccountHash("reddit:c"), usedAt = now - 5 * hour)),
            FeedPositionStart.SHOW_STORED,
        )
        repeat(FEED_POSITIONS_PER_ACCOUNT_ON_DISK + 5) { index ->
            clock = now + index
            store.update(communityFeedScrollKey("c$index", CommunitySort.HOT), FeedScrollAnchor("t3_$index", index, 0))
        }
        store.update(youFeedScrollKey(1), FeedScrollAnchor("t3_you", 3, 0))
        store.update(home, FeedScrollAnchor.Start)
        account = "reddit:gone"
        store.update(home, FeedScrollAnchor("t3_gone", 9, 0))

        val saved = store.positionsToSave(listOf("reddit:a", "reddit:c"))

        val savedA = saved.filter { it.accountHash == feedPositionAccountHash("reddit:a") }
        assertEquals(FEED_POSITIONS_PER_ACCOUNT_ON_DISK, savedA.size)
        assertEquals("t3_${FEED_POSITIONS_PER_ACCOUNT_ON_DISK + 4}", savedA.first().anchor.itemKey)
        assertTrue(savedA.all { it.key is FeedScrollKey.Community })
        assertEquals(listOf(norge), saved.filter { it.accountHash == feedPositionAccountHash("reddit:c") }.map { it.key })
        assertTrue(saved.none { it.accountHash == feedPositionAccountHash("reddit:gone") })
    }

    @Test
    fun theGateKeepsAShownStoredListAndRefreshesALoadNewOneEvenDeep() {
        val gate = FeedEntryRefreshGate()
        val homeKey = RepositoryQueryKey("issue290|a|home|best")
        val communityKey = RepositoryQueryKey("issue290|a|r/norge|top")

        assertEquals(FeedEntryRefreshDecision.KEEP_RESUMED, gate.claim(homeKey, deepInList = false, resumed = FeedPositionStart.SHOW_STORED))
        assertEquals(FeedEntryRefreshDecision.SKIP_REENTRY, gate.claim(homeKey))
        assertEquals(FeedEntryRefreshDecision.EXECUTE_RESUMED, gate.claim(communityKey, deepInList = true, resumed = FeedPositionStart.LOAD_NEW))
        assertEquals(FeedEntryRefreshDecision.SKIP_REENTRY, gate.claim(communityKey))
    }

    @Test
    fun aColdStartReadsTheFileAndTurningTheSettingOffDeletesIt() {
        val dir = Files.createTempDirectory("issue290").toFile()
        val file = File(dir, FEED_POSITION_FILE_NAME)
        val first = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialResumeFeedPosition = true,
            feedPositionFile = FeedPositionFile { file },
            clock = { now },
        )
        first.updateFeedScrollAnchor(home, itemKey = "t3_home", fallbackIndex = 30, pixelOffset = 5)
        first.saveFeedPositions()
        assertTrue(file.isFile)

        val second = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialResumeFeedPosition = true,
            initialResumeFeedPositionStart = FeedPositionStart.LOAD_NEW,
            feedPositionFile = FeedPositionFile { file },
            clock = { now + hour },
        )
        assertFalse(second.feedPositionsResumePending)
        assertEquals(FeedPositionStart.LOAD_NEW, second.feedPositionResume(home))
        assertEquals("t3_home", second.feedPositionAwaitingFind(home))

        val tooLate = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialResumeFeedPosition = true,
            initialResumeFeedPositionAge = FeedPositionAge.ONE_HOUR,
            feedPositionFile = FeedPositionFile { file },
            clock = { now + 2 * hour },
        )
        assertNull(tooLate.feedPositionResume(home))
        assertEquals(FeedScrollAnchor.Start, tooLate.feedScrollAnchor(home))

        val off = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            feedPositionFile = FeedPositionFile { file },
            clock = { now },
        )
        assertNull(off.feedPositionResume(home))
        off.updateResumeFeedPosition(true)
        off.updateResumeFeedPosition(false)
        assertFalse(file.exists())
        dir.deleteRecursively()
    }

    @Test
    fun theThreeSettingsAreBackedUpAndOffByDefault() {
        val defaults = SubshellSettings()
        assertFalse(defaults.resumeFeedPosition)
        assertEquals(FeedPositionAge.TWELVE_HOURS, defaults.resumeFeedPositionAge)
        assertEquals(FeedPositionStart.SHOW_STORED, defaults.resumeFeedPositionStart)

        val chosen = defaults.copy(
            resumeFeedPosition = true,
            resumeFeedPositionAge = FeedPositionAge.NO_LIMIT,
            resumeFeedPositionStart = FeedPositionStart.LOAD_NEW,
        )
        assertEquals(chosen, SubshellSettings.fromStorageMap(chosen.toStorageMap()))
    }
}
