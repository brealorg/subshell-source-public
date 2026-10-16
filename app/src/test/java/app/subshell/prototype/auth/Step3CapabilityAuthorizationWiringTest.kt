package app.subshell.prototype.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredAuthAccountRecord
import app.subshell.prototype.data.auth.StoredAuthAccountRegistry
import app.subshell.prototype.data.auth.StoredOAuthClientSelection
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.auth.oauthClientIdFingerprint
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (reddit01c2, c3a, c3b, c3c2, c4): every capability the real runtime
 * builds asks Reddit for authorization as its own owner (and context, for
 * Manage post) and for exactly the scopes that feature needs. The account
 * holds only the core read scopes, so each request is the feature's scope
 * set; the expectations are literal scope names, not the constants.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3CapabilityAuthorizationWiringTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = EncryptedAuthSessionStore(context, IdentityCipher())
    private val mode = OAuthClientMode.REDREADER_COMPATIBILITY
    private val clientId = "harness-client-01"
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, mode, "t2me")

    private val core = setOf("identity", "mysubreddits", "read")
    private val write = core + "submit"
    private val textPost = write + "history"
    private val edit = textPost + "edit"

    @Before
    fun signedInWithTheCoreReadScopesOnly() {
        assumeFalse(BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED)
        store.clearAll()
        store.saveOAuthClientSelection(StoredOAuthClientSelection(OAuthProvider.REDDIT, mode, clientId))
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(
                    StoredAuthAccountRecord(
                        accountId = accountId,
                        provider = OAuthProvider.REDDIT,
                        providerAccountId = "t2me",
                        username = "me",
                        createdAtEpochMs = 1L,
                        lastUsedAtEpochMs = 1L,
                        clientMode = mode,
                    ),
                ),
                activeAccountId = accountId,
            ),
        )
        store.saveSession(
            accountId,
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("access", "refresh", "bearer", core.joinToString(" "), Long.MAX_VALUE),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = mode,
                clientIdFingerprint = oauthClientIdFingerprint(clientId),
            ),
        )
    }

    private data class Request(val owner: String?, val context: String?, val scopes: Set<String>)

    private fun requestOf(begin: suspend () -> String, cancel: () -> Unit): Request {
        val url = runBlocking { begin() }
        val pending = requireNotNull(store.pendingAuthorization()) { "no pending authorization after begin" }
        cancel()
        return Request(
            owner = pending.authorizationOwnerKey,
            context = pending.authorizationContextKey,
            scopes = url.toHttpUrl().queryParameter("scope").orEmpty()
                .split(' ', '+').filter(String::isNotBlank).toSet(),
        )
    }

    private fun AuthCommentWriteCapability.request() =
        requestOf({ beginAuthorization(accountId) }, ::cancelPendingAuthorization)

    private fun AuthInboxReadCapability.request() =
        requestOf({ beginAuthorization(accountId) }, ::cancelPendingAuthorization)

    private fun AuthCustomFeedWriteCapability.request() =
        requestOf({ beginAuthorization(accountId) }, ::cancelPendingAuthorization)

    @Test
    fun everyCapabilityAsksAsItsOwnOwnerForItsOwnScopes() {
        val runtime = AuthApplicationRuntimeFactory.create(context, IdentityCipher())
        val actual = linkedMapOf(
            "commentWrite" to runtime.commentWriteAuthorizationCapability!!.request(),
            "textPostWrite" to runtime.textPostWriteAuthorizationCapability!!.request(),
            "commentEdit" to runtime.commentEditAuthorizationCapability!!.request(),
            "commentDelete" to runtime.commentDeleteAuthorizationCapability!!.request(),
            "postEdit" to runtime.postEditAuthorizationCapability!!.request(),
            "postDelete" to runtime.postDeleteAuthorizationCapability!!.request(),
            "composerFlair" to runtime.composerFlairAuthorizationCapability!!.request(),
            "composerRequirements" to runtime.composerPostRequirementsAuthorizationCapability!!.request(),
            "postManageEdit" to runtime.postManageEditAuthorizationCapability!!.request(),
            "postManageFlair" to runtime.postManageFlairAuthorizationCapability!!.request(),
            "postManageModeration" to runtime.postManageModerationAuthorizationCapability!!.request(),
            "inboxRead" to runtime.inboxReadCapability!!.request(),
            "customFeedWrite" to runtime.customFeedWriteCapability!!.request(),
            "communityMembership" to runtime.communityMembershipCapability!!.request(),
            "redditFavorite" to runtime.redditFavoriteCapability!!.request(),
            "postVote" to runtime.postVoteCapability!!.request(),
            "postSave" to runtime.postSaveCapability!!.request(),
            "postFollow" to runtime.postFollowCapability!!.request(),
            "contentSafety" to runtime.contentSafetyCapability!!.request(),
            "modmailRead" to runtime.modmailReadCapability!!.request(),
            "moderationLog" to runtime.moderationLogCapability!!.request(),
            "moderatorUsers" to runtime.moderatorUserManagementCapability!!.request(),
            "moderatorTeam" to runtime.moderatorTeamCapability!!.request(),
            "moderationAction" to runtime.moderationActionCapability!!.request(),
            "removalReason" to runtime.removalReasonCapability!!.request(),
        )

        val expected = linkedMapOf(
            "commentWrite" to Request("comment_write", null, write),
            "textPostWrite" to Request("text_post_write", null, textPost),
            "commentEdit" to Request("comment_edit", null, edit),
            "commentDelete" to Request("comment_delete", null, edit),
            "postEdit" to Request("post_edit", null, edit),
            "postDelete" to Request("post_delete", null, edit),
            "composerFlair" to Request("composer_flair", null, write + "flair"),
            "composerRequirements" to Request("composer_requirements", null, write),
            "postManageEdit" to Request("post_manage", "edit", edit),
            "postManageFlair" to Request("post_manage", "flair", write + "flair"),
            "postManageModeration" to Request("post_manage", "moderation", core + "modposts"),
            "inboxRead" to Request("inbox_read", null, core + "privatemessages"),
            "customFeedWrite" to Request("custom_feed_write", null, core + "subscribe"),
            "communityMembership" to Request("community_membership", null, core + "subscribe"),
            "redditFavorite" to Request("reddit_favorite", null, core + "subscribe"),
            "postVote" to Request("post_vote", null, core + "vote"),
            "postSave" to Request("post_save", null, core + "save"),
            "postFollow" to Request("post_follow", null, core + "subscribe"),
            "contentSafety" to Request("content_safety", null, core + setOf("account", "history", "privatemessages", "report")),
            "modmailRead" to Request("modmail_read", null, core + "modmail"),
            "moderationLog" to Request("moderation_log", null, core + "modlog"),
            "moderatorUsers" to Request("moderation_users", null, core + "modcontributors"),
            "moderatorTeam" to Request("moderation_team", null, core + "modothers"),
            "moderationAction" to Request("moderation_action", null, core + "modposts"),
            // Reading removal reasons needs modcontributors too (MOD01A21).
            "removalReason" to Request("moderation_reason", null, core + setOf("modposts", "modcontributors")),
        )
        expected.keys.forEach { name -> assertEquals(name, expected[name], actual[name]) }
    }

    // STEP3 (wiki01a1): the community wiki asks as its own owner, per
    // community, for reading only; no capability ever asks to edit a wiki.
    @Test
    fun theWikiAsksToReadOnlyAndNothingAsksToEditAWiki() {
        val runtime = AuthApplicationRuntimeFactory.create(context, IdentityCipher())
        val wiki = runtime.communityWikiReadAuthorizer!!
        val owner = app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ

        val key = app.subshell.prototype.data.auth.communityWikiAuthorizationContextKey("kotlin")
        val request = requestOf(
            { wiki.beginAuthorization(accountId, owner, key) },
            { wiki.cancelPendingAuthorization(owner, key) },
        )

        assertEquals("community_wiki_read", request.owner)
        assertEquals(key, request.context)
        assertEquals(core + "wikiread", request.scopes)
        val editing = app.subshell.prototype.data.auth.reddit.RedditOAuthCapability.entries
            .filter { "wikiedit" in app.subshell.prototype.data.auth.reddit.RedditOAuth.scopesFor(it) }
        assertEquals(emptyList<Any>(), editing)
    }

    // STEP3 (profile01a4a): a profile's history asks as its own owner, per
    // user, for the core read scopes plus history.
    @Test
    fun profileHistoryAsksCoreReadPlusHistoryForItsUser() {
        val runtime = AuthApplicationRuntimeFactory.create(context, IdentityCipher())
        val history = runtime.profileHistoryAuthorizer!!
        val owner = app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner.PROFILE_HISTORY

        val key = app.subshell.prototype.data.auth.profileHistoryAuthorizationContextKey("me_user")
        val request = requestOf(
            { history.beginAuthorization(accountId, owner, key) },
            { history.cancelPendingAuthorization(owner, key) },
        )

        assertEquals("profile_history", request.owner)
        assertEquals(key, request.context)
        assertEquals(core + "history", request.scopes)
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
