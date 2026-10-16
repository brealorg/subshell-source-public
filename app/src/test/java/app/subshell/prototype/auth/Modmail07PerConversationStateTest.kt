package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.RedditModmailThread
import app.subshell.prototype.data.account.RedditModmailUnreadCounts
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailThreadResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailUnreadCountsResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailUnreadResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailReadStateRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailReadStateResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailSendRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailSendResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MODMAIL07 (#126, #127, #128): on two-pane the conversation list stays
 * tappable, so a reply or the automatic mark-read can finish after the reader
 * opened another conversation. Its in-progress flag used to stay set (the new
 * conversation's Send, archive and triage did nothing), and an unknown send
 * outcome blocked every conversation and was never cleared by the refresh the
 * composer asks for.
 */
class Modmail07PerConversationStateTest {
    private val account = "reddit:mod"

    private fun conversation(id: String, unread: Boolean = false) = RedditModmailConversation(
        id = id,
        communityName = "test",
        subject = "Subject $id",
        participantName = null,
        preview = "preview",
        lastUpdatedEpochSeconds = 1L,
        unread = unread,
        isInternal = false,
        messageCount = 1,
    )

    private var conversations = listOf(conversation("aaa"), conversation("bbb"))
    private var send = CompletableDeferred<AuthenticatedRedditModmailSendResult>()
    private var readState = CompletableDeferred<AuthenticatedRedditModmailReadStateResult>()

    private val loader = object : AuthenticatedRedditModmailLoader {
        override suspend fun load(accountStableId: String, after: String?) =
            AuthenticatedRedditModmailResult.Loaded(setOf("test"), conversations, nextCursor = null)
        override suspend fun loadUnread(accountStableId: String) =
            AuthenticatedRedditModmailUnreadResult.Loaded(emptyList())
        override suspend fun loadUnreadCounts(accountStableId: String) =
            AuthenticatedRedditModmailUnreadCountsResult.Loaded(RedditModmailUnreadCounts())
        override suspend fun loadThread(accountStableId: String, conversationId: String) =
            AuthenticatedRedditModmailThreadResult.Loaded(
                RedditModmailThread(conversations.single { it.id == conversationId }, messages = emptyList()),
            )
    }

    private val writer = object : AuthenticatedRedditModmailWriter {
        override suspend fun send(request: AuthenticatedRedditModmailSendRequest) = send.await()
        override suspend fun setReadState(request: AuthenticatedRedditModmailReadStateRequest) =
            readState.await().also {
                // Reddit now lists the conversation with the new read state.
                conversations = conversations.map { conversation ->
                    if (conversation.id == request.conversationId) conversation.copy(unread = request.unread) else conversation
                }
            }
    }

    private suspend fun loadedCoordinator(): ModmailCoordinator {
        val modmail = ModmailCoordinator(
            capability = null,
            loader = loader,
            writer = writer,
            clientMode = null,
            activeAccountStableId = { account },
        )
        modmail.load(account)
        return modmail
    }

    private fun CoroutineScope.reply(modmail: ModmailCoordinator, conversationId: String) = launch(
        Dispatchers.Unconfined,
        start = CoroutineStart.UNDISPATCHED,
    ) {
        modmail.sendMessage(account, conversationId, body = "hello", internal = false, authorHidden = false)
    }

    @Test
    fun aReplyThatFinishesAfterAnotherConversationOpenedReleasesSend() = runBlocking {
        val modmail = loadedCoordinator()
        modmail.openConversation(account, "aaa")
        reply(modmail, "aaa")
        assertTrue(modmail.uiState.value.sendInProgress)

        modmail.openConversation(account, "bbb")
        send.complete(AuthenticatedRedditModmailSendResult.Sent)

        assertEquals("bbb", modmail.uiState.value.selectedConversationId)
        assertFalse(modmail.uiState.value.sendInProgress)
    }

    @Test
    fun aMarkReadThatFinishesAfterAnotherConversationOpenedIsReleasedAndListed() = runBlocking {
        conversations = listOf(conversation("aaa", unread = true), conversation("bbb"))
        val modmail = loadedCoordinator()
        launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            modmail.openConversation(account, "aaa") // starts the automatic mark-read
        }
        assertTrue(modmail.uiState.value.readStateMutationInProgress)

        modmail.openConversation(account, "bbb")
        readState.complete(AuthenticatedRedditModmailReadStateResult.Applied)

        assertFalse(modmail.uiState.value.readStateMutationInProgress)
        assertFalse(modmail.uiState.value.conversations.single { it.id == "aaa" }.unread)
    }

    @Test
    fun anUnknownSendOutcomeStaysWithItsConversationAndARefreshClearsIt() = runBlocking {
        val modmail = loadedCoordinator()
        modmail.openConversation(account, "aaa")
        send.complete(
            AuthenticatedRedditModmailSendResult.Failed(
                kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                outcomeUnknown = true,
            ),
        )
        reply(modmail, "aaa")
        assertTrue(modmail.uiState.value.sendOutcomeUnknown)

        modmail.openConversation(account, "bbb")
        assertFalse(modmail.uiState.value.sendOutcomeUnknown)

        modmail.openConversation(account, "aaa") // the refresh the composer asks for
        assertFalse(modmail.uiState.value.sendOutcomeUnknown)
    }

    @Test
    fun theRefreshOfTheSameThreadClearsTheUnknownOutcome() = runBlocking {
        val modmail = loadedCoordinator()
        modmail.openConversation(account, "aaa")
        send.complete(
            AuthenticatedRedditModmailSendResult.Failed(
                kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
                outcomeUnknown = true,
            ),
        )
        reply(modmail, "aaa")

        modmail.openConversation(account, "aaa")

        assertFalse(modmail.uiState.value.sendOutcomeUnknown)
    }
}
