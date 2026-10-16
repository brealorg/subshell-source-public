package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditModmailConversation
import app.subshell.prototype.data.account.RedditModmailListMode
import app.subshell.prototype.data.account.RedditModmailThread
import app.subshell.prototype.data.account.RedditModmailTriageAction
import app.subshell.prototype.data.account.RedditModmailUnreadCounts
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailThreadResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailUnreadCountsResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailUnreadResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailArchiveRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailArchiveResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailReadStateRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailReadStateResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailSendRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailSendResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailTriageRequest
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailTriageResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModmailWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (modmail01a1, modmail02, modmail04a1, modmail05, modmail06): opening
 * an unread conversation marks it read once (a read one is not written);
 * archiving, triage and a reply each send one request and then read the
 * thread (and the chosen queue) back from Reddit, and a lost answer is not
 * read back as if it succeeded; a mute needs an outside participant; after an
 * account switch nothing is sent, and signing in again is a fresh sign-in for
 * the active account only; checking the read state reads the thread, the
 * queue (which carries the unread counts) again.
 */
class Step3ModmailCoordinatorTest {
    private val account = "reddit:mod"
    private var active: String? = account

    private fun conversation(
        id: String,
        unread: Boolean = false,
        internal: Boolean = false,
        participant: String? = "someone",
    ) = RedditModmailConversation(
        id = id,
        communityName = "subshell",
        subject = "Subject $id",
        participantName = participant,
        preview = "preview",
        lastUpdatedEpochSeconds = 1L,
        unread = unread,
        isInternal = internal,
        messageCount = 1,
    )

    private var conversations = listOf(conversation("aaa"), conversation("bbb"))
    private val calls = mutableListOf<String>()

    private val loader = object : AuthenticatedRedditModmailLoader {
        override suspend fun load(accountStableId: String, after: String?) =
            loadByMode(accountStableId, after, RedditModmailListMode.ACTIVE)

        override suspend fun loadByMode(
            accountStableId: String,
            after: String?,
            listMode: RedditModmailListMode,
        ): AuthenticatedRedditModmailResult {
            calls += "list:$listMode"
            return AuthenticatedRedditModmailResult.Loaded(setOf("subshell"), conversations, nextCursor = null)
        }

        override suspend fun loadUnread(accountStableId: String) =
            AuthenticatedRedditModmailUnreadResult.Loaded(emptyList())

        override suspend fun loadUnreadCounts(accountStableId: String): AuthenticatedRedditModmailUnreadCountsResult =
            AuthenticatedRedditModmailUnreadCountsResult.Loaded(RedditModmailUnreadCounts())

        override suspend fun loadThread(accountStableId: String, conversationId: String): AuthenticatedRedditModmailThreadResult {
            calls += "thread:$conversationId"
            return AuthenticatedRedditModmailThreadResult.Loaded(
                RedditModmailThread(conversations.single { it.id == conversationId }, messages = emptyList()),
            )
        }
    }

    private var archiveAnswer: AuthenticatedRedditModmailArchiveResult = AuthenticatedRedditModmailArchiveResult.Applied
    private val writes = mutableListOf<String>()

    private val writer = object : AuthenticatedRedditModmailWriter {
        override suspend fun send(request: AuthenticatedRedditModmailSendRequest): AuthenticatedRedditModmailSendResult {
            writes += "send:${request.conversationId}:${request.body}"
            return AuthenticatedRedditModmailSendResult.Sent
        }

        override suspend fun setArchived(request: AuthenticatedRedditModmailArchiveRequest): AuthenticatedRedditModmailArchiveResult {
            writes += "archive:${request.conversationId}:${request.archived}"
            return archiveAnswer
        }

        override suspend fun triage(request: AuthenticatedRedditModmailTriageRequest): AuthenticatedRedditModmailTriageResult {
            writes += "triage:${request.conversationId}:${request.action}"
            return AuthenticatedRedditModmailTriageResult.Applied
        }

        override suspend fun setReadState(request: AuthenticatedRedditModmailReadStateRequest): AuthenticatedRedditModmailReadStateResult {
            writes += "read:${request.conversationId}:${request.unread}"
            conversations = conversations.map {
                if (it.id == request.conversationId) it.copy(unread = request.unread) else it
            }
            return AuthenticatedRedditModmailReadStateResult.Applied
        }
    }

    private val capability = object : AuthCommentWriteCapability {
        val begun = mutableListOf<String>()
        override suspend fun beginAuthorization(accountStableId: String): String {
            begun += accountStableId
            return "https://example.test/authorize"
        }
        override fun hasPendingAuthorization() = false
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("unused")
    }

    private suspend fun loaded(): ModmailCoordinator {
        val modmail = ModmailCoordinator(
            capability = capability,
            loader = loader,
            writer = writer,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            activeAccountStableId = { active },
        )
        modmail.load(account)
        calls.clear()
        return modmail
    }

    @Test
    fun openingAnUnreadConversationMarksItReadOnceAndAReadOneIsNotWritten() = runBlocking {
        conversations = listOf(conversation("aaa", unread = true), conversation("bbb"))
        val modmail = loaded()

        modmail.openConversation(account, "aaa")
        assertEquals("thread:aaa", calls.first())
        assertEquals(listOf("read:aaa:false"), writes)

        writes.clear()
        modmail.openConversation(account, "bbb")
        assertEquals(emptyList<String>(), writes)
    }

    @Test
    fun anArchivedConversationIsReadBackWithTheChosenQueueAndALostAnswerIsNot() = runBlocking {
        val modmail = loaded()
        modmail.selectListMode(account, RedditModmailListMode.ARCHIVED)
        modmail.openConversation(account, "aaa")
        calls.clear()

        modmail.setArchived(account, "aaa", archived = false)
        assertEquals(listOf("archive:aaa:false"), writes)
        assertEquals(listOf("thread:aaa", "list:ARCHIVED"), calls)

        writes.clear()
        calls.clear()
        archiveAnswer = AuthenticatedRedditModmailArchiveResult.Failed(
            kind = AuthenticatedRedditModmailFailureKind.TRANSPORT,
            outcomeUnknown = true,
        )
        modmail.setArchived(account, "aaa", archived = true)

        assertEquals(listOf("archive:aaa:true"), writes)
        assertEquals(emptyList<String>(), calls)
        assertTrue(modmail.uiState.value.conversationActionOutcomeUnknown)
    }

    @Test
    fun aMuteNeedsAnOutsideParticipantAndAHighlightIsReadBack() = runBlocking {
        conversations = listOf(conversation("aaa", internal = true), conversation("bbb", participant = null), conversation("ccc"))
        val modmail = loaded()

        modmail.openConversation(account, "aaa")
        modmail.applyTriage(account, "aaa", RedditModmailTriageAction.MUTE_3_DAYS)
        modmail.openConversation(account, "bbb")
        modmail.applyTriage(account, "bbb", RedditModmailTriageAction.MUTE_3_DAYS)
        assertEquals(emptyList<String>(), writes)

        modmail.openConversation(account, "ccc")
        calls.clear()
        modmail.applyTriage(account, "ccc", RedditModmailTriageAction.HIGHLIGHT)

        assertEquals(listOf("triage:ccc:HIGHLIGHT"), writes)
        assertEquals(listOf("thread:ccc", "list:ACTIVE"), calls)
    }

    @Test
    fun afterAnAccountSwitchNothingIsSentAndSignInIsOnlyForTheActiveAccount() = runBlocking {
        val modmail = loaded()
        modmail.openConversation(account, "aaa")

        active = "reddit:other"
        modmail.setArchived(account, "aaa", archived = true)
        modmail.applyTriage(account, "aaa", RedditModmailTriageAction.HIGHLIGHT)
        modmail.sendMessage(account, "aaa", body = "hello", internal = false, authorHidden = false)
        assertEquals(emptyList<String>(), writes)

        assertEquals(AuthBrowserLaunchResult.NotStarted, modmail.beginAuthorization(account))
        assertEquals(emptyList<String>(), capability.begun)

        active = account
        val ready = modmail.beginAuthorization(account) as AuthBrowserLaunchResult.Ready
        assertTrue(ready.freshAccountSession)
        assertEquals(listOf(account), capability.begun)
    }

    @Test
    fun checkingTheReadStateRereadsThreadAndQueueAndASentReplyRereadsItsThread() = runBlocking {
        val modmail = loaded()
        modmail.openConversation(account, "aaa")
        calls.clear()

        modmail.verifyReadState(account, "aaa")
        // The unread counts come with every list load (RedditModmailLoader).
        assertEquals(listOf("thread:aaa", "list:ACTIVE"), calls)

        calls.clear()
        modmail.sendMessage(account, "aaa", body = "hello", internal = false, authorHidden = false)
        assertEquals(listOf("send:aaa:hello"), writes)
        assertEquals("thread:aaa", calls.first())
    }
}
