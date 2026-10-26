package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.DirectChatDeleteFailureKind
import app.subshell.prototype.data.account.DirectChatLiveFailureKind
import app.subshell.prototype.data.account.DirectChatReaction
import app.subshell.prototype.data.account.DirectChatReactionFailureKind
import app.subshell.prototype.data.account.DirectChatReactionPresentationCache
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatReactionRelationsProbeReader
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatReactionResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatReactionWriter
import app.subshell.prototype.data.auth.reddit.RedditChatMatrixReactionRelationEvent
import app.subshell.prototype.data.auth.reddit.RedditChatMatrixReactionRelationsReadResult
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatLiveReader
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatLiveResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatRedactionResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatRedactionWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatTypingWriter
import app.subshell.prototype.model.MessageBubble
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Characterisation of the Direct Chat live session on AuthConnectViewModel
 * (start / stream callbacks / stop / typing / delete), written before it
 * moves to a coordinator. Current behaviour, not a specification.
 *
 * STEP3 (messages02c7, messages02e2a): the live window keeps the newest 100
 * messages; reactions go through the ViewModel one at a time, a reaction whose
 * answer is lost (stop, exception) is "may have been sent", only a live,
 * unredacted message can be reacted to, and a chat's cached reactions show at
 * once and are replaced by Reddit's answer, which ends with the session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelDirectChatLiveTest {
    private val account = "reddit:test"
    private val item = "t4_item"
    private val peer = "peer"

    @Test
    fun startStreamsMessagesIntoStateAndASecondStartForTheSameTargetIsANoOp() {
        val reader = FakeReader()
        val viewModel = viewModel(reader)

        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        var state = viewModel.inbox().directChatLiveUiState.value
        assertTrue(state.connecting)
        assertFalse(state.live)
        assertEquals(1, reader.streams)
        assertNull(reader.lastSince)

        reader.callbacks!!.connected()
        reader.callbacks!!.messages(listOf(bubble("\$m1", outgoing = true), bubble("\$m2", outgoing = false)))
        reader.callbacks!!.cursor("cur-1")
        idle()
        state = viewModel.inbox().directChatLiveUiState.value
        assertTrue(state.live)
        assertFalse(state.connecting)
        assertEquals(listOf("\$m1", "\$m2"), state.messages.map { it.id })
        assertEquals(1L, state.contentRevision)

        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        assertEquals(1, reader.streams)

        // A peer-typing signal reaches the state; an incoming message clears it.
        reader.callbacks!!.peerTyping(true)
        idle()
        assertTrue(viewModel.inbox().directChatLiveUiState.value.peerTyping)
        reader.callbacks!!.messages(listOf(bubble("\$m3", outgoing = false)))
        idle()
        assertFalse(viewModel.inbox().directChatLiveUiState.value.peerTyping)
    }

    @Test
    fun stopEndsTheSessionAndARestartResumesFromTheRecordedCursorWithTheMessagesKept() {
        val reader = FakeReader()
        val viewModel = viewModel(reader)
        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        reader.callbacks!!.connected()
        reader.callbacks!!.messages(listOf(bubble("\$m1", outgoing = true)))
        reader.callbacks!!.cursor("cur-7")
        idle()

        viewModel.inbox().stopRedditDirectChatLive(account, item)
        idle()
        var state = viewModel.inbox().directChatLiveUiState.value
        assertFalse(state.live)
        assertFalse(state.connecting)
        assertTrue(reader.lastCancelled)
        assertEquals(listOf("\$m1"), state.messages.map { it.id })

        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        assertEquals(2, reader.streams)
        assertEquals("cur-7", reader.lastSince)
        state = viewModel.inbox().directChatLiveUiState.value
        assertTrue(state.connecting)
        assertEquals(listOf("\$m1"), state.messages.map { it.id })

        // Another target starts fresh, without the cursor.
        viewModel.inbox().startRedditDirectChatLive(account, "t4_other", "someone")
        idle()
        assertEquals(3, reader.streams)
        assertNull(reader.lastSince)
        assertTrue(viewModel.inbox().directChatLiveUiState.value.messages.isEmpty())
    }

    @Test
    fun aFailedStreamReportsTheKindAndASessionFailureForgetsTheCursor() {
        val reader = FakeReader()
        val viewModel = viewModel(reader)
        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        reader.callbacks!!.connected()
        reader.callbacks!!.cursor("cur-1")
        idle()
        reader.finish(AuthenticatedRedditDirectChatLiveResult.Failed(DirectChatLiveFailureKind.SESSION))
        idle()
        val state = viewModel.inbox().directChatLiveUiState.value
        assertFalse(state.live)
        assertEquals(DirectChatLiveFailureKind.SESSION, state.failureKind)

        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        assertEquals(2, reader.streams)
        assertNull(reader.lastSince)
    }

    // STEP3 (messages02d2a): a peer-typing state never outlives the stream.
    @Test
    fun peerTypingIsClearedWhenTheStreamStopsOrFails() {
        val reader = FakeReader()
        val viewModel = viewModel(reader)
        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        reader.callbacks!!.connected()
        reader.callbacks!!.peerTyping(true)
        idle()
        assertTrue(viewModel.inbox().directChatLiveUiState.value.peerTyping)

        viewModel.inbox().stopRedditDirectChatLive(account, item)
        idle()
        assertFalse(viewModel.inbox().directChatLiveUiState.value.peerTyping)

        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        reader.callbacks!!.connected()
        reader.callbacks!!.peerTyping(true)
        idle()
        assertTrue(viewModel.inbox().directChatLiveUiState.value.peerTyping)

        reader.finish(AuthenticatedRedditDirectChatLiveResult.Failed(DirectChatLiveFailureKind.TRANSPORT))
        idle()
        assertFalse(viewModel.inbox().directChatLiveUiState.value.peerTyping)
    }

    @Test
    fun typingIsSentWhileLiveAndTheIdleTimerSendsFalse() {
        val reader = FakeReader()
        val typing = FakeTyping()
        val viewModel = viewModel(reader, typing = typing)

        // Not live yet: a typing=true update is dropped.
        viewModel.inbox().updateRedditDirectChatTyping(account, item, peer, typing = true)
        idle()
        assertEquals(emptyList<Boolean>(), typing.sent)

        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        reader.callbacks!!.connected()
        idle()
        viewModel.inbox().updateRedditDirectChatTyping(account, item, peer, typing = true)
        idle()
        assertEquals(listOf(true), typing.sent)

        // Silence for the idle window: typing=false is sent on its own.
        idleFor(2_500)
        assertEquals(listOf(true, false), typing.sent)

        // stop while typing sends false too (already false here: no extra send).
        viewModel.inbox().stopRedditDirectChatLive(account, item)
        idle()
        assertEquals(listOf(true, false), typing.sent)
    }

    @Test
    fun deleteRedactsOnlyAnOwnLiveMessageAndAppliesTheAcceptedRedaction() {
        val reader = FakeReader()
        val redaction = FakeRedaction()
        val viewModel = viewModel(reader, redaction = redaction)
        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        reader.callbacks!!.connected()
        reader.callbacks!!.messages(listOf(bubble("\$mine", outgoing = true), bubble("\$theirs", outgoing = false)))
        idle()

        viewModel.inbox().deleteRedditDirectChatMessage(account, item, peer, "\$theirs")
        idle()
        assertEquals(DirectChatDeleteFailureKind.NOT_OWNER, viewModel.inbox().directChatLiveUiState.value.deleteFailureKind)
        assertEquals(0, redaction.calls)

        viewModel.inbox().deleteRedditDirectChatMessage(account, item, peer, "\$mine")
        idle()
        var state = viewModel.inbox().directChatLiveUiState.value
        assertEquals("\$mine", state.deletingEventId)
        assertNull(state.deleteFailureKind)
        assertEquals(1, redaction.calls)
        // A second delete while one is in flight is ignored.
        viewModel.inbox().deleteRedditDirectChatMessage(account, item, peer, "\$mine")
        idle()
        assertEquals(1, redaction.calls)

        redaction.gate.complete(AuthenticatedRedditDirectChatRedactionResult.Accepted("\$redaction"))
        idle()
        state = viewModel.inbox().directChatLiveUiState.value
        assertNull(state.deletingEventId)
        assertTrue("\$mine" in state.redactedMessageIds)
        assertEquals(2L, state.contentRevision)
    }

    // STEP3 (messages02c7): the live window keeps the newest 100 messages.
    @Test
    fun theLiveWindowKeepsTheNewest100Messages() {
        val reader = FakeReader()
        val viewModel = viewModel(reader)
        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        reader.callbacks!!.connected()
        reader.callbacks!!.messages(
            (1..150).map { index -> bubble("\$m$index", outgoing = index % 2 == 0).copy(createdEpochSeconds = index.toLong()) },
        )
        idle()

        val ids = viewModel.inbox().directChatLiveUiState.value.messages.map { it.id }
        assertEquals(100, ids.size)
        assertEquals((51..150).map { "\$m$it" }, ids)
    }

    // STEP3 (messages02e2a): an accepted reaction is shown and remembered.
    @Test
    fun anAcceptedReactionIsShownAndCached() {
        val reader = FakeReader()
        val writer = FakeReactionWriter()
        val viewModel = reactionViewModel(reader, writer)
        goLive(viewModel, reader)

        viewModel.inbox().reactRedditDirectChatMessage(account, item, peer, "\$m1", reactionKey)
        idle()
        assertEquals("\$m1", viewModel.inbox().directChatLiveUiState.value.reactingEventId)
        assertEquals(listOf("\$m1" to reactionKey), writer.calls)

        writer.gate.complete(AuthenticatedRedditDirectChatReactionResult.Accepted("\$r1", "\$m1", reactionKey))
        idle()

        val state = viewModel.inbox().directChatLiveUiState.value
        assertNull(state.reactingEventId)
        assertNull(state.reactionFailureKind)
        assertEquals(listOf(DirectChatReaction("\$r1", "\$m1", reactionKey, senderIsSelf = true)), state.reactions)
        val cached = cache.load(account, item, peer)
        assertEquals(listOf("\$r1"), cached?.reactions?.map { it.reactionEventId })
    }

    // STEP3 (messages02e2a): one reaction at a time.
    @Test
    fun aSecondReactionWhileOneIsSendingIsIgnored() {
        val reader = FakeReader()
        val writer = FakeReactionWriter()
        val viewModel = reactionViewModel(reader, writer)
        goLive(viewModel, reader)

        viewModel.inbox().reactRedditDirectChatMessage(account, item, peer, "\$m1", reactionKey)
        idle()
        viewModel.inbox().reactRedditDirectChatMessage(account, item, peer, "\$m2", reactionKey)
        viewModel.inbox().reactRedditDirectChatMessage(account, item, peer, "\$m1", otherReactionKey)
        idle()

        assertEquals(1, writer.calls.size)
        assertEquals("\$m1", viewModel.inbox().directChatLiveUiState.value.reactingEventId)
    }

    // STEP3 (messages02e2a, E2A3): leaving the chat while a reaction is sent
    // ends it as "may have been sent".
    @Test
    fun stoppingDuringAReactionMakesItMayHaveBeenSent() {
        val reader = FakeReader()
        val writer = FakeReactionWriter()
        val viewModel = reactionViewModel(reader, writer)
        goLive(viewModel, reader)
        viewModel.inbox().reactRedditDirectChatMessage(account, item, peer, "\$m1", reactionKey)
        idle()

        viewModel.inbox().stopRedditDirectChatLive(account, item)
        idle()

        val state = viewModel.inbox().directChatLiveUiState.value
        assertNull(state.reactingEventId)
        assertEquals(DirectChatReactionFailureKind.OUTCOME_UNKNOWN, state.reactionFailureKind)
        assertTrue(writer.cancelled)
        assertEquals(1, writer.calls.size)
    }

    // STEP3 (messages02e2a): an exception from the writer is never retried.
    @Test
    fun aReactionThatThrowsIsMayHaveBeenSent() {
        val reader = FakeReader()
        val writer = FakeReactionWriter(throwInstead = true)
        val viewModel = reactionViewModel(reader, writer)
        goLive(viewModel, reader)

        viewModel.inbox().reactRedditDirectChatMessage(account, item, peer, "\$m1", reactionKey)
        idle()

        val state = viewModel.inbox().directChatLiveUiState.value
        assertNull(state.reactingEventId)
        assertEquals(DirectChatReactionFailureKind.OUTCOME_UNKNOWN, state.reactionFailureKind)
        assertEquals(1, writer.calls.size)
    }

    // STEP3 (messages02e2a): only a live, unredacted message can be reacted to.
    @Test
    fun anUnknownOrDeletedMessageCannotBeReactedTo() {
        val reader = FakeReader()
        val writer = FakeReactionWriter()
        val viewModel = reactionViewModel(reader, writer)
        goLive(viewModel, reader)

        viewModel.inbox().reactRedditDirectChatMessage(account, item, peer, "\$missing", reactionKey)
        idle()
        assertEquals(DirectChatReactionFailureKind.TARGET, viewModel.inbox().directChatLiveUiState.value.reactionFailureKind)

        reader.callbacks!!.redactions(listOf("\$m1"))
        idle()
        viewModel.inbox().reactRedditDirectChatMessage(account, item, peer, "\$m1", reactionKey)
        idle()

        assertEquals(DirectChatReactionFailureKind.TARGET, viewModel.inbox().directChatLiveUiState.value.reactionFailureKind)
        assertEquals(emptyList<Pair<String, String>>(), writer.calls)
    }

    // STEP3 (messages02e2a R3B-R3D): cached reactions show at once, Reddit's
    // relations replace them, and the read ends with the session.
    @Test
    fun cachedReactionsShowAtOnceAndRedditsRelationsReplaceThem() {
        cache.save(
            accountStableId = account,
            inboxItemId = item,
            participantUsername = peer,
            messages = listOf(bubble("\$m1", outgoing = true)),
            reactions = listOf(DirectChatReaction("\$cached", "\$m1", reactionKey, senderIsSelf = false)),
        )
        val reader = FakeReader()
        val probe = FakeProbe()
        val viewModel = reactionViewModel(reader, FakeReactionWriter(), probe)

        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        assertEquals(listOf("\$cached"), viewModel.inbox().directChatLiveUiState.value.reactions.map { it.reactionEventId })
        assertEquals(0, probe.calls)

        reader.callbacks!!.messages(listOf(bubble("\$m1", outgoing = true), bubble("\$m2", outgoing = false)))
        reader.callbacks!!.connected()
        idle()
        assertEquals(1, probe.calls)
        assertEquals(setOf("\$m1", "\$m2"), probe.targets.toSet())
        assertEquals(listOf("\$cached"), viewModel.inbox().directChatLiveUiState.value.reactions.map { it.reactionEventId })

        probe.onBatch!!(
            RedditChatMatrixReactionRelationsReadResult(
                authoritativeTargetEventIds = setOf("\$m1", "\$m2"),
                reactions = listOf(RedditChatMatrixReactionRelationEvent("\$fresh", "\$m2", reactionKey, senderIsSelf = true)),
            ),
        )
        idle()
        assertEquals(listOf("\$fresh"), viewModel.inbox().directChatLiveUiState.value.reactions.map { it.reactionEventId })

        viewModel.inbox().stopRedditDirectChatLive(account, item)
        idle()
        assertTrue(probe.cancelled)
    }

    private val reactionKey = "8r21ukpfa7081.gif"
    private val otherReactionKey = "ytv3x0sfa7081.png"
    private val cache = DirectChatReactionPresentationCache(
        preferences = ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("step3-direct-chat-reactions", android.content.Context.MODE_PRIVATE),
        json = SubshellNetworkJson.providerJson,
    )

    private fun goLive(viewModel: AuthConnectViewModel, reader: FakeReader) {
        viewModel.inbox().startRedditDirectChatLive(account, item, peer)
        idle()
        reader.callbacks!!.connected()
        reader.callbacks!!.messages(listOf(bubble("\$m1", outgoing = true), bubble("\$m2", outgoing = false)))
        idle()
    }

    private fun reactionViewModel(
        reader: AuthenticatedRedditDirectChatLiveReader,
        writer: AuthenticatedRedditDirectChatReactionWriter,
        probe: AuthenticatedRedditDirectChatReactionRelationsProbeReader? = null,
    ): AuthConnectViewModel =
        AuthConnectViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            unavailableAuthApplicationRuntime().copy(
                directChatLiveReader = reader,
                directChatTypingWriter = FakeTyping(),
                directChatRedactionWriter = FakeRedaction(),
                directChatReactionWriter = writer,
                directChatReactionRelationsProbeReader = probe,
                directChatReactionPresentationCache = cache,
            ),
        )

    private class FakeReactionWriter(private val throwInstead: Boolean = false) : AuthenticatedRedditDirectChatReactionWriter {
        val calls = mutableListOf<Pair<String, String>>()
        var cancelled = false
        val gate = CompletableDeferred<AuthenticatedRedditDirectChatReactionResult>()
        override suspend fun react(
            accountStableId: String,
            participantUsername: String,
            targetEventId: String,
            reactionKey: String,
        ): AuthenticatedRedditDirectChatReactionResult {
            calls += targetEventId to reactionKey
            if (throwInstead) error("lost")
            try {
                return gate.await()
            } catch (stopped: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw stopped
            }
        }
    }

    private class FakeProbe : AuthenticatedRedditDirectChatReactionRelationsProbeReader {
        var calls = 0
        var targets: List<String> = emptyList()
        var cancelled = false
        var onBatch: ((RedditChatMatrixReactionRelationsReadResult) -> Unit)? = null
        private val gate = CompletableDeferred<RedditChatMatrixReactionRelationsReadResult>()
        override suspend fun probe(
            accountStableId: String,
            participantUsername: String,
            targetEventIds: List<String>,
            onBatch: (RedditChatMatrixReactionRelationsReadResult) -> Unit,
        ): RedditChatMatrixReactionRelationsReadResult {
            calls++
            targets = targetEventIds
            this.onBatch = onBatch
            try {
                return gate.await()
            } catch (stopped: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw stopped
            }
        }
    }

    private fun viewModel(
        reader: AuthenticatedRedditDirectChatLiveReader,
        typing: AuthenticatedRedditDirectChatTypingWriter = FakeTyping(),
        redaction: AuthenticatedRedditDirectChatRedactionWriter = FakeRedaction(),
    ): AuthConnectViewModel =
        AuthConnectViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            unavailableAuthApplicationRuntime().copy(
                directChatLiveReader = reader,
                directChatTypingWriter = typing,
                directChatRedactionWriter = redaction,
            ),
        )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun idleFor(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun bubble(id: String, outgoing: Boolean) = MessageBubble(
        id = id,
        sender = if (outgoing) "me" else peer,
        age = "now",
        body = "body $id",
        outgoing = outgoing,
    )

    class Callbacks(
        val connected: () -> Unit,
        val cursor: (String) -> Unit,
        val messages: (List<MessageBubble>) -> Unit,
        val peerTyping: (Boolean) -> Unit,
        val redactions: (List<String>) -> Unit,
    )

    /** Reader whose stream stays open until the test finishes it or the session is cancelled. */
    private class FakeReader : AuthenticatedRedditDirectChatLiveReader {
        var streams = 0
        var lastSince: String? = null
        var lastCancelled = false
        var callbacks: Callbacks? = null
        private var gate = CompletableDeferred<AuthenticatedRedditDirectChatLiveResult>()

        fun finish(result: AuthenticatedRedditDirectChatLiveResult) { gate.complete(result) }

        override suspend fun stream(
            accountStableId: String,
            participantUsername: String,
            since: String?,
            onConnected: () -> Unit,
            onCursor: (String) -> Unit,
            onMessages: (List<MessageBubble>) -> Unit,
            onPeerTyping: (Boolean) -> Unit,
            onRedactions: (List<String>) -> Unit,
        ): AuthenticatedRedditDirectChatLiveResult {
            streams++
            lastSince = since
            lastCancelled = false
            callbacks = Callbacks(onConnected, onCursor, onMessages, onPeerTyping, onRedactions)
            gate = CompletableDeferred()
            try {
                return gate.await()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                lastCancelled = true
                throw cancelled
            }
        }
    }

    private class FakeTyping : AuthenticatedRedditDirectChatTypingWriter {
        val sent = mutableListOf<Boolean>()
        override suspend fun setTyping(accountStableId: String, participantUsername: String, typing: Boolean): Boolean {
            sent += typing
            return true
        }
    }

    private class FakeRedaction : AuthenticatedRedditDirectChatRedactionWriter {
        var calls = 0
        var gate = CompletableDeferred<AuthenticatedRedditDirectChatRedactionResult>()
        override suspend fun redact(accountStableId: String, participantUsername: String, targetEventId: String): AuthenticatedRedditDirectChatRedactionResult {
            calls++
            return gate.await()
        }
    }
}
