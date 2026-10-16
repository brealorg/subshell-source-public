package app.subshell.prototype.auth

import android.content.Context
import app.subshell.prototype.chat.RedditChatWebSessionTokenProvider
import app.subshell.prototype.data.account.DirectChatReactionPresentationCache
import app.subshell.prototype.data.auth.AuthCommentWriteTokenSource
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.reddit.AndroidRedditMediaUploadSource
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatLiveReader
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatReactionRelationsProbeReader
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatReactionWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatRedactionWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatStartWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditDirectChatTypingWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditMessageReplyWriter
import app.subshell.prototype.data.auth.reddit.RedditChatMatrixLiveReader
import app.subshell.prototype.data.auth.reddit.RedditChatMatrixMediaWriter
import app.subshell.prototype.data.auth.reddit.RedditChatMatrixReactionRelationsProbeReader
import app.subshell.prototype.data.auth.reddit.RedditChatMatrixReactionWriter
import app.subshell.prototype.data.auth.reddit.RedditChatMatrixRedactionWriter
import app.subshell.prototype.data.auth.reddit.RedditChatMatrixSessionCache
import app.subshell.prototype.data.auth.reddit.RedditChatMatrixStartWriter
import app.subshell.prototype.data.auth.reddit.RedditChatMatrixTypingWriter
import app.subshell.prototype.data.auth.reddit.RedditMessageReplyWriter
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.auth.reddit.RedditOAuthRuntimeSelection
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.SubshellNetworkTransport

/**
 * The Direct Chat part of the auth runtime: the parts AuthApplicationRuntime
 * carries for chat and message replies. All are null unless the inbox read
 * selection uses the same client mode as the active configuration.
 */
internal class DirectChatRuntimeParts(
    val messageReplyWriter: AuthenticatedRedditMessageReplyWriter?,
    val liveReader: AuthenticatedRedditDirectChatLiveReader?,
    val typingWriter: AuthenticatedRedditDirectChatTypingWriter?,
    val redactionWriter: AuthenticatedRedditDirectChatRedactionWriter?,
    val reactionWriter: AuthenticatedRedditDirectChatReactionWriter?,
    val reactionPresentationCache: DirectChatReactionPresentationCache?,
    val reactionRelationsProbeReader: AuthenticatedRedditDirectChatReactionRelationsProbeReader?,
    val startWriter: AuthenticatedRedditDirectChatStartWriter?,
)

/**
 * Builds the Direct Chat parts. Moved out of
 * AuthApplicationRuntimeFactory.create() verbatim; the parts share one
 * first-party token provider and one Matrix session cache, as before.
 */
internal fun directChatRuntimeParts(
    appContext: Context,
    configuration: OAuthProviderConfiguration,
    inboxReadSelection: RedditOAuthRuntimeSelection?,
    inboxReadCapability: AuthInboxReadCapability?,
    commentWriteCapability: AuthCommentWriteTokenSource?,
): DirectChatRuntimeParts {
    // SUBSHELL_MESSAGES02C7_FOREGROUND_MATRIX_DIRECT_CHAT_LIVE_READ_V1
    // Direct Chat read/write share one in-memory first-party JWT provider plus the same
    // verified Matrix identity/room cache. This keeps C6 warm-path behavior intact while the
    // foreground /sync reader can start without repeating WebView/whoami/room discovery.
    val directChatMatrixAvailable =
        inboxReadSelection != null &&
            inboxReadSelection.configuration.clientMode == configuration.clientMode
    val directChatMatrixTokenProvider = if (directChatMatrixAvailable) {
        RedditChatWebSessionTokenProvider(appContext)
    } else {
        null
    }
    val directChatMatrixSessionCache = if (directChatMatrixAvailable) {
        RedditChatMatrixSessionCache()
    } else {
        null
    }
    // SUBSHELL_MESSAGES02E2A_R3D_PERSISTED_REACTION_PRESENTATION_CACHE_V1
    val directChatReactionPresentationCache = if (directChatMatrixAvailable) {
        DirectChatReactionPresentationCache(
            preferences = appContext.getSharedPreferences(
                "subshell_preferences",
                Context.MODE_PRIVATE,
            ),
            json = SubshellNetworkJson.providerJson,
        )
    } else {
        null
    }
    val directChatLiveReader = if (directChatMatrixAvailable) {
        RedditChatMatrixLiveReader(
            capability = requireNotNull(inboxReadCapability),
            tokenProvider = requireNotNull(directChatMatrixTokenProvider),
            client = SubshellNetworkTransport.nonRetryingMediaWriteClient,
            sessionCache = requireNotNull(directChatMatrixSessionCache),
        )
    } else {
        null
    }
    val directChatTypingWriter = if (directChatMatrixAvailable) {
        RedditChatMatrixTypingWriter(
            capability = requireNotNull(inboxReadCapability),
            tokenProvider = requireNotNull(directChatMatrixTokenProvider),
            client = SubshellNetworkTransport.nonRetryingMediaWriteClient,
            sessionCache = requireNotNull(directChatMatrixSessionCache),
        )
    } else {
        null
    }
    val directChatRedactionWriter = if (directChatMatrixAvailable) {
        RedditChatMatrixRedactionWriter(
            capability = requireNotNull(inboxReadCapability),
            tokenProvider = requireNotNull(directChatMatrixTokenProvider),
            client = SubshellNetworkTransport.nonRetryingMediaWriteClient,
            sessionCache = requireNotNull(directChatMatrixSessionCache),
        )
    } else {
        null
    }
    // SUBSHELL_MESSAGES02E2A_DIRECT_CHAT_REACTION_WRITE_V1
    val directChatReactionWriter = if (directChatMatrixAvailable) {
        RedditChatMatrixReactionWriter(
            capability = requireNotNull(inboxReadCapability),
            tokenProvider = requireNotNull(directChatMatrixTokenProvider),
            client = SubshellNetworkTransport.nonRetryingMediaWriteClient,
            sessionCache = requireNotNull(directChatMatrixSessionCache),
        )
    } else {
        null
    }
    // SUBSHELL_MESSAGES02E2A_R3A_REACTION_RELATIONS_READ_PROBE_V1
    val directChatReactionRelationsProbeReader = if (directChatMatrixAvailable) {
        RedditChatMatrixReactionRelationsProbeReader(
            capability = requireNotNull(inboxReadCapability),
            tokenProvider = requireNotNull(directChatMatrixTokenProvider),
            client = SubshellNetworkTransport.nonRetryingMediaWriteClient,
            sessionCache = requireNotNull(directChatMatrixSessionCache),
        )
    } else {
        null
    }
    // SUBSHELL_MESSAGES02D5A_REAL_NEW_DIRECT_CHAT_V1
    // SUBSHELL_MESSAGES02D5C_INITIAL_IMAGE_GIF_V1
    val directChatMediaWriter = if (directChatMatrixAvailable) {
        RedditChatMatrixMediaWriter(
            tokenProvider = requireNotNull(directChatMatrixTokenProvider),
            mediaSource = AndroidRedditMediaUploadSource(appContext),
            client = SubshellNetworkTransport.nonRetryingMediaWriteClient,
            sessionCache = requireNotNull(directChatMatrixSessionCache),
        )
    } else {
        null
    }
    val directChatStartWriter = if (directChatMatrixAvailable) {
        RedditChatMatrixStartWriter(
            capability = requireNotNull(inboxReadCapability),
            tokenProvider = requireNotNull(directChatMatrixTokenProvider),
            client = SubshellNetworkTransport.nonRetryingMediaWriteClient,
            sessionCache = requireNotNull(directChatMatrixSessionCache),
            mediaWriter = requireNotNull(directChatMediaWriter),
        )
    } else {
        null
    }
    val messageReplyWriter = if (directChatMatrixAvailable) {
        RedditMessageReplyWriter(
            callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
            endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
            userAgent = requireNotNull(inboxReadSelection).userAgent,
            capability = requireNotNull(inboxReadCapability),
            mediaCapability = commentWriteCapability,
            mediaSource = AndroidRedditMediaUploadSource(appContext),
            mediaUploadCallFactory = SubshellNetworkTransport.nonRetryingMediaWriteClient,
            mediaWebSocketClient = SubshellNetworkTransport.nonRetryingMediaWriteClient,
            directChatMediaWriter = requireNotNull(directChatMediaWriter),
        )
    } else {
        null
    }

    return DirectChatRuntimeParts(
        messageReplyWriter = messageReplyWriter,
        liveReader = directChatLiveReader,
        typingWriter = directChatTypingWriter,
        redactionWriter = directChatRedactionWriter,
        reactionWriter = directChatReactionWriter,
        reactionPresentationCache = directChatReactionPresentationCache,
        reactionRelationsProbeReader = directChatReactionRelationsProbeReader,
        startWriter = directChatStartWriter,
    )
}
