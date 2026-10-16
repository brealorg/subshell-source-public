package app.subshell.prototype.data.account

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Review data/account (reports/2026-09-28-review-account.md) #1, #3, #4:
 * ACCOUNT01_MATRIX_MEDIA_ID_V1, ACCOUNT01_REACTION_CACHE_MERGE_V1 and
 * ACCOUNT01_REACTION_CACHE_ACCOUNT_SCOPED_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Account01ChatMediaAndReactionCacheTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val preferences = application.getSharedPreferences("account01-test", Context.MODE_PRIVATE)
    private var now = 1_000_000_000L
    private val cache = DirectChatReactionPresentationCache(
        preferences = preferences,
        json = SubshellNetworkJson.providerJson,
        currentTimeMillis = { now },
    )

    @Before
    fun clear() {
        preferences.edit().clear().commit()
    }

    // #1 -----------------------------------------------------------------------

    @Test
    fun aMediaIdIsNotCutAtItsFirstS() {
        assertEquals("ab1sxyz", redditMatrixMediaIdFromMessageBody("mxc://reddit.com/ab1sxyz"))
        assertEquals("s0me1d", redditMatrixMediaIdFromMessageBody("mxc://reddit.com/s0me1d"))
        assertEquals(
            "ab1s999",
            redditMatrixMediaIdFromMessageBody(
                "https://matrix.redditspace.com/_matrix/media/v3/download/reddit.com/ab1s999?allow_redirect=true",
            ),
        )
        // Whitespace ends the ID, as intended.
        assertEquals("abc", redditMatrixMediaIdFromMessageBody("mxc://reddit.com/abc more text"))
        assertNotEquals(
            redditMatrixMediaIdFromMessageBody("mxc://reddit.com/ab1sxyz"),
            redditMatrixMediaIdFromMessageBody("mxc://reddit.com/ab1s999"),
        )
    }

    // #3 -----------------------------------------------------------------------

    private fun bubble(id: String, body: String) = MessageBubble(
        id = id,
        sender = "alice",
        age = "1m",
        body = body,
        outgoing = false,
        createdEpochSeconds = 100L,
    )

    private fun reaction(id: String, target: String) = DirectChatReaction(
        reactionEventId = id,
        targetEventId = target,
        key = REACTION_KEY,
        senderIsSelf = false,
    )

    private fun save(messages: List<MessageBubble>, reactions: List<DirectChatReaction>, account: String = "reddit:me") =
        cache.save(account, "inbox-1", "alice", messages, reactions)

    private fun load(account: String = "reddit:me") = cache.load(account, "inbox-1", "alice")

    @Test
    fun aSaveFromTheRecentWindowKeepsCachedReactionsOnOlderMessages() {
        val old = bubble("\$old", "older message")
        val recent = bubble("\$recent", "recent message")
        save(listOf(old, recent), listOf(reaction("\$r1", "\$old"), reaction("\$r2", "\$recent")))

        // Reopened: only the recent window is loaded, then a reaction is written.
        now += 1_000
        save(listOf(recent), listOf(reaction("\$r2", "\$recent"), reaction("\$r3", "\$recent")))

        val snapshot = load()!!
        assertEquals(setOf("\$old", "\$recent"), snapshot.targets.map { it.targetEventId }.toSet())
        assertEquals(setOf("\$r1", "\$r2", "\$r3"), snapshot.reactions.map { it.reactionEventId }.toSet())
    }

    @Test
    fun theLiveWindowWinsForTheMessagesItHolds() {
        val recent = bubble("\$recent", "recent message")
        save(listOf(recent), listOf(reaction("\$r1", "\$recent")))

        // The reaction was removed while the message is on screen.
        now += 1_000
        save(listOf(recent, bubble("\$other", "other")), listOf(reaction("\$r9", "\$other")))

        val snapshot = load()!!
        assertEquals(listOf("\$r9"), snapshot.reactions.map { it.reactionEventId })
    }

    // #4 -----------------------------------------------------------------------

    @Test
    fun removingAnAccountDropsOnlyItsEntries() {
        val message = bubble("\$m", "hello")
        save(listOf(message), listOf(reaction("\$r", "\$m")), account = "reddit:me")
        save(listOf(message), listOf(reaction("\$r", "\$m")), account = "reddit:other")

        cache.removeAccount("reddit:me")

        assertNull(load("reddit:me"))
        assertTrue(load("reddit:other") != null)
    }

    @Test
    fun expiredEntriesAndKeysWithoutAnAccountSegmentArePrunedOnSave() {
        val message = bubble("\$m", "hello")
        save(listOf(message), listOf(reaction("\$r", "\$m")), account = "reddit:old")
        val legacyKey = "direct_chat_reaction_presentation_" + "a".repeat(64)
        preferences.edit().putString(legacyKey, "{}").putString("unrelated_setting", "kept").commit()

        now += 86_400_001L
        save(listOf(message), listOf(reaction("\$r", "\$m")), account = "reddit:me")

        val keys = preferences.all.keys
        assertFalse(keys.contains(legacyKey))
        assertTrue(keys.contains("unrelated_setting"))
        assertEquals(
            1,
            keys.count { key -> key.startsWith("direct_chat_reaction_presentation_") },
        )
        assertTrue(load("reddit:me") != null)
    }

    @Test
    fun theKeyNamesNeitherAccountNorChatNorParticipant() {
        val key = directChatReactionPresentationCacheStorageKey("account-secret", "inbox-secret", "ParticipantSecret")!!
        assertTrue(key.startsWith(directChatReactionPresentationCacheAccountPrefix("account-secret")!!))
        assertFalse(key.contains("secret", ignoreCase = true))
    }

    private companion object {
        const val REACTION_KEY = "g6akcitga7081.gif"
    }
}
