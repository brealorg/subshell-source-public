package app.subshell.prototype.auth

import kotlinx.coroutines.runBlocking
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommentDeleteRequest
import app.subshell.prototype.data.account.PostDeleteRequest
import app.subshell.prototype.data.account.PostEditRequest
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.account.RedditModeratorInviteRequest
import app.subshell.prototype.data.account.RedditModeratorPermission
import app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest
import app.subshell.prototype.data.account.RedditModeratorRemovalRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorInviteResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorInviteWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorPermissionWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorRemovalResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorRemovalWriter
import app.subshell.prototype.data.auth.communityWikiAuthorizationContextKey
import app.subshell.prototype.data.auth.profileHistoryAuthorizationContextKey
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentDeleteFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentDeleteResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentDeleter
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostDeleteFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostDeleteResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostDeleter
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditFailureKind
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditor
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwnerResolver
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationResult
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthConnectFlow
import app.subshell.prototype.data.auth.AuthContextualCapabilityAuthorizer
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapability
import app.subshell.prototype.data.auth.AuthCustomFeedWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthInboxReadCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.PendingOAuthAuthorization
import app.subshell.prototype.data.auth.StoredOAuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Characterisation of the per-owner OAuth callback routing in
 * AuthConnectViewModel (handleCallbackResultToken and
 * resumeStoredCallbackIfPresent), written before the two parallel
 * `when (authorizationOwner)` blocks are consolidated. The real encrypted
 * store and owner resolver decide the owner; every capability and
 * authorizer is a named recorder that reports "no stored callback", so a
 * routed callback shows up as that sink asking and then cancelling (or, on
 * resume, asking for a pending one). An owned callback never reaches the
 * connect flow. The owner-less and invalid-routing paths stay with
 * AuthConnectViewModelCallbackRoutingTest.
 *
 * Quiet owners: the post delete and post edit coordinators only claim a
 * callback while they hold an active authorization request (comment delete
 * claims its owner-bound record like comment edit since LOW9, OCB-407), post
 * manage and the wiki/profile reads are routed by their context key, and the
 * moderator team claims one only with an in-memory pending team request.
 * Without that they touch nothing; the armed and contextual cases below
 * show where they route. The moderator team case arms one of its three
 * coordinators at a time (opening one dismisses the others, so the VM never
 * holds two pending team requests) and shows the callback completing there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelCallbackOwnerRoutingTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())

    private val plainRoutes: Map<AuthCapabilityAuthorizationOwner, String?> = mapOf(
        AuthCapabilityAuthorizationOwner.COMMENT_WRITE to "commentWrite",
        AuthCapabilityAuthorizationOwner.COMMENT_EDIT to "commentEdit",
        AuthCapabilityAuthorizationOwner.COMMENT_DELETE to "commentDelete",
        AuthCapabilityAuthorizationOwner.POST_EDIT to null,
        AuthCapabilityAuthorizationOwner.POST_DELETE to null,
        AuthCapabilityAuthorizationOwner.POST_MANAGE to null,
        AuthCapabilityAuthorizationOwner.MODERATION_ACTION to "moderationAction",
        AuthCapabilityAuthorizationOwner.MODERATION_REASON to "removalReason",
        AuthCapabilityAuthorizationOwner.POST_VOTE to "postVote",
        AuthCapabilityAuthorizationOwner.POST_SAVE to "postSave",
        AuthCapabilityAuthorizationOwner.POST_FOLLOW to "postFollow",
        AuthCapabilityAuthorizationOwner.CONTENT_SAFETY to "contentSafety",
        AuthCapabilityAuthorizationOwner.TEXT_POST_WRITE to "textPostWrite",
        AuthCapabilityAuthorizationOwner.COMPOSER_FLAIR to "composerFlair",
        AuthCapabilityAuthorizationOwner.COMPOSER_REQUIREMENTS to "composerRequirements",
        AuthCapabilityAuthorizationOwner.COMMUNITY_MEMBERSHIP to "communityMembership",
        AuthCapabilityAuthorizationOwner.REDDIT_FAVORITE to "redditFavorite",
        AuthCapabilityAuthorizationOwner.CUSTOM_FEED_WRITE to "customFeedWrite",
        AuthCapabilityAuthorizationOwner.INBOX_READ to "inboxRead",
        AuthCapabilityAuthorizationOwner.MODMAIL_READ to "modmailRead",
        AuthCapabilityAuthorizationOwner.MODERATION_LOG to "moderationLog",
        AuthCapabilityAuthorizationOwner.MODERATION_USERS to "moderatorUserManagement",
        AuthCapabilityAuthorizationOwner.MODERATION_TEAM to null,
        AuthCapabilityAuthorizationOwner.PROFILE_HISTORY to "profileHistory",
        AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ to "communityWiki",
    )

    @Test
    fun everyOwnedCallbackReachesOnlyItsOwnSinkAndNeverTheConnectFlow() {
        assertEquals(AuthCapabilityAuthorizationOwner.entries.toSet(), plainRoutes.keys)
        for ((owner, sink) in plainRoutes) {
            // Without a context key the wiki/profile resume stays quiet.
            val resumeSink = sink.takeUnless {
                owner == AuthCapabilityAuthorizationOwner.PROFILE_HISTORY ||
                    owner == AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ
            }
            assertEquals("$owner handle", listOfNotNull(sink), route(owner, null, null, handle = true))
            assertEquals("$owner resume", listOfNotNull(resumeSink), route(owner, null, null, handle = false))
        }
    }

    @Test
    fun contextualOwnersRouteByTheirContextKey() {
        val cases = listOf(
            Triple(AuthCapabilityAuthorizationOwner.POST_MANAGE, "edit", "postManageEdit"),
            Triple(AuthCapabilityAuthorizationOwner.POST_MANAGE, "flair", "postManageFlair"),
            Triple(AuthCapabilityAuthorizationOwner.POST_MANAGE, "moderation", "postManageModeration"),
            Triple(AuthCapabilityAuthorizationOwner.COMMUNITY_WIKI_READ, communityWikiAuthorizationContextKey("kotlin"), "communityWiki"),
            Triple(AuthCapabilityAuthorizationOwner.PROFILE_HISTORY, profileHistoryAuthorizationContextKey("tester"), "profileHistory"),
        )
        for ((owner, context, sink) in cases) {
            assertEquals("$owner/$context handle", listOf(sink), route(owner, context, null, handle = true))
            assertEquals("$owner/$context resume", listOf(sink), route(owner, context, null, handle = false))
        }
    }

    @Test
    fun armedDeleteAndEditOwnersReachTheirOwnCapability() {
        val cases = listOf(
            AuthCapabilityAuthorizationOwner.COMMENT_DELETE to "commentDelete",
            AuthCapabilityAuthorizationOwner.POST_EDIT to "postEdit",
            AuthCapabilityAuthorizationOwner.POST_DELETE to "postDelete",
        )
        for ((owner, sink) in cases) {
            assertEquals("$owner handle", listOf(sink), route(owner, null, sink, handle = true))
            assertEquals("$owner resume", listOf(sink), route(owner, null, sink, handle = false))
        }
    }

    @Test
    fun armedModeratorTeamCallbackCompletesOnTheArmedCoordinatorOnly() {
        for (armed in listOf("invite", "permission", "removal")) {
            for (handle in listOf(true, false)) {
                val label = "$armed ${if (handle) "handle" else "resume"}"
                val log = mutableListOf<String>()
                store.clearAll()
                store.savePendingAuthorization(pending(AuthCapabilityAuthorizationOwner.MODERATION_TEAM.wireKey))
                val viewModel = viewModel(log, RecordingConnectFlow(log), tweak = { runtime ->
                    armedRuntime(runtime).copy(
                        moderatorTeamCapability = StoredTeamCapability(account, log),
                        moderatorTeamClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                        redditModeratorInviteWriter = object : AuthenticatedRedditModeratorInviteWriter {
                            override suspend fun invite(request: RedditModeratorInviteRequest) =
                                AuthenticatedRedditModeratorInviteResult.AuthorizationRequired
                        },
                        redditModeratorPermissionWriter = object : AuthenticatedRedditModeratorPermissionWriter {
                            override suspend fun update(request: RedditModeratorPermissionUpdateRequest) =
                                AuthenticatedRedditModeratorPermissionUpdateResult.AuthorizationRequired
                        },
                        redditModeratorRemovalWriter = object : AuthenticatedRedditModeratorRemovalWriter {
                            override suspend fun remove(request: RedditModeratorRemovalRequest) =
                                AuthenticatedRedditModeratorRemovalResult.AuthorizationRequired
                        },
                    )
                })
                idle()
                when (armed) {
                    "invite" -> {
                        viewModel.openModeratorInvite(account, "subshell")
                        viewModel.inviteModerator(
                            RedditModeratorInviteRequest(account, "subshell", "target_user", permissions = setOf(RedditModeratorPermission.POSTS)),
                        )
                    }
                    "permission" -> {
                        viewModel.openModeratorPermissionUpdate(account, "subshell", "target_user")
                        viewModel.updateModeratorPermissions(
                            RedditModeratorPermissionUpdateRequest(account, "subshell", "target_user", permissions = setOf(RedditModeratorPermission.POSTS)),
                        )
                    }
                    "removal" -> {
                        viewModel.openModeratorRemoval(account, "subshell", "target_user")
                        viewModel.removeModerator(RedditModeratorRemovalRequest(account, "subshell", "target_user"))
                    }
                }
                idle()
                assertEquals("$label armed", listOf(armed), teamAuthorizationRequired(viewModel))
                log.clear()
                if (handle) viewModel.handleCallbackResultToken("token") else viewModel.resumeStoredCallbackIfPresent()
                idle()
                assertTrue("$label reached the connect flow: $log", log.none { it.startsWith("connectFlow.") })
                assertEquals("$label completions", 1, log.count { it == "moderatorTeam.complete" })
                assertEquals("$label ready", listOf(armed), teamAuthorizationReady(viewModel))
            }
        }
    }

    private fun teamAuthorizationRequired(viewModel: AuthConnectViewModel) = listOfNotNull(
        "invite".takeIf { viewModel.moderatorInviteUiState.value.authorizationRequired },
        "permission".takeIf { viewModel.moderatorPermissionUpdateUiState.value.authorizationRequired },
        "removal".takeIf { viewModel.moderatorRemovalUiState.value.authorizationRequired },
    )

    private fun teamAuthorizationReady(viewModel: AuthConnectViewModel) = listOfNotNull(
        "invite".takeIf { viewModel.moderatorInviteUiState.value.authorizationReady },
        "permission".takeIf { viewModel.moderatorPermissionUpdateUiState.value.authorizationReady },
        "removal".takeIf { viewModel.moderatorRemovalUiState.value.authorizationReady },
    )

    /**
     * A login or reauthentication writes an owner-less pending authorization.
     * Its callback goes to the connect flow and nowhere else, even while a
     * capability authorization the user abandoned is still waiting. Before
     * AUTHCALLBACK02 the legacy owner-less chain let that abandoned
     * delete/edit request take the login's result token.
     */
    @Test
    fun ownerlessLoginCallbackWithAnAbandonedCapabilityRequest() {
        for (arm in listOf(null, "commentDelete", "postEdit", "postDelete")) {
            for (handle in listOf(true, false)) {
                val log = mutableListOf<String>()
                store.clearAll()
                val viewModel = viewModel(log, RecordingConnectFlow(log), tweak = armedRuntime)
                idle()
                arm(viewModel, arm)
                // The login replaces the abandoned owner-bound pending record.
                store.clearAll()
                store.savePendingAuthorization(pending(null))
                log.clear()
                if (handle) viewModel.handleCallbackResultToken("token") else viewModel.resumeStoredCallbackIfPresent()
                idle()
                val label = "abandoned=$arm ${if (handle) "handle" else "resume"}"
                val connect = if (handle) "connectFlow.handle" else "connectFlow.resume"
                assertEquals("$label reaches only the connect flow", listOf(connect), log)
            }
        }
    }

    /** The sinks a callback for [owner] reaches; fails if it reaches the connect flow. */
    private fun route(
        owner: AuthCapabilityAuthorizationOwner,
        context: String?,
        arm: String?,
        handle: Boolean,
    ): List<String> {
        val log = mutableListOf<String>()
        store.clearAll()
        store.savePendingAuthorization(pending(owner.wireKey, context))
        val viewModel = viewModel(log, RecordingConnectFlow(log), tweak = armedRuntime)
        idle()
        arm(viewModel, arm)
        log.clear()
        if (handle) viewModel.handleCallbackResultToken("token") else viewModel.resumeStoredCallbackIfPresent()
        idle()
        assertTrue("$owner reached the connect flow: $log", log.none { it.startsWith("connectFlow.") })
        return log.map { it.substringBefore('.') }.distinct().sorted()
    }

    /** Leaves [arm]'s coordinator waiting for its browser callback. */
    private fun arm(viewModel: AuthConnectViewModel, arm: String?) = runBlocking<Unit> {
        when (arm) {
            "commentDelete" -> {
                viewModel.deleteComment(CommentDeleteRequest(account, "t3_post", "t1_comment", "tester", RedditCommentSort.BEST))
                idle(); viewModel.beginCommentDeleteAuthorization()
            }
            "postEdit" -> {
                viewModel.editPost(PostEditRequest(account, "t3_post", "tester", "text"))
                idle(); viewModel.beginPostEditAuthorization()
            }
            "postDelete" -> {
                viewModel.deletePost(PostDeleteRequest(account, "t3_post", "tester"))
                idle(); viewModel.beginPostDeleteAuthorization()
            }
        }
        idle()
    }

    private val account = "reddit:test"

    private val armedRuntime: (AuthApplicationRuntime) -> AuthApplicationRuntime = { runtime ->
        runtime.copy(
            accountSessionDirectory = FixedDirectory(account),
            commentEditClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            commentDeleter = AuthenticatedCommentDeleter {
                AuthenticatedCommentDeleteResult.Failed(AuthenticatedCommentDeleteFailureKind.AUTHORIZATION_REQUIRED)
            },
            postEditor = AuthenticatedPostEditor {
                AuthenticatedPostEditResult.Failed(AuthenticatedPostEditFailureKind.AUTHORIZATION_REQUIRED)
            },
            postDeleter = AuthenticatedPostDeleter {
                AuthenticatedPostDeleteResult.Failed(AuthenticatedPostDeleteFailureKind.AUTHORIZATION_REQUIRED)
            },
        )
    }

    private class FixedDirectory(private val active: String) : AccountSessionDirectory {
        private val snapshot = AccountSessionDirectorySnapshot(
            accounts = listOf(
                AccountSessionSummary(
                    stableId = active,
                    username = "tester",
                    source = AccountSessionSource.AUTHENTICATED,
                    state = AccountSessionState.CONNECTED,
                    providerLabel = "Reddit",
                ),
            ),
            activeAccountId = active,
        )

        override fun snapshot() = snapshot
        override fun selectAccount(stableId: String) = snapshot
    }

    private fun viewModel(
        log: MutableList<String>,
        connect: AuthConnectFlow,
        tweak: (AuthApplicationRuntime) -> AuthApplicationRuntime = { it },
    ): AuthConnectViewModel {
        fun c(name: String) = RecordingCommentCapability(name, log)
        return AuthConnectViewModel(
            application,
            unavailableAuthApplicationRuntime().copy(
                connectFlow = connect,
                authorizationOwnerResolver = AuthCapabilityAuthorizationOwnerResolver(store),
                customFeedWriteCapability = RecordingFeedCapability("customFeedWrite", log),
                communityMembershipCapability = RecordingFeedCapability("communityMembership", log),
                redditFavoriteCapability = RecordingFeedCapability("redditFavorite", log),
                inboxReadCapability = RecordingInboxCapability("inboxRead", log),
                moderationLogCapability = c("moderationLog"),
                moderatorUserManagementCapability = c("moderatorUserManagement"),
                moderatorTeamCapability = c("moderatorTeam"),
                modmailReadCapability = c("modmailRead"),
                moderationActionCapability = c("moderationAction"),
                removalReasonCapability = c("removalReason"),
                commentEditAuthorizationCapability = c("commentEdit"),
                commentDeleteAuthorizationCapability = c("commentDelete"),
                commentWriteAuthorizationCapability = c("commentWrite"),
                textPostWriteAuthorizationCapability = c("textPostWrite"),
                postEditAuthorizationCapability = c("postEdit"),
                postDeleteAuthorizationCapability = c("postDelete"),
                composerFlairAuthorizationCapability = c("composerFlair"),
                composerPostRequirementsAuthorizationCapability = c("composerRequirements"),
                postManageEditAuthorizationCapability = c("postManageEdit"),
                postManageFlairAuthorizationCapability = c("postManageFlair"),
                postManageModerationAuthorizationCapability = c("postManageModeration"),
                postVoteCapability = c("postVote"),
                postSaveCapability = c("postSave"),
                postFollowCapability = c("postFollow"),
                contentSafetyCapability = c("contentSafety"),
                communityWikiReadAuthorizer = RecordingAuthorizer("communityWiki", log),
                profileHistoryAuthorizer = RecordingAuthorizer("profileHistory", log),
            ).let(tweak),
        )
    }

    private fun idle() = repeat(3) { shadowOf(Looper.getMainLooper()).idle() }

    private fun pending(ownerKey: String?, contextKey: String? = null) = PendingOAuthAuthorization(
        provider = OAuthProvider.REDDIT,
        state = "state",
        redirectUri = "subshell://callback",
        createdAtEpochMs = 1L,
        authorizationOwnerKey = ownerKey,
        authorizationContextKey = contextKey,
    )

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private class RecordingConnectFlow(private val log: MutableList<String>) : AuthConnectFlow {
        override val uiState: StateFlow<ConnectAccountUiState> =
            MutableStateFlow(ConnectAccountUiState.unavailable("Reddit"))
        override suspend fun beginAuthorization(): AuthBrowserLaunchResult = error("not used")
        override suspend fun beginReauthentication(accountStableId: String): AuthBrowserLaunchResult = error("not used")
        override suspend fun browserLaunchFailed() = Unit
        override suspend fun cancel() = Unit
        override suspend fun reset() = Unit
        override fun hasStoredCallback(): Boolean = false
        override suspend fun handleCallbackResultToken(resultToken: String) { log += "connectFlow.handle" }
        override suspend fun resumeStoredCallbackIfPresent() { log += "connectFlow.resume" }
    }

    private class RecordingCommentCapability(private val name: String, private val log: MutableList<String>) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String { log += "$name.begin"; return "https://auth.invalid/$name" }
        override fun hasPendingAuthorization(): Boolean { log += "$name.hasPending"; return false }
        override fun hasStoredCallback(): Boolean { log += "$name.hasStoredCallback"; return false }
        override fun cancelPendingAuthorization() { log += "$name.cancel" }
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("$name.complete")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("not used")
    }

    /** A moderator team capability holding a stored callback that upgrades [account]. */
    private class StoredTeamCapability(private val account: String, private val log: MutableList<String>) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = error("not used")
        override fun hasPendingAuthorization(): Boolean { log += "moderatorTeam.hasPending"; return true }
        override fun hasStoredCallback(): Boolean { log += "moderatorTeam.hasStoredCallback"; return true }
        override fun cancelPendingAuthorization() { log += "moderatorTeam.cancel" }
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult {
            log += "moderatorTeam.complete"
            return AuthCommentWriteCapabilityResult.Upgraded(account, "tester")
        }
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("not used")
    }

    private class RecordingFeedCapability(private val name: String, private val log: MutableList<String>) : AuthCustomFeedWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = error("not used")
        override suspend fun hasScopeGrant(accountStableId: String): Boolean = false
        override suspend fun authorizedSession(accountStableId: String): StoredOAuthSession? = null
        override fun hasPendingAuthorization(): Boolean { log += "$name.hasPending"; return false }
        override fun hasStoredCallback(): Boolean { log += "$name.hasStoredCallback"; return false }
        override fun cancelPendingAuthorization() { log += "$name.cancel" }
        override suspend fun completePendingAuthorization(): AuthCustomFeedWriteCapabilityResult = error("$name.complete")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("not used")
    }

    private class RecordingInboxCapability(private val name: String, private val log: MutableList<String>) : AuthInboxReadCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = error("not used")
        override suspend fun hasScopeGrant(accountStableId: String): Boolean = false
        override fun hasPendingAuthorization(): Boolean { log += "$name.hasPending"; return false }
        override fun hasStoredCallback(): Boolean { log += "$name.hasStoredCallback"; return false }
        override fun cancelPendingAuthorization() { log += "$name.cancel" }
        override suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult = error("$name.complete")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("not used")
    }

    private class RecordingAuthorizer(private val name: String, private val log: MutableList<String>) : AuthContextualCapabilityAuthorizer {
        override suspend fun beginAuthorization(accountStableId: String, owner: AuthCapabilityAuthorizationOwner): String = error("not used")
        override suspend fun beginAuthorization(accountStableId: String, owner: AuthCapabilityAuthorizationOwner, contextKey: String?): String = error("not used")
        override fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner): Boolean { log += "$name.hasPending"; return false }
        override fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner, contextKey: String?): Boolean { log += "$name.hasPending"; return false }
        override fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner): Boolean { log += "$name.hasStoredCallback"; return false }
        override fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner, contextKey: String?): Boolean { log += "$name.hasStoredCallback"; return false }
        override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) { log += "$name.cancel" }
        override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner, contextKey: String?) { log += "$name.cancel" }
        override suspend fun completePendingAuthorization(owner: AuthCapabilityAuthorizationOwner): AuthCapabilityAuthorizationResult = error("$name.complete")
        override suspend fun completePendingAuthorization(owner: AuthCapabilityAuthorizationOwner, contextKey: String?): AuthCapabilityAuthorizationResult = error("$name.complete")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("not used")
    }
}
