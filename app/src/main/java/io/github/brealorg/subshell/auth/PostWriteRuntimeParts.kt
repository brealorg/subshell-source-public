package io.github.brealorg.subshell.auth

import android.content.Context
import io.github.brealorg.subshell.data.auth.AuthAccountSessionCoordinator
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapability
import io.github.brealorg.subshell.data.auth.AuthCommentWriteTokenSource
import io.github.brealorg.subshell.data.auth.reddit.AndroidRedditMediaUploadSource
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedCommentDeleter
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedCommentEditor
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedCommentSubmitter
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedComposerCommunitySearchLoader
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedComposerPolicyLoader
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedCrosspostSourceLoader
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostDeleter
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostEditor
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostFollowWriter
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostManager
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostSaveWriter
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedPostVoteWriter
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedTextPostSubmitter
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditCommentDeleter
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditCommentEditor
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditCommentSubmitter
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditComposerCommunitySearchLoader
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditComposerPolicyLoader
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditCrosspostSourceLoader
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditPostDeleter
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditPostEditor
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditPostFollower
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditPostManager
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditPostSaver
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditPostVoter
import io.github.brealorg.subshell.data.auth.reddit.ConfiguredRedditTextPostSubmitter
import io.github.brealorg.subshell.data.auth.reddit.RedditOAuth
import io.github.brealorg.subshell.data.auth.reddit.RedditOAuthRuntimeSelection
import io.github.brealorg.subshell.data.drafts.TextPostSubmitAttemptStore
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.SubshellNetworkTransport
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
