package app.subshell.prototype.data.auth.reddit

// SUBSHELL_MESSAGES02E2A_R3A_REACTION_RELATIONS_READ_PROBE_V1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Messages02E2AR3ARelationsProbePolicyTest {
    @Test
    fun relationsPathUsesStableMatrixAnnotationReactionRoute() {
        assertEquals(
            listOf(
                "_matrix",
                "client",
                "v1",
                "rooms",
                "!room:reddit.com",
                "relations",
                "\$message",
                "m.annotation",
                "m.reaction",
            ),
            redditChatMatrixReactionRelationsPathSegments(
                roomId = "!room:reddit.com",
                targetEventId = "\$message",
            ),
        )
    }

    @Test
    fun probeTargetsAreMatrixOnlyDistinctAndBoundedToLatestTwelve() {
        val values = (1..14).map { index -> "\$m$index" } +
            listOf("t4_legacy", "\$m14", "  \$m13  ")

        assertEquals(
            (14 downTo 3).map { index -> "\$m$index" },
            redditChatMatrixReactionRelationsProbeTargets(values),
        )
    }

    @Test
    fun projectionCountsKnownUnknownSelfPeerAndMalformedWithoutKeysInResult() {
        val body = """
            {
              "chunk": [
                {
                  "type": "m.reaction",
                  "event_id": "${'$'}r1",
                  "sender": "@self:reddit.com",
                  "content": {
                    "m.relates_to": {
                      "rel_type": "m.annotation",
                      "event_id": "${'$'}target",
                      "key": "g6akcitga7081.gif"
                    }
                  }
                },
                {
                  "type": "m.reaction",
                  "event_id": "${'$'}r2",
                  "sender": "@peer:reddit.com",
                  "content": {
                    "m.relates_to": {
                      "rel_type": "m.annotation",
                      "event_id": "${'$'}target",
                      "key": "future-reaction.gif"
                    }
                  }
                },
                {
                  "type": "m.room.message",
                  "event_id": "${'$'}wrong",
                  "sender": "@peer:reddit.com",
                  "content": {}
                }
              ]
            }
        """.trimIndent()

        val page = requireNotNull(
            redditChatMatrixReactionRelationsPage(
                body = body,
                targetEventId = "\$target",
                ownUserId = "@self:reddit.com",
            ),
        )
        assertEquals(
            listOf(2, 1, 1, 1, 1, 1),
            listOf(
                page.reactionCount,
                page.knownCatalogCount,
                page.selfCount,
                page.peerCount,
                page.unknownKeyCount,
                page.malformedCount,
            ),
        )
    }

    @Test
    fun projectionFailsClosedWithoutChunk() {
        assertNull(
            redditChatMatrixReactionRelationsPage(
                body = """{"next_batch":"token"}""",
                targetEventId = "\$target",
                ownUserId = "@self:reddit.com",
            ),
        )
    }

    @Test
    fun relationsPageProjectsKnownReactionEventsForHydration() {
        val body = """
            {
              "chunk": [
                {
                  "type": "m.reaction",
                  "event_id": "${'$'}r1",
                  "sender": "@self:reddit.com",
                  "content": {
                    "m.relates_to": {
                      "rel_type": "m.annotation",
                      "event_id": "${'$'}target",
                      "key": "g6akcitga7081.gif"
                    }
                  }
                },
                {
                  "type": "m.reaction",
                  "event_id": "${'$'}r2",
                  "sender": "@peer:reddit.com",
                  "content": {
                    "m.relates_to": {
                      "rel_type": "m.annotation",
                      "event_id": "${'$'}target",
                      "key": "future.gif"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val page = requireNotNull(
            redditChatMatrixReactionRelationsPage(
                body = body,
                targetEventId = "\$target",
                ownUserId = "@self:reddit.com",
            ),
        )
        assertEquals(2, page.reactionCount)
        assertEquals(1, page.knownCatalogCount)
        assertEquals(1, page.unknownKeyCount)
        assertEquals(1, page.reactions.size)
        assertEquals("\$r1", page.reactions.single().reactionEventId)
        assertEquals("\$target", page.reactions.single().targetEventId)
        assertEquals("g6akcitga7081.gif", page.reactions.single().key)
        assertEquals(true, page.reactions.single().senderIsSelf)
    }

    @Test
    fun relationsProbeBatchesNewestTargetsFourAtATime() {
        val newestFirst = (12 downTo 1).map { index -> "\$m$index" }

        assertEquals(
            listOf(
                listOf("\$m12", "\$m11", "\$m10", "\$m9"),
                listOf("\$m8", "\$m7", "\$m6", "\$m5"),
                listOf("\$m4", "\$m3", "\$m2", "\$m1"),
            ),
            redditChatMatrixReactionRelationsProbeBatches(newestFirst),
        )
    }
}
