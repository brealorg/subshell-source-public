package app.subshell.prototype.auth

import android.content.Context
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteTokenSource
import app.subshell.prototype.data.auth.reddit.AndroidRedditMediaUploadSource
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentDeleter
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentEditor
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentSubmitter
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerCommunitySearchLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedComposerPolicyLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedCrosspostSourceLoader
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostDeleter
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostEditor
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostFollowWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostManager
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostSaveWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedPostVoteWriter
import app.subshell.prototype.data.auth.reddit.AuthenticatedTextPostSubmitter
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditCommentDeleter
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditCommentEditor
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditCommentSubmitter
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditComposerCommunitySearchLoader
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditComposerPolicyLoader
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditCrosspostSourceLoader
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditPostDeleter
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditPostEditor
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditPostFollower
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditPostManager
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditPostSaver
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditPostVoter
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditTextPostSubmitter
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.auth.reddit.RedditOAuthRuntimeSelection
import app.subshell.prototype.data.drafts.TextPostSubmitAttemptStore
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellNetworkTransport
import okhttp3.Call

/**
 * The post and comment write part of the auth runtime: vote, save and
 * follow writers, comment/post edit and delete, the post manager, the
 * comment and text-post submitters, and the composer's crosspost source,
 * policy and community search loaders. Each is null unless its selection
 * and capability exist. Property names are the AuthApplicationRuntime
 * fields they fill.
 */
internal class PostWriteRuntimeParts(
    val postVoteWriter: AuthenticatedPostVoteWriter?,
    val postSaveWriter: AuthenticatedPostSaveWriter?,
    val postFollowWriter: AuthenticatedPostFollowWriter?,
    val commentEditor: AuthenticatedCommentEditor?,
    val commentDeleter: AuthenticatedCommentDeleter?,
    val postEditor: AuthenticatedPostEditor?,
    val postDeleter: AuthenticatedPostDeleter?,
    val postManager: AuthenticatedPostManager?,
    val commentSubmitter: AuthenticatedCommentSubmitter?,
    val textPostSubmitter: AuthenticatedTextPostSubmitter?,
    val crosspostSourceLoader: AuthenticatedCrosspostSourceLoader?,
    val composerPolicyLoader: AuthenticatedComposerPolicyLoader?,
    val composerCommunitySearchLoader: AuthenticatedComposerCommunitySearchLoader?,
)

/**
 * Builds the post and comment write parts. The constructor arguments moved
 * out of AuthApplicationRuntimeFactory.create()'s AuthApplicationRuntime(...)
 * call verbatim; parameters carry the names those expressions read.
 */
internal fun postWriteRuntimeParts(
    appContext: Context,
    textPostSubmitAttemptStore: TextPostSubmitAttemptStore,
    selected: RedditOAuthRuntimeSelection,
    commentWriteSelection: RedditOAuthRuntimeSelection?,
    commentEditSelection: RedditOAuthRuntimeSelection?,
    postVoteSelection: RedditOAuthRuntimeSelection?,
    postSaveSelection: RedditOAuthRuntimeSelection?,
    postFollowSelection: RedditOAuthRuntimeSelection?,
    callFactory: Call.Factory,
    coordinator: AuthAccountSessionCoordinator,
    textPostWriteSelection: RedditOAuthRuntimeSelection?,
    commentWriteCapability: AuthCommentWriteTokenSource?,
    commentEditCapability: AuthCommentWriteTokenSource?,
    postVoteCapability: AuthCommentWriteCapability?,
    postSaveCapability: AuthCommentWriteCapability?,
    postFollowCapability: AuthCommentWriteCapability?,
    textPostWriteCapability: AuthCommentWriteTokenSource?,
): PostWriteRuntimeParts = PostWriteRuntimeParts(
        postVoteWriter = postVoteSelection?.let { voteSelection ->
            postVoteCapability?.let { capability ->
                ConfiguredRedditPostVoter(
                    capability = capability,
                    callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = voteSelection.userAgent,
                )
            }
        },
        postSaveWriter = postSaveSelection?.let { saveSelection ->
            postSaveCapability?.let { capability ->
                ConfiguredRedditPostSaver(
                    capability = capability,
                    callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = saveSelection.userAgent,
                )
            }
        },
        postFollowWriter = postFollowSelection?.let { followSelection ->
            postFollowCapability?.let { capability ->
                ConfiguredRedditPostFollower(
                    capability = capability,
                    callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = followSelection.userAgent,
                )
            }
        },
        commentEditor = commentEditSelection?.let { editSelection ->
            commentEditCapability?.let { capability ->
                ConfiguredRedditCommentEditor(
                    capability = capability,
                    callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = editSelection.userAgent,
                )
            }
        },
        commentDeleter = commentEditSelection?.let { editSelection ->
            commentEditCapability?.let { capability ->
                ConfiguredRedditCommentDeleter(
                    capability = capability,
                    callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = editSelection.userAgent,
                )
            }
        },
        postEditor = commentEditSelection?.let { editSelection ->
            commentEditCapability?.let { capability ->
                ConfiguredRedditPostEditor(
                    capability = capability,
                    callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = editSelection.userAgent,
                )
            }
        },
        postDeleter = commentEditSelection?.let { editSelection ->
            commentEditCapability?.let { capability ->
                ConfiguredRedditPostDeleter(
                    capability = capability,
                    callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = editSelection.userAgent,
                )
            }
        },
        postManager = ConfiguredRedditPostManager(
            accountCoordinator = coordinator,
            callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
            endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
            userAgent = selected.userAgent,
        ),
        commentSubmitter = commentWriteSelection?.let { writeSelection ->
            commentWriteCapability?.let { capability ->
                ConfiguredRedditCommentSubmitter(
                    capability = capability,
                    callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = writeSelection.userAgent,
                    mediaSource = AndroidRedditMediaUploadSource(appContext),
                    mediaUploadCallFactory = SubshellNetworkTransport.nonRetryingMediaWriteClient,
                    mediaWebSocketClient = SubshellNetworkTransport.nonRetryingMediaWriteClient,
                )
            }
        },
        textPostSubmitter = textPostWriteSelection?.let { writeSelection ->
            textPostWriteCapability?.let { capability ->
                ConfiguredRedditTextPostSubmitter(
                    capability = capability,
                    callFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory,
                    endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
                    userAgent = writeSelection.userAgent,
                    mediaSource = AndroidRedditMediaUploadSource(appContext),
                    mediaUploadCallFactory = SubshellNetworkTransport.nonRetryingMediaWriteClient,
                    mediaWebSocketClient = SubshellNetworkTransport.nonRetryingMediaWriteClient,
                    onFinalSubmitDispatched = textPostSubmitAttemptStore::markDispatched,
                )
            }
        },
        crosspostSourceLoader = ConfiguredRedditCrosspostSourceLoader(
            accountCoordinator = coordinator,
            callFactory = callFactory,
            endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
            userAgent = selected.userAgent,
        ),
        composerPolicyLoader = ConfiguredRedditComposerPolicyLoader(
            accountCoordinator = coordinator,
            callFactory = callFactory,
            endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
            userAgent = selected.userAgent,
        ),
        composerCommunitySearchLoader = ConfiguredRedditComposerCommunitySearchLoader(
            accountCoordinator = coordinator,
            callFactory = callFactory,
            endpoint = NetworkEndpoint.https(RedditOAuth.apiEndpoint),
            userAgent = selected.userAgent,
        ),
)
