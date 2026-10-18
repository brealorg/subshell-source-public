package app.subshell.prototype.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.StoredOAuthClientSelection
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HARNESS04: characterisation of the real auth composition root. Runs
 * AuthApplicationRuntimeFactory.create(context, cipher) under Robolectric
 * with a test cipher (the Android Keystore is not available on the JVM) and
 * pins the runtime's shape — for every field, null or the implementation
 * class (enum values by name) — plus the instances fields share.
 *
 * Extractions out of create() must keep these shapes; a deliberate wiring
 * change updates them in the same commit. The client ID is a fake,
 * format-valid value; no real configuration is read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthApplicationRuntimeFactoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun nativeClientIsNotConfiguredInThisBuild() {
        // With a configured native client the unconfigured path does not exist.
        assumeFalse(BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED)
    }

    @Test
    fun withoutAClientSelectionOnlyTheUnavailableConnectFlowIsWired() {
        val runtime = AuthApplicationRuntimeFactory.create(context, IdentityCipher())

        assertEquals(unavailableShape(), shape(runtime))
    }

    @Test
    fun anUnreadableSessionStoreYieldsTheUnavailableRuntime() {
        storeCompatibilitySelection()

        val runtime = AuthApplicationRuntimeFactory.create(context, UnreadableCipher())

        assertEquals(unavailableShape(), shape(runtime))
    }

    @Test
    fun aCompatibilityClientWiresEveryCapabilityLoaderAndWriter() {
        storeCompatibilitySelection()

        val runtime = AuthApplicationRuntimeFactory.create(context, IdentityCipher())

        assertEquals(COMPATIBILITY_SHAPE, shape(runtime))
    }

    @Test
    fun profileCommentsAndAccountHistoryCommentsShareOneLoader() {
        storeCompatibilitySelection()

        val runtime = AuthApplicationRuntimeFactory.create(context, IdentityCipher())

        assertSame(runtime.redditProfileCommentsLoader, runtime.redditAccountHistoryCommentLoader)
        assertEquals(
            // OCB-102: one cache store removes an account and a deleted post.
            listOf(
                "accountCacheRemover,deletedPostCacheRemover",
                "redditAccountHistoryCommentLoader,redditProfileCommentsLoader",
            ),
            sharedInstanceGroups(runtime),
        )
    }

    // STEP3 (messages02c7, 02d5c): every Direct Chat reader and writer uses
    // the same in-memory web token provider and the same verified Matrix
    // identity/room cache, so a room the live reader verified is the room a
    // reply, reaction or first message goes to.
    @Test
    fun everyDirectChatPartSharesOneTokenProviderAndOneSessionCache() {
        storeCompatibilitySelection()

        val runtime = AuthApplicationRuntimeFactory.create(context, IdentityCipher())

        val replyWriter = requireNotNull(runtime.messageReplyWriter)
        val mediaWriter = requireNotNull(privateField(replyWriter, "directChatMediaWriter"))
        val startWriter = requireNotNull(runtime.directChatStartWriter)
        val parts = listOf(
            requireNotNull(runtime.directChatLiveReader),
            requireNotNull(runtime.directChatTypingWriter),
            requireNotNull(runtime.directChatRedactionWriter),
            requireNotNull(runtime.directChatReactionWriter),
            requireNotNull(runtime.directChatReactionRelationsProbeReader),
            startWriter,
            mediaWriter,
        )
        val tokenProvider = requireNotNull(privateField(parts.first(), "tokenProvider"))
        val sessionCache = requireNotNull(privateField(parts.first(), "sessionCache"))
        parts.forEach { part ->
            assertSame(part.javaClass.simpleName, tokenProvider, privateField(part, "tokenProvider"))
            assertSame(part.javaClass.simpleName, sessionCache, privateField(part, "sessionCache"))
        }
        // A first message's image goes through the reply's own media writer.
        assertSame(mediaWriter, privateField(startWriter, "mediaWriter"))
    }

    // STEP3 (mod01a1, mod01a2, mod01a21, modmail01a1): Modmail reads and
    // writes go through the Modmail grant, a removal through the moderation
    // grant and removal reasons through their own grant — never another
    // capability's session.
    @Test
    fun modmailModerationAndRemovalReasonsUseTheirOwnGrants() {
        storeCompatibilitySelection()

        val runtime = AuthApplicationRuntimeFactory.create(context, IdentityCipher())

        val modmailCapability = requireNotNull(runtime.modmailReadCapability)
        assertSame(modmailCapability, privateField(requireNotNull(runtime.redditModmailLoader), "capability"))
        assertSame(modmailCapability, privateField(requireNotNull(runtime.redditModmailWriter), "capability"))
        assertSame(
            requireNotNull(runtime.moderationActionCapability),
            privateField(requireNotNull(runtime.moderationActionWriter), "capability"),
        )
        assertSame(
            requireNotNull(runtime.removalReasonCapability),
            privateField(requireNotNull(runtime.removalReasonLoader), "capability"),
        )
    }

    // STEP3 (safety01a2): a confirmed hide or post report re-reads Hidden
    // through the runtime's own account-history updater (the one You → Hidden
    // reads), with the content-safety grant.
    @Test
    fun contentSafetyRefreshesHiddenThroughTheRuntimesHistoryUpdater() {
        storeCompatibilitySelection()

        val runtime = AuthApplicationRuntimeFactory.create(context, IdentityCipher())

        val writer = requireNotNull(runtime.contentSafetyWriter)
        assertSame(requireNotNull(runtime.redditAccountHistoryUpdater), privateField(writer, "hiddenHistoryUpdater"))
        assertSame(requireNotNull(runtime.contentSafetyCapability), privateField(writer, "capability"))
    }

    private fun privateField(owner: Any, name: String): Any? {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            type.declaredFields.firstOrNull { it.name == name }?.let { field ->
                field.isAccessible = true
                return field.get(owner)
            }
            type = type.superclass
        }
        error("${owner.javaClass.simpleName} has no field $name")
    }

    private fun storeCompatibilitySelection() {
        EncryptedAuthSessionStore(context, IdentityCipher()).saveOAuthClientSelection(
            StoredOAuthClientSelection(
                provider = OAuthProvider.REDDIT,
                mode = OAuthClientMode.REDREADER_COMPATIBILITY,
                clientId = "harness-client-01",
            ),
        )
    }

    private fun unavailableShape(): List<String> =
        COMPATIBILITY_SHAPE.map { entry ->
            val name = entry.substringBefore('=')
            when (name) {
                "connectFlow" -> "connectFlow=UnavailableAuthConnectFlow"
                "callbackRoutingDispatcher" -> entry
                else -> "$name=null"
            }
        }

    private fun fields(runtime: AuthApplicationRuntime) =
        runtime.javaClass.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .sortedBy { it.name }
            .onEach { it.isAccessible = true }

    private fun shape(runtime: AuthApplicationRuntime): List<String> =
        fields(runtime).map { field ->
            val value = field.get(runtime)
            field.name + "=" + when (value) {
                null -> "null"
                is Enum<*> -> value.name
                else -> value.javaClass.simpleName
            }
        }

    private fun sharedInstanceGroups(runtime: AuthApplicationRuntime): List<String> =
        fields(runtime)
            .map { it.name to it.get(runtime) }
            .filter { (_, value) -> value != null && value !is Enum<*> }
            .groupBy { (_, value) -> System.identityHashCode(value) }
            .values
            .filter { it.size > 1 }
            .map { group -> group.map { it.first }.sorted().joinToString(",") }
            .sorted()

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private class UnreadableCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = error("unreadable")
        override fun deleteKey() = Unit
    }

    private companion object {
        val COMPATIBILITY_SHAPE = listOf(
        "accountCacheRemover=RoomSubshellCacheStore",
        "accountSessionDirectory=AuthAccountSessionCoordinator",
        "authorizationOwnerResolver=AuthCapabilityAuthorizationOwnerResolver",
        // LOW11 (OCB-404): callback routing reads the store on IO.
        "callbackRoutingDispatcher=DefaultIoScheduler",
        "commentDeleteAuthorizationCapability=OwnerBoundAuthCommentWriteCapability",
        "commentDeleter=ConfiguredRedditCommentDeleter",
        "commentEditAuthorizationCapability=OwnerBoundAuthCommentWriteCapability",
        "commentEditClientMode=REDREADER_COMPATIBILITY",
        "commentEditor=ConfiguredRedditCommentEditor",
        "commentSubmitter=ConfiguredRedditCommentSubmitter",
        "commentWriteAuthorizationCapability=OwnerBoundAuthCommentWriteCapability",
        "commentWriteClientMode=REDREADER_COMPATIBILITY",
        "communityAccessService=ConfiguredRedditCommunityAccessService",
        "communityMembershipCapability=OwnerBoundAuthCustomFeedWriteCapability",
        "communityMembershipClientMode=REDREADER_COMPATIBILITY",
        "communityMembershipWriter=ConfiguredRedditCommunityMembershipWriter",
        "communityWikiReadAuthorizer=ConfiguredAuthCapabilityAuthorizer",
        "communityWikiReadClientMode=REDREADER_COMPATIBILITY",
        "composerCommunitySearchLoader=ConfiguredRedditComposerCommunitySearchLoader",
        "composerFlairAuthorizationCapability=OwnerBoundAuthCommentWriteCapability",
        "composerFlairClientMode=REDREADER_COMPATIBILITY",
        "composerPolicyLoader=ConfiguredRedditComposerPolicyLoader",
        "composerPostRequirementsAuthorizationCapability=OwnerBoundAuthCommentWriteCapability",
        "connectFlow=ConfiguredAuthConnectFlowController",
        "contentSafetyCapability=OwnerBoundAuthCommentWriteCapability",
        "contentSafetyClientMode=REDREADER_COMPATIBILITY",
        "contentSafetyWriter=ConfiguredRedditContentSafetyWriter",
        "crosspostSourceLoader=ConfiguredRedditCrosspostSourceLoader",
        "customFeedCreator=ConfiguredCustomFeedCreator",
        "customFeedReader=ConfiguredCustomFeedReader",
        "customFeedWriteCapability=OwnerBoundAuthCustomFeedWriteCapability",
        "customFeedWriteClientMode=REDREADER_COMPATIBILITY",
        "deletedPostCacheRemover=RoomSubshellCacheStore",
        "directChatLiveReader=RedditChatMatrixLiveReader",
        "directChatReactionPresentationCache=DirectChatReactionPresentationCache",
        "directChatReactionRelationsProbeReader=RedditChatMatrixReactionRelationsProbeReader",
        "directChatReactionWriter=RedditChatMatrixReactionWriter",
        "directChatRedactionWriter=RedditChatMatrixRedactionWriter",
        "directChatStartWriter=RedditChatMatrixStartWriter",
        "directChatTypingWriter=RedditChatMatrixTypingWriter",
        "inboxReadCapability=OwnerBoundAuthInboxReadCapability",
        "inboxReadClientMode=REDREADER_COMPATIBILITY",
        "inboxReadMutationWriter=RedditInboxReadMutationWriter",
        "messageReplyWriter=RedditMessageReplyWriter",
        "moderationActionCapability=OwnerBoundAuthCommentWriteCapability",
        "moderationActionClientMode=REDREADER_COMPATIBILITY",
        "moderationActionWriter=ConfiguredRedditModerationWriter",
        "moderationLogCapability=OwnerBoundAuthCommentWriteCapability",
        "moderationLogClientMode=REDREADER_COMPATIBILITY",
        "moderatorTeamCapability=OwnerBoundAuthCommentWriteCapability",
        "moderatorTeamClientMode=REDREADER_COMPATIBILITY",
        "moderatorUserManagementCapability=OwnerBoundAuthCommentWriteCapability",
        "moderatorUserManagementClientMode=REDREADER_COMPATIBILITY",
        "modmailReadCapability=OwnerBoundAuthCommentWriteCapability",
        "modmailReadClientMode=REDREADER_COMPATIBILITY",
        "onlineVisibilityService=ConfiguredRedditOnlineVisibilityService",
        "postDeleteAuthorizationCapability=OwnerBoundAuthCommentWriteCapability",
        "postDeleter=ConfiguredRedditPostDeleter",
        "postEditAuthorizationCapability=OwnerBoundAuthCommentWriteCapability",
        "postEditor=ConfiguredRedditPostEditor",
        "postFollowCapability=OwnerBoundAuthCommentWriteCapability",
        "postFollowClientMode=REDREADER_COMPATIBILITY",
        "postFollowWriter=ConfiguredRedditPostFollower",
        "postManageEditAuthorizationCapability=OwnerContextBoundAuthCommentWriteCapability",
        "postManageFlairAuthorizationCapability=OwnerContextBoundAuthCommentWriteCapability",
        "postManageModerationAuthorizationCapability=OwnerContextBoundAuthCommentWriteCapability",
        "postManager=ConfiguredRedditPostManager",
        "postModerationClientMode=REDREADER_COMPATIBILITY",
        "postSaveCapability=OwnerBoundAuthCommentWriteCapability",
        "postSaveClientMode=REDREADER_COMPATIBILITY",
        "postSaveWriter=ConfiguredRedditPostSaver",
        "postVoteCapability=OwnerBoundAuthCommentWriteCapability",
        "postVoteClientMode=REDREADER_COMPATIBILITY",
        "postVoteWriter=ConfiguredRedditPostVoter",
        "profileHistoryAuthorizer=ConfiguredAuthCapabilityAuthorizer",
        "profileHistoryCapability=ConfiguredAuthProfileHistoryCapability",
        "profileHistoryClientMode=REDREADER_COMPATIBILITY",
        "redditAccountHistoryCommentLoader=RedditProfileCommentsLoader",
        "redditAccountHistoryUpdater=RedditAccountHistoryUpdater",
        "redditApprovedUsersReader=RedditModeratorApprovedUsersReader",
        "redditBannedUsersReader=RedditModeratorBannedUsersReader",
        "redditCommentLoader=RedditCommentLoader",
        "redditCommunityDiscoveryLoader=RedditCommunityDiscoveryLoader",
        "redditCommunityInfoLoader=RedditCommunityInfoLoader",
        "redditCommunityPinnedPostsLoader=RedditCommunityPinnedPostsLoader",
        "redditCommunityWikiLoader=RedditCommunityWikiLoader",
        "redditFavoriteCapability=OwnerBoundAuthCustomFeedWriteCapability",
        "redditFavoriteClientMode=REDREADER_COMPATIBILITY",
        "redditFavoriteWriter=ConfiguredRedditFavoriteCommunityWriter",
        "redditFeedUpdater=RedditFeedUpdater",
        "redditInboxLoader=RedditInboxLoader",
        "redditModerationLoader=RedditModerationLoader",
        "redditModerationLogLoader=RedditModerationLogLoader",
        "redditModeratorInviteWriter=RedditModeratorInvitationWriter",
        "redditModeratorPermissionWriter=RedditModeratorPermissionWriter",
        "redditModeratorRemovalWriter=RedditModeratorRemovalWriter",
        "redditModeratorUserApprovalWriter=RedditModeratorApprovalWriter",
        "redditModeratorUserMuteWriter=RedditModeratorMuteWriter",
        "redditModeratorUserWriter=RedditModeratorUserWriter",
        "redditModmailLoader=RedditModmailLoader",
        "redditModmailWriter=ConfiguredRedditModmailWriter",
        "redditMutedUsersReader=RedditModeratorMutedUsersReader",
        "redditProfileCommentsLoader=RedditProfileCommentsLoader",
        "redditSavedHistoryLoader=RedditSavedHistoryLoader",
        "redditSubscriptionsLoader=RedditSubscriptionsLoader",
        "redditUserFlairManager=RedditUserFlairManager",
        "removalReasonCapability=OwnerBoundAuthCommentWriteCapability",
        "removalReasonClientMode=REDREADER_COMPATIBILITY",
        "removalReasonLoader=ConfiguredRedditRemovalReasonLoader",
        "textPostSubmitter=ConfiguredRedditTextPostSubmitter",
        "textPostWriteAuthorizationCapability=OwnerBoundAuthCommentWriteCapability",
        "textPostWriteClientMode=REDREADER_COMPATIBILITY",
        )
    }
}
