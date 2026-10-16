package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.DirectChatReactionFailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * STEP3 (messages02e2a): a Direct Chat reaction is one Matrix annotation PUT
 * to a room the foreground chat has already verified (no room discovery, a
 * cold session sends nothing), with a key from Reddit's catalog only; a lost
 * answer is an unknown outcome and is not sent again; the logs name no event,
 * room or key. The reaction read only reads: at most the newest twelve
 * targets, newest first, four at a time, and nothing without a warm session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3MatrixReactionReadersWritersTest {
    private val fixture = MatrixTestFixture()
    private val key = "g6akcitga7081.gif"
    private val target = "\$target"

    private fun reactionWriter() = RedditChatMatrixReactionWriter(
        capability = fixture.capability,
        tokenProvider = fixture.tokenSource,
        client = fixture.client,
        ioDispatcher = Dispatchers.IO,
        sessionCache = fixture.sessionCache,
    )

    private fun probeReader() = RedditChatMatrixReactionRelationsProbeReader(
        capability = fixture.capability,
        tokenProvider = fixture.tokenSource,
        client = fixture.client,
        ioDispatcher = Dispatchers.IO,
        sessionCache = fixture.sessionCache,
    )

    private fun react(reactionKey: String = key) = runBlocking {
        reactionWriter().react(fixture.account, fixture.participant, target, reactionKey)
    }

    @Test
    fun aReactionIsOneAnnotationPutToTheVerifiedRoom() {
        fixture.warmCache()
        fixture.respond = { MatrixAnswer.Json(200, """{"event_id":"${'$'}reaction"}""") }

        val result = react()

        assertEquals(AuthenticatedRedditDirectChatReactionResult.Accepted("\$reaction", target, key), result)
        val request = fixture.requests.single()
        assertEquals("PUT", request.method)
        assertTrue(
            decode(request.path),
            decode(request.path).startsWith("/_matrix/client/v3/rooms/${fixture.roomId}/send/m.reaction/"),
        )
        val relation = Json.parseToJsonElement(requireNotNull(request.body)).jsonObject
            .getValue("m.relates_to").jsonObject
        assertEquals("m.annotation", relation.getValue("rel_type").jsonPrimitive.content)
        assertEquals(target, relation.getValue("event_id").jsonPrimitive.content)
        assertEquals(key, relation.getValue("key").jsonPrimitive.content)
        assertEquals("Bearer ${fixture.webToken}", request.authorization)
    }

    @Test
    fun aColdSessionSendsNothingAndLooksForNoRoom() {
        val result = react()

        assertTrue(result is AuthenticatedRedditDirectChatReactionResult.Failed)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun aLostReactionAnswerIsUnknownAndIsNotSentAgain() {
        fixture.warmCache()
        fixture.respond = { MatrixAnswer.Transport }

        val result = react()

        assertEquals(
            DirectChatReactionFailureKind.OUTCOME_UNKNOWN,
            (result as AuthenticatedRedditDirectChatReactionResult.Failed).kind,
        )
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun aKeyOutsideRedditsCatalogIsNeverSent() {
        fixture.warmCache()

        val result = react(reactionKey = "unknown.gif")

        assertTrue(result is AuthenticatedRedditDirectChatReactionResult.Failed)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun reactionLogsNameNoEventRoomOrKey() {
        fixture.warmCache()
        fixture.respond = { MatrixAnswer.Json(200, """{"event_id":"${'$'}reaction"}""") }
        ShadowLog.clear()
        react()
        fixture.respond = { MatrixAnswer.Json(500, "{}") }
        react()

        val logs = ShadowLog.getLogsForTag("subshell-matrix-reaction").map { it.msg }
        assertTrue(logs.isNotEmpty())
        val ids = listOf(target, "\$reaction", fixture.roomId, key, fixture.ownMatrixUserId)
        logs.forEach { line -> ids.forEach { id -> assertFalse(line, line.contains(id)) } }
    }

    @Test
    fun theReactionReadOnlyReadsTheNewestTwelveTargetsFourAtATime() {
        fixture.warmCache()
        fixture.respond = { MatrixAnswer.Json(200, """{"chunk":[]}""") }
        val targets = (1..14).map { "\$e$it" }

        val result = runBlocking { probeReader().probe(fixture.account, fixture.participant, targets) }

        assertTrue(fixture.requests.all { it.method == "GET" })
        val read = fixture.requests.map { request ->
            val path = decode(request.path)
            val prefix = "/_matrix/client/v1/rooms/${fixture.roomId}/relations/"
            assertTrue(path, path.startsWith(prefix) && path.endsWith("/m.annotation/m.reaction"))
            path.removePrefix(prefix).removeSuffix("/m.annotation/m.reaction")
        }
        assertEquals((3..14).map { "\$e$it" }.toSet(), read.toSet())
        assertEquals(12, read.size)
        // Newest first: the first batch of four is the four newest targets.
        assertEquals((11..14).map { "\$e$it" }.toSet(), read.take(4).toSet())
        assertTrue(fixture.maxConcurrentRequests.get() <= 4)
        assertEquals((3..14).map { "\$e$it" }.toSet(), result.authoritativeTargetEventIds)
    }

    @Test
    fun theReactionReadAsksNothingWithoutAWarmSession() {
        val result = runBlocking { probeReader().probe(fixture.account, fixture.participant, listOf("\$e1")) }

        assertTrue(fixture.requests.isEmpty())
        assertTrue(result.authoritativeTargetEventIds.isEmpty())
    }

    private fun decode(path: String): String = java.net.URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
}
