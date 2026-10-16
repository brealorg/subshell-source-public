package app.subshell.prototype.data.account

// SUBSHELL_MESSAGES02E2A_R3D_PERSISTED_REACTION_PRESENTATION_CACHE_V1

import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.model.MessageBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02E2AR3DReactionPresentationCachePolicyTest {
    @Test
    fun cachedTextTargetMatchesWithoutPersistingRawMessageBody() {
        val message = bubble(
            id = "\$matrix",
            body = "super secret reaction target",
            outgoing = true,
            created = 100L,
        )
        val target = requireNotNull(directChatReactionCachedTarget(message))

        assertNull(target.mediaId)
        assertNotNull(target.bodySha256)
        assertFalse(target.bodySha256!!.contains("secret"))
        assertEquals(
            "\$matrix",
            directChatCachedReactionTargetEventId(
                displayed = bubble(
                    id = "t4_display",
                    body = "super secret reaction target",
                    outgoing = true,
                    created = 106L,
                ),
                cachedTargets = listOf(target),
            ),
        )
        assertNull(
            directChatCachedReactionTargetEventId(
                displayed = bubble(
                    id = "t4_display",
                    body = "different",
                    outgoing = true,
                    created = 106L,
                ),
                cachedTargets = listOf(target),
            ),
        )
    }

    @Test
    fun presentationSnapshotStoresOnlyTargetsThatActuallyHaveKnownReactions() {
        val messages = listOf(
            bubble("\$m1", "hello", true, 100L),
            bubble("\$m2", "world", false, 101L),
        )
        val snapshot = directChatReactionPresentationSnapshot(
            messages = messages,
            reactions = listOf(
                DirectChatReaction(
                    reactionEventId = "\$r1",
                    targetEventId = "\$m2",
                    key = "g6akcitga7081.gif",
                    senderIsSelf = true,
                ),
            ),
            updatedAtEpochMs = 1_000L,
        )

        assertEquals(listOf("\$m2"), snapshot.targets.map { it.targetEventId })
        assertEquals(listOf("\$r1"), snapshot.reactions.map { it.reactionEventId })
    }

    @Test
    fun encodedSnapshotContainsHashesButNoMessageBodiesOrParticipants() {
        val snapshot = directChatReactionPresentationSnapshot(
            messages = listOf(
                bubble("\$m1", "never persist this body", true, 100L),
            ),
            reactions = listOf(
                DirectChatReaction(
                    reactionEventId = "\$r1",
                    targetEventId = "\$m1",
                    key = "foyijyyga7081.gif",
                    senderIsSelf = true,
                ),
            ),
            updatedAtEpochMs = 1_000L,
        )
        val encoded = encodeDirectChatReactionPresentationSnapshot(snapshot)

        assertFalse(encoded.contains("never persist this body"))
        assertFalse(encoded.contains("participant"))
        assertTrue(encoded.contains("body_sha256"))
        assertNotNull(
            decodeDirectChatReactionPresentationSnapshot(
                raw = encoded,
                nowEpochMs = 2_000L,
                json = SubshellNetworkJson.providerJson,
            ),
        )
        assertNull(
            decodeDirectChatReactionPresentationSnapshot(
                raw = encoded,
                nowEpochMs = 86_402_001L,
                json = SubshellNetworkJson.providerJson,
            ),
        )
    }

    @Test
    fun storageKeyDoesNotExposeAccountInboxOrParticipant() {
        val key = requireNotNull(
            directChatReactionPresentationCacheStorageKey(
                accountStableId = "account-secret",
                inboxItemId = "inbox-secret",
                participantUsername = "ParticipantSecret",
            ),
        )

        assertFalse(key.contains("account-secret"))
        assertFalse(key.contains("inbox-secret"))
        assertFalse(key.contains("participantsecret", ignoreCase = true))
        assertTrue(key.startsWith("direct_chat_reaction_presentation_"))
    }

    private fun bubble(
        id: String,
        body: String,
        outgoing: Boolean,
        created: Long,
    ) = MessageBubble(
        id = id,
        sender = if (outgoing) "self" else "peer",
        age = "now",
        body = body,
        outgoing = outgoing,
        createdEpochSeconds = created,
    )
}
