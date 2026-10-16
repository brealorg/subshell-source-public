package app.subshell.prototype.ui.error

import app.subshell.prototype.data.account.CommentDeleteFailureKind
import app.subshell.prototype.data.account.CommentDeleteUiState
import app.subshell.prototype.data.account.CommentEditFailureKind
import app.subshell.prototype.data.account.CommentEditUiState
import app.subshell.prototype.data.account.CommentWriteFailureKind
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipFailureKind
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.CustomFeedCreateFailureKind
import app.subshell.prototype.data.account.CustomFeedManageFailureKind
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.data.account.PostDeleteFailureKind
import app.subshell.prototype.data.account.PostDeleteUiState
import app.subshell.prototype.data.account.PostEditFailureKind
import app.subshell.prototype.data.account.PostEditUiState
import app.subshell.prototype.data.account.PostManageFailureKind
import app.subshell.prototype.data.account.PostManageUiState
import app.subshell.prototype.data.account.PostVoteFailureKind
import app.subshell.prototype.data.account.PostVoteUiState
import app.subshell.prototype.data.account.PostSaveFailureKind
import app.subshell.prototype.data.account.PostSaveUiState
import app.subshell.prototype.data.account.PostFollowFailureKind
import app.subshell.prototype.data.account.PostFollowUiState
import app.subshell.prototype.data.account.ContentSafetyFailureKind
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.isCommentSaveFullname
import app.subshell.prototype.data.account.TextPostWriteFailureKind
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyPhase
import app.subshell.prototype.data.account.MessageReplyUiState
import app.subshell.prototype.data.account.NOT_CONFIRMED_MEMBERSHIP_MESSAGE
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.data.error.FailureDetails


internal fun contentSafetyErrorPresentation(
    state: ContentSafetyUiState,
): ErrorPresentation {
    val outcomeUnknown =
        state.failureKind == ContentSafetyFailureKind.OUTCOME_UNKNOWN
    val title = when {
        outcomeUnknown && state.operation == ContentSafetyOperation.REPORT_CONTENT ->
            "Report status not confirmed"
        state.operation == ContentSafetyOperation.REPORT_CONTENT ->
            "Couldn't report content"
        state.operation == ContentSafetyOperation.SET_USER_BLOCKED && state.blocked == false ->
            "Couldn't unblock user"
        state.operation == ContentSafetyOperation.SET_USER_BLOCKED ->
            "Couldn't block user"
        state.hidden == false ->
            "Couldn't unhide post"
        else ->
            "Couldn't hide post"
    }
    return ErrorPresentation(
        // SAFETY01A31_CONTENT_SAFETY_VIEWPORT_DEGRADED_V1
        kind = ErrorPresentationKind.DEGRADED,
        title = title,
        message = when (state.failureKind) {
            ContentSafetyFailureKind.CONFIGURATION ->
                "Reddit safety actions aren't configured for this account."
            ContentSafetyFailureKind.ACCOUNT_CHANGED ->
                "The active account changed before the action could finish."
            ContentSafetyFailureKind.BROWSER_LAUNCH ->
                "Reddit authorization couldn't be opened."
            ContentSafetyFailureKind.CALLBACK ->
                "Reddit authorization didn't complete."
            ContentSafetyFailureKind.AUTHORIZATION ->
                "Reddit didn't grant the permission required for this action."
            ContentSafetyFailureKind.SESSION ->
                "The Reddit sign-in for this account needs attention."
            ContentSafetyFailureKind.VALIDATION ->
                "The safety action contains invalid data."
            ContentSafetyFailureKind.HTTP ->
                "Reddit rejected the safety request."
            ContentSafetyFailureKind.TRANSPORT ->
                "subshell couldn't reach Reddit for this action."
            ContentSafetyFailureKind.PROTOCOL ->
                "Reddit returned a response subshell couldn't verify."
            // LOWUI_SAFETY_COPY_NAMES_ITS_ACTION_V1 (OCB-170): a block or hide
            // failure no longer reads as a report.
            ContentSafetyFailureKind.API ->
                "Reddit rejected the ${contentSafetyActionNoun(state)}."
            ContentSafetyFailureKind.PROVIDER_RESTRICTION ->
                "Reddit only allows this block action for approved OAuth clients. " +
                    "This account connection cannot perform it."
            ContentSafetyFailureKind.OUTCOME_UNKNOWN ->
                when (state.operation) {
                    ContentSafetyOperation.REPORT_CONTENT, null ->
                        "Reddit may have received this report. Check the content before reporting again."
                    ContentSafetyOperation.SET_USER_BLOCKED ->
                        "Reddit may have received the ${contentSafetyActionNoun(state)}. " +
                            "Check the user's profile before trying again."
                    ContentSafetyOperation.SET_POST_HIDDEN ->
                        "Reddit may have received the ${contentSafetyActionNoun(state)}. " +
                            "Check the post before trying again."
                }
            null ->
                "The Reddit safety action couldn't be completed."
        },
        primaryAction = if (state.retryable && !outcomeUnknown) {
            ErrorAction(ErrorActionKind.RETRY, "Retry")
        } else {
            null
        },
        secondaryAction = ErrorAction(ErrorActionKind.DISMISS, "Dismiss"),
    ).withFailureDetails(state.failureDetails)
}

private fun contentSafetyActionNoun(state: ContentSafetyUiState): String = when (state.operation) {
    ContentSafetyOperation.REPORT_CONTENT, null -> "report"
    ContentSafetyOperation.SET_USER_BLOCKED ->
        if (state.blocked == false) "unblock request" else "block request"
    ContentSafetyOperation.SET_POST_HIDDEN ->
        if (state.hidden == false) "unhide request" else "hide request"
}

internal fun commentWriteErrorPresentation(
    state: CommentWriteUiState,
): ErrorPresentation {
    if (state.phase == CommentWritePhase.OUTCOME_UNKNOWN) {
        return writeErrorPresentation(
            title = "Comment may have been posted",
            // COMPOSER01_COMMENT_OUTCOME_READ_BACK_V1: Check again reads the
            // thread; the same comment is never sent twice without it.
            message = "Reddit may have received this comment. Check the thread before trying again.",
            details = state.failureDetails,
            retryable = true,
            retryLabel = "Check again",
            dismissible = true,
        )
    }
    return writeErrorPresentation(
        title = "Couldn't post comment",
        message = when (state.failureKind) {
            CommentWriteFailureKind.CONFIGURATION ->
                "Comment posting isn't configured for this account."
            CommentWriteFailureKind.ACCOUNT_CHANGED ->
                "The active account changed. Review the draft before posting."
            CommentWriteFailureKind.BROWSER_LAUNCH ->
                "Reddit authorization couldn't be opened."
            CommentWriteFailureKind.CALLBACK ->
                "Reddit authorization couldn't be completed."
            CommentWriteFailureKind.AUTHORIZATION ->
                "Comment access wasn't granted."
            CommentWriteFailureKind.SESSION ->
                "The Reddit sign-in for this account needs attention."
            CommentWriteFailureKind.RATE_LIMIT ->
                "Reddit asked you to wait before posting again."
            CommentWriteFailureKind.PARENT_UNAVAILABLE ->
                "This thread can no longer accept replies."
            CommentWriteFailureKind.PERMISSION ->
                "This account can't comment in this community."
            CommentWriteFailureKind.VALIDATION ->
                "Review the comment text and try again."
            CommentWriteFailureKind.NOT_POSTED ->
                "Reddit doesn't show this comment. You can send it again."
            CommentWriteFailureKind.MEDIA_NOT_ALLOWED ->
                "This community doesn't allow this kind of image in comments. Remove it to send the text."
            CommentWriteFailureKind.MEDIA_TOO_LARGE ->
                "This image is too large to post. Choose a smaller one."
            CommentWriteFailureKind.MEDIA_UNSUPPORTED ->
                "This image type can't be posted. Choose a JPEG, PNG, WebP, or GIF."
            CommentWriteFailureKind.MEDIA_UNAVAILABLE ->
                "This image can no longer be read. Choose it again."
            CommentWriteFailureKind.API ->
                "Reddit rejected the comment."
            CommentWriteFailureKind.HTTP ->
                "Reddit rejected the comment request."
            CommentWriteFailureKind.TRANSPORT ->
                "subshell couldn't reach Reddit to post the comment."
            CommentWriteFailureKind.ALREADY_SUBMITTING ->
                "This comment is already being posted."
            null -> "The comment couldn't be posted."
        },
        details = state.failureDetails,
        retryable = state.retryable &&
            state.failureKind != CommentWriteFailureKind.ALREADY_SUBMITTING,
        dismissible = true,
    )
}


internal fun messageReplyErrorPresentation(
    state: MessageReplyUiState,
    directChat: Boolean,
    canRefresh: Boolean = true,
): ErrorPresentation {
    // SUBSHELL_MESSAGES02C4R3R1_MESSAGE_REPLY_ERROR_PRESENTATION_PARITY_V1
    val noun = if (directChat) "message" else "reply"
    val nounTitle = if (directChat) "Message" else "Reply"

    // SUBSHELL_MESSAGES02E2A2_REDDIT_PARITY_DUAL_WRITE_V1
    if (directChat && state.sentHadMedia && state.completionRevision > 0L) {
        if (state.phase == MessageReplyPhase.OUTCOME_UNKNOWN) {
            return writeErrorPresentation(
                title = "Media sent; text may have been sent",
                message = "The image/GIF was sent, but Reddit did not confirm the text. Refresh the conversation before trying the text again.",
                details = null,
                retryable = canRefresh,
                retryLabel = "Refresh",
                dismissible = false,
            ).copy(
                secondaryAction = ErrorAction(ErrorActionKind.DISMISS, "Checked"),
            )
        }
        if (state.phase == MessageReplyPhase.FAILED) {
            return writeErrorPresentation(
                title = "Media sent, text not sent",
                message = "The image/GIF was sent, but the message text wasn't sent.",
                details = null,
                retryable = false,
                dismissible = true,
            )
        }
    }

    if (state.phase == MessageReplyPhase.OUTCOME_UNKNOWN) {
        return writeErrorPresentation(
            title = "$nounTitle may have been sent",
            message = "Reddit may have received this $noun. Refresh the conversation before trying again.",
            details = null,
            retryable = canRefresh,
            retryLabel = "Refresh",
            dismissible = false,
        ).copy(
            secondaryAction = ErrorAction(ErrorActionKind.DISMISS, "Checked"),
        )
    }

    val primaryAction = when (state.failureKind) {
        MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED ->
            ErrorAction(ErrorActionKind.SIGN_IN, "Grant media access")
        // CHAT01_CHAT_SESSION_FAILURE_STEP_V1: the Direct Chat media session is
        // Reddit's web session, not the OAuth grant "Reconnect" renews, so no
        // button that cannot help.
        MessageReplyFailureKind.AUTHORIZATION_REQUIRED,
        -> ErrorAction(ErrorActionKind.SIGN_IN, "Reconnect")
        else -> null
    }

    val presentation = writeErrorPresentation(
        title = if (directChat) "Couldn't send message" else "Couldn't send reply",
        message = when (state.failureKind) {
            MessageReplyFailureKind.VALIDATION ->
                "Review the $noun and try again."
            MessageReplyFailureKind.CHAT_SESSION_REQUIRED ->
                "subshell couldn't open Reddit's chat session for images. Text messages still work; the details below say which step failed."
            MessageReplyFailureKind.CHAT_ROOM_UNAVAILABLE ->
                "subshell couldn't match this conversation to its Reddit Chat room."
            MessageReplyFailureKind.MEDIA_CAPTION_UNSUPPORTED ->
                "Send Direct Chat text and media separately for now."
            MessageReplyFailureKind.MEDIA_UNSUPPORTED ->
                "Choose a JPEG, PNG, WebP, or GIF."
            MessageReplyFailureKind.MEDIA_TOO_LARGE ->
                "Image/GIF must be 20 MB or smaller."
            MessageReplyFailureKind.MEDIA_UNAVAILABLE ->
                "This image/GIF can no longer be read. Choose it again."
            MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED ->
                "Reddit media access is required to send images or GIFs."
            MessageReplyFailureKind.AUTHORIZATION_REQUIRED ->
                "The Reddit sign-in for this account needs attention before $noun sending can continue."
            MessageReplyFailureKind.RATE_LIMIT ->
                "Reddit asked you to wait before sending another $noun."
            MessageReplyFailureKind.API ->
                "Reddit rejected the $noun."
            MessageReplyFailureKind.HTTP ->
                "Reddit rejected the $noun request."
            MessageReplyFailureKind.TRANSPORT ->
                "subshell couldn't reach Reddit to send the $noun."
            MessageReplyFailureKind.SESSION ->
                "The Reddit sign-in for this account needs attention."
            // LOWUI4 (OCB-171): not reached; MessageReplyCoordinator turns this
            // kind into phase OUTCOME_UNKNOWN, answered above. Kept for an
            // exhaustive when.
            MessageReplyFailureKind.OUTCOME_UNKNOWN ->
                "Reddit returned a send result subshell couldn't verify."
            MessageReplyFailureKind.UNAVAILABLE ->
                "${nounTitle} sending is unavailable for this conversation."
            null ->
                "The $noun couldn't be sent."
        },
        details = state.failureDetails,
        retryable = false,
        dismissible = true,
    )
    return if (primaryAction == null) {
        presentation
    } else {
        presentation.copy(primaryAction = primaryAction)
    }
}

internal fun textPostWriteErrorPresentation(
    state: TextPostWriteUiState,
): ErrorPresentation {
    if (state.phase == TextPostWritePhase.OUTCOME_UNKNOWN) {
        return writeErrorPresentation(
            title = "Post may have been published",
            message = "Reddit may have received this post. Check your profile before trying to publish it again.",
            details = state.failureDetails,
            retryable = true,
            retryLabel = "Check again",
        )
    }
    return writeErrorPresentation(
        title = "Couldn't publish post",
        message = when (state.failureKind) {
            TextPostWriteFailureKind.CONFIGURATION ->
                "Post publishing isn't configured for this account."
            TextPostWriteFailureKind.ACCOUNT_CHANGED ->
                "The active account changed. Review the draft before posting."
            TextPostWriteFailureKind.BROWSER_LAUNCH ->
                "Reddit authorization couldn't be opened."
            TextPostWriteFailureKind.CALLBACK ->
                "Reddit authorization couldn't be completed."
            TextPostWriteFailureKind.AUTHORIZATION ->
                "Post access wasn't granted."
            TextPostWriteFailureKind.SESSION ->
                "The Reddit sign-in for this account needs attention."
            TextPostWriteFailureKind.RATE_LIMIT ->
                "Reddit asked you to wait before posting again."
            TextPostWriteFailureKind.COMMUNITY_UNAVAILABLE ->
                "This community is unavailable."
            TextPostWriteFailureKind.PERMISSION ->
                "This account can't post in this community."
            TextPostWriteFailureKind.SUBMISSION_RESTRICTED ->
                "This community isn't accepting this post."
            TextPostWriteFailureKind.CHALLENGE_REQUIRED ->
                "Reddit requires an additional verification step."
            TextPostWriteFailureKind.VALIDATION ->
                "Review the highlighted field before publishing."
            TextPostWriteFailureKind.API ->
                "Reddit rejected the post."
            TextPostWriteFailureKind.HTTP ->
                "Reddit rejected the publish request."
            TextPostWriteFailureKind.TRANSPORT ->
                "subshell couldn't reach Reddit to publish the post."
            TextPostWriteFailureKind.ALREADY_SUBMITTING ->
                "This post is already being published."
            TextPostWriteFailureKind.NOT_POSTED ->
                "Reddit doesn't show this post. You can post it again."
            TextPostWriteFailureKind.EARLIER_POST_UNCONFIRMED ->
                "Reddit may have received an earlier post from this account. Check it before posting this one."
            null -> "The post couldn't be published."
        },
        details = state.failureDetails,
        retryable = state.retryable &&
            state.failureKind != TextPostWriteFailureKind.ALREADY_SUBMITTING,
        // COMPOSER02_SUBMIT_ATTEMPT_PER_ACCOUNT_V1: the action reads the earlier
        // post back; it never sends this one.
        retryLabel = if (
            state.failureKind == TextPostWriteFailureKind.EARLIER_POST_UNCONFIRMED
        ) {
            "Check again"
        } else {
            "Retry"
        },
        dismissible = true,
    )
}

internal fun commentEditErrorPresentation(
    state: CommentEditUiState,
): ErrorPresentation = writeErrorPresentation(
    // H1_COMMENT_EDIT_OUTCOME_UNKNOWN_V1: an unconfirmed edit offers Check
    // again (a read), never a blind re-send.
    title = if (state.failureKind == CommentEditFailureKind.OUTCOME_UNKNOWN) {
        "Edit not confirmed"
    } else {
        "Couldn't edit comment"
    },
    message = when (state.failureKind) {
        CommentEditFailureKind.CONFIGURATION ->
            "Comment editing isn't configured for this account."
        CommentEditFailureKind.ACCOUNT_CHANGED ->
            "The active account changed before the comment could be edited."
        CommentEditFailureKind.NOT_OWNER ->
            "Only comments authored by the active account can be edited."
        CommentEditFailureKind.BROWSER_LAUNCH ->
            "Reddit authorization couldn't be opened."
        CommentEditFailureKind.CALLBACK ->
            "Reddit authorization didn't complete."
        CommentEditFailureKind.AUTHORIZATION ->
            "Reddit didn't grant the permission required to edit this comment."
        CommentEditFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        CommentEditFailureKind.RATE_LIMIT ->
            "Reddit rate-limited the edit request. Try again shortly."
        CommentEditFailureKind.VALIDATION ->
            "Reddit rejected the edited comment text."
        CommentEditFailureKind.API ->
            "Reddit rejected the comment edit."
        CommentEditFailureKind.HTTP ->
            "Reddit rejected the edit request."
        CommentEditFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit. The comment wasn't changed."
        CommentEditFailureKind.ALREADY_EDITING ->
            "This comment is already being edited."
        CommentEditFailureKind.OUTCOME_UNKNOWN ->
            "Reddit may have saved the edit, but subshell couldn't confirm it. " +
                "Your text is still here."
        CommentEditFailureKind.NOT_SAVED ->
            "Reddit didn't save the edit. Your text is still here, so you can save it again."
        null -> "The comment couldn't be edited."
    },
    details = state.failureDetails,
    retryable = state.failureKind == CommentEditFailureKind.OUTCOME_UNKNOWN || (
        state.retryable &&
            state.failureKind != CommentEditFailureKind.ALREADY_EDITING
        ),
    retryLabel = if (state.failureKind == CommentEditFailureKind.OUTCOME_UNKNOWN) "Check again" else "Retry",
    dismissible = true,
)

internal fun postEditErrorPresentation(
    state: PostEditUiState,
): ErrorPresentation = writeErrorPresentation(
    // H2_POST_EDIT_OUTCOME_UNKNOWN_V1: an unconfirmed edit offers Check
    // again (a read), never a blind re-send.
    title = if (state.failureKind == PostEditFailureKind.OUTCOME_UNKNOWN) {
        "Edit not confirmed"
    } else {
        "Couldn't edit post"
    },
    message = when (state.failureKind) {
        PostEditFailureKind.CONFIGURATION ->
            "Post editing isn't configured for this account."
        PostEditFailureKind.ACCOUNT_CHANGED ->
            "The active account changed before the post could be edited."
        PostEditFailureKind.NOT_OWNER ->
            "Only self-posts authored by the active account can be edited."
        PostEditFailureKind.BROWSER_LAUNCH ->
            "Reddit authorization couldn't be opened."
        PostEditFailureKind.CALLBACK ->
            "Reddit authorization didn't complete."
        PostEditFailureKind.AUTHORIZATION ->
            "Reddit didn't grant the permission required to edit this post."
        PostEditFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        PostEditFailureKind.RATE_LIMIT ->
            "Reddit rate-limited the edit request. Try again shortly."
        PostEditFailureKind.VALIDATION ->
            "Reddit rejected the edited post body."
        PostEditFailureKind.API ->
            "Reddit rejected the post edit."
        PostEditFailureKind.HTTP ->
            "Reddit rejected the edit request."
        PostEditFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit. The post wasn't changed."
        PostEditFailureKind.ALREADY_EDITING ->
            "This post is already being edited."
        PostEditFailureKind.OUTCOME_UNKNOWN ->
            "Reddit may have saved the edit, but subshell couldn't confirm it. " +
                "Your text is still here."
        PostEditFailureKind.NOT_SAVED ->
            "Reddit didn't save the edit. Your text is still here, so you can save it again."
        null -> "The post couldn't be edited."
    },
    details = state.failureDetails,
    retryable = state.failureKind == PostEditFailureKind.OUTCOME_UNKNOWN || (
        state.retryable &&
            state.failureKind != PostEditFailureKind.ALREADY_EDITING
        ),
    retryLabel = if (state.failureKind == PostEditFailureKind.OUTCOME_UNKNOWN) "Check again" else "Retry",
)

internal fun commentDeleteErrorPresentation(
    state: CommentDeleteUiState,
): ErrorPresentation {
    val outcomeUnconfirmed =
        state.failureKind == CommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED
    return writeErrorPresentation(
        title = if (outcomeUnconfirmed) {
            "Comment deletion not confirmed"
        } else {
            "Couldn't delete comment"
        },
        message = when (state.failureKind) {
            CommentDeleteFailureKind.CONFIGURATION ->
                "Comment deletion isn't configured for this account."
            CommentDeleteFailureKind.ACCOUNT_CHANGED ->
                "The active account changed before the comment could be deleted."
            CommentDeleteFailureKind.NOT_OWNER ->
                "Only comments authored by the active account can be deleted."
            CommentDeleteFailureKind.BROWSER_LAUNCH ->
                "Reddit authorization couldn't be opened."
            CommentDeleteFailureKind.CALLBACK ->
                "Reddit authorization didn't complete."
            CommentDeleteFailureKind.AUTHORIZATION ->
                "Reddit didn't grant the permission required to delete this comment."
            CommentDeleteFailureKind.SESSION ->
                "The Reddit sign-in for this account needs attention."
            CommentDeleteFailureKind.RATE_LIMIT ->
                "Reddit rate-limited the delete request. Try again shortly."
            CommentDeleteFailureKind.HTTP ->
                "Reddit rejected the delete request."
            CommentDeleteFailureKind.TRANSPORT ->
                "subshell couldn't reach Reddit. The comment wasn't confirmed deleted."
            CommentDeleteFailureKind.PROTOCOL ->
                "Reddit returned a response subshell couldn't verify."
            CommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED ->
                // LOWUI_DELETE_NOT_CONFIRMED_SAYS_MAY_V1 (OCB-172): a timeout or a
                // dropped connection is not an accepted request.
                "Reddit may have received the delete request, but subshell couldn't confirm it. Check the thread before trying again."
            CommentDeleteFailureKind.ALREADY_DELETING ->
                "This comment is already being deleted."
            null -> "The comment couldn't be deleted."
        },
        details = state.failureDetails,
        retryable = state.retryable &&
            !outcomeUnconfirmed &&
            state.failureKind != CommentDeleteFailureKind.ALREADY_DELETING,
    )
}

internal fun postDeleteErrorPresentation(
    state: PostDeleteUiState,
): ErrorPresentation = writeErrorPresentation(
    // H3_POST_DELETE_OUTCOME_UNKNOWN_V1: an unconfirmed delete offers Check
    // again (a read), never a blind re-send.
    title = if (state.failureKind == PostDeleteFailureKind.OUTCOME_UNKNOWN) {
        "Deletion not confirmed"
    } else {
        "Couldn't delete post"
    },
    message = when (state.failureKind) {
        PostDeleteFailureKind.CONFIGURATION ->
            "Post deletion isn't configured for this account."
        PostDeleteFailureKind.ACCOUNT_CHANGED ->
            "The active account changed. Reopen the post and try again."
        PostDeleteFailureKind.NOT_OWNER ->
            "This post isn't owned by the active account."
        PostDeleteFailureKind.BROWSER_LAUNCH ->
            "Reddit authorization couldn't be opened."
        PostDeleteFailureKind.CALLBACK ->
            "Reddit authorization didn't complete."
        PostDeleteFailureKind.AUTHORIZATION ->
            "Reddit didn't grant the permission required to delete this post."
        PostDeleteFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        PostDeleteFailureKind.RATE_LIMIT ->
            "Reddit rate-limited the delete request. Try again shortly."
        PostDeleteFailureKind.HTTP ->
            "Reddit rejected the delete request."
        PostDeleteFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit. The post wasn't deleted."
        PostDeleteFailureKind.PROTOCOL ->
            "Reddit returned a response subshell couldn't verify."
        PostDeleteFailureKind.ALREADY_DELETING ->
            "A post deletion is already in progress."
        PostDeleteFailureKind.OUTCOME_UNKNOWN ->
            "Reddit may have deleted the post, but subshell couldn't confirm it."
        PostDeleteFailureKind.NOT_DELETED ->
            "Reddit didn't delete the post. You can try again."
        null -> "The post couldn't be deleted."
    },
    details = state.failureDetails,
    retryable = state.failureKind == PostDeleteFailureKind.OUTCOME_UNKNOWN || (
        state.retryable &&
            state.failureKind != PostDeleteFailureKind.ALREADY_DELETING
        ),
    retryLabel = if (state.failureKind == PostDeleteFailureKind.OUTCOME_UNKNOWN) "Check again" else "Retry",
)

internal fun postManageErrorPresentation(
    state: PostManageUiState,
): ErrorPresentation = writeErrorPresentation(
    title = if (state.operation == null) {
        "Couldn't load post controls"
    } else {
        "Couldn't update post"
    },
    message = when (state.failureKind) {
        PostManageFailureKind.CONFIGURATION ->
            "Post management is unavailable for this account."
        PostManageFailureKind.ACCOUNT_CHANGED ->
            "The active account changed. Reopen the post and try again."
        PostManageFailureKind.NOT_OWNER ->
            "This post isn't owned by the active account."
        PostManageFailureKind.BROWSER_LAUNCH ->
            "Reddit authorization couldn't be opened."
        PostManageFailureKind.CALLBACK ->
            "Reddit authorization didn't complete."
        PostManageFailureKind.AUTHORIZATION ->
            "Reddit didn't grant the permission required for this change."
        PostManageFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        PostManageFailureKind.RATE_LIMIT ->
            "Reddit rate-limited the request. Try again shortly."
        PostManageFailureKind.HTTP ->
            "Reddit rejected the post-management request."
        PostManageFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit for this change."
        PostManageFailureKind.PROTOCOL ->
            "Reddit returned a response subshell couldn't use."
        PostManageFailureKind.ALREADY_MUTATING ->
            "Another post change is already in progress."
        null -> "Post management couldn't be completed."
    },
    details = state.failureDetails,
    retryable = state.retryable && state.operation == null,
)

internal fun communityMembershipErrorPresentation(
    state: CommunityMembershipUiState,
): ErrorPresentation {
    val outcomeUnknown =
        state.failureKind == CommunityMembershipFailureKind.OUTCOME_UNKNOWN
    val notConfirmed =
        state.failureKind == CommunityMembershipFailureKind.NOT_CONFIRMED
    val failureTitle = when (state.action) {
        CommunityMembershipAction.JOIN -> "Couldn't join community"
        CommunityMembershipAction.LEAVE -> "Couldn't leave community"
        null -> "Couldn't change community membership"
    }
    return writeErrorPresentation(
        title = if (outcomeUnknown || notConfirmed) {
            "Membership change not confirmed"
        } else {
            failureTitle
        },
        message = when (state.failureKind) {
            CommunityMembershipFailureKind.VALIDATION ->
                "This community membership request is invalid."
            CommunityMembershipFailureKind.ACCOUNT_CHANGED ->
                "The active Reddit account changed. Try again."
            CommunityMembershipFailureKind.CONFIGURATION ->
                "Community membership changes are unavailable for this account."
            CommunityMembershipFailureKind.AUTHORIZATION,
            CommunityMembershipFailureKind.CALLBACK,
            CommunityMembershipFailureKind.BROWSER_LAUNCH,
            -> "Reddit authorization didn't complete."
            CommunityMembershipFailureKind.SESSION ->
                "The Reddit sign-in for this account needs attention."
            CommunityMembershipFailureKind.HTTP ->
                if (state.failureDetails?.httpStatusCode == 429) {
                    "Reddit is rate limiting this action. Try again later."
                } else {
                    "Reddit rejected the membership change."
                }
            CommunityMembershipFailureKind.PROTOCOL ->
                "Reddit returned a response subshell couldn't use."
            CommunityMembershipFailureKind.OUTCOME_UNKNOWN ->
                "Reddit may have received this membership change. subshell is checking the current membership before another change is allowed."
            CommunityMembershipFailureKind.NOT_CONFIRMED ->
                state.message ?: NOT_CONFIRMED_MEMBERSHIP_MESSAGE
            null -> "The membership change couldn't be completed."
        },
        details = state.failureDetails,
        retryable = state.failureRetryable && !outcomeUnknown,
    )
}

internal fun customFeedCreateErrorPresentation(
    state: CustomFeedsUiState,
): ErrorPresentation {
    val outcomeUnknown =
        state.createFailureKind == CustomFeedCreateFailureKind.OUTCOME_UNKNOWN
    return writeErrorPresentation(
        title = if (outcomeUnknown) {
            "Feed creation not confirmed"
        } else {
            "Couldn't create custom feed"
        },
        message = when (state.createFailureKind) {
            CustomFeedCreateFailureKind.CONFIGURATION ->
                "Custom feed creation is unavailable for this account."
            CustomFeedCreateFailureKind.ACCOUNT_CHANGED ->
                "The active Reddit account changed."
            CustomFeedCreateFailureKind.AUTHORIZATION,
            CustomFeedCreateFailureKind.CALLBACK,
            -> "Reddit authorization didn't complete."
            CustomFeedCreateFailureKind.SESSION ->
                "The Reddit sign-in for this account needs attention."
            CustomFeedCreateFailureKind.NAME_CONFLICT ->
                "A custom feed with that name already exists."
            CustomFeedCreateFailureKind.HTTP ->
                "Reddit rejected the create request."
            CustomFeedCreateFailureKind.OUTCOME_UNKNOWN ->
                "Reddit may have received this request. subshell is checking your feeds before another create is allowed."
            CustomFeedCreateFailureKind.PROTOCOL ->
                "Reddit returned a response subshell couldn't use."
            CustomFeedCreateFailureKind.VALIDATION ->
                "Choose a valid name and at least one community."
            null -> "The custom feed couldn't be created."
        },
        details = state.createFailureDetails,
        retryable = state.createFailureDetails?.retryable == true && !outcomeUnknown,
    )
}

internal fun customFeedManageErrorPresentation(
    state: CustomFeedsUiState,
    // LOWUX1_FEED_DELETE_FAILURE_SAYS_DELETE_V1 (OCB-187): the delete dialog
    // reported "Couldn't update custom feed".
    deleting: Boolean = false,
): ErrorPresentation {
    val outcomeUnknown =
        state.manageFailureKind == CustomFeedManageFailureKind.OUTCOME_UNKNOWN
    return writeErrorPresentation(
        title = when {
            outcomeUnknown && deleting -> "Feed deletion not confirmed"
            outcomeUnknown -> "Feed change not confirmed"
            deleting -> "Couldn't delete custom feed"
            else -> "Couldn't update custom feed"
        },
        message = when (state.manageFailureKind) {
            CustomFeedManageFailureKind.CONFIGURATION ->
                "Custom feed management is unavailable for this account."
            CustomFeedManageFailureKind.ACCOUNT_CHANGED ->
                "The active Reddit account changed."
            CustomFeedManageFailureKind.AUTHORIZATION,
            CustomFeedManageFailureKind.CALLBACK,
            -> "Reddit authorization didn't complete."
            CustomFeedManageFailureKind.SESSION ->
                "The Reddit sign-in for this account needs attention."
            CustomFeedManageFailureKind.HTTP ->
                "Reddit rejected the custom-feed request."
            CustomFeedManageFailureKind.OUTCOME_UNKNOWN ->
                "Reddit may have received this request. subshell is checking your feeds before another change is allowed."
            CustomFeedManageFailureKind.PROTOCOL ->
                "Reddit returned a response subshell couldn't use."
            CustomFeedManageFailureKind.VALIDATION ->
                "The custom feed details are invalid."
            null -> "The custom feed change couldn't be completed."
        },
        details = state.manageFailureDetails,
        retryable = state.manageFailureDetails?.retryable == true && !outcomeUnknown,
    )
}

// COMMENTSAVE01A2_TARGET_AWARE_SAVE_ERROR_COPY_V1
// FOLLOWPOST01A1B_SHARED_WRITE_ERROR_PRESENTATION_V1
internal fun postFollowErrorPresentation(
    state: PostFollowUiState,
): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = "Couldn't update followed post",
    message = when (state.failureKind) {
        PostFollowFailureKind.CONFIGURATION ->
            "Following posts isn't configured for this account."
        PostFollowFailureKind.ACCOUNT_CHANGED ->
            "The active account changed before the followed state could be updated."
        PostFollowFailureKind.BROWSER_LAUNCH ->
            "Reddit authorization couldn't be opened."
        PostFollowFailureKind.CALLBACK ->
            "Reddit authorization didn't complete."
        PostFollowFailureKind.AUTHORIZATION ->
            "Reddit didn't grant post-follow access."
        PostFollowFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        PostFollowFailureKind.VALIDATION ->
            "This post can't be followed."
        PostFollowFailureKind.HTTP ->
            "Reddit rejected the post-follow request."
        PostFollowFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit to update the followed state."
        PostFollowFailureKind.PROTOCOL ->
            "Reddit returned an unexpected response to the post-follow request."
        null ->
            "The followed state couldn't be updated."
    },
    primaryAction = if (state.retryable) {
        ErrorAction(
            ErrorActionKind.RETRY,
            "Retry",
        )
    } else {
        null
    },
    secondaryAction =
        ErrorAction(
            ErrorActionKind.DISMISS,
            "Dismiss",
        ),
).withFailureDetails(state.failureDetails)

internal fun postSaveErrorPresentation(
    state: PostSaveUiState,
): ErrorPresentation {
    val commentTarget = state.postFullname
        ?.let { fullname -> isCommentSaveFullname(fullname) } == true
    return ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = if (commentTarget) {
            "Couldn't update saved comment"
        } else {
            "Couldn't update saved post"
        },
        message = when (state.failureKind) {
            PostSaveFailureKind.CONFIGURATION ->
                if (commentTarget) {
                    "Saving comments isn't configured for this account."
                } else {
                    "Saving posts isn't configured for this account."
                }
            PostSaveFailureKind.ACCOUNT_CHANGED ->
                "The active account changed before the saved state could be updated."
            PostSaveFailureKind.BROWSER_LAUNCH ->
                "Reddit authorization couldn't be opened."
            PostSaveFailureKind.CALLBACK ->
                "Reddit authorization didn't complete."
            PostSaveFailureKind.AUTHORIZATION ->
                "Reddit didn't grant save access."
            PostSaveFailureKind.SESSION ->
                "The Reddit sign-in for this account needs attention."
            PostSaveFailureKind.VALIDATION ->
                if (commentTarget) {
                    "This comment can't be saved."
                } else {
                    "This post can't be saved."
                }
            PostSaveFailureKind.HTTP ->
                "Reddit rejected the save request."
            PostSaveFailureKind.TRANSPORT ->
                "subshell couldn't reach Reddit to update the saved state."
            PostSaveFailureKind.PROTOCOL ->
                "Reddit returned an unexpected response to the save request."
            null -> "The saved state couldn't be updated."
        },
        primaryAction = if (state.retryable) {
            ErrorAction(ErrorActionKind.RETRY, "Retry")
        } else {
            null
        },
        secondaryAction = ErrorAction(ErrorActionKind.DISMISS, "Dismiss"),
    ).withFailureDetails(state.failureDetails)
}

internal fun postVoteErrorPresentation(
    state: PostVoteUiState,
): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = "Couldn't update vote",
    message = when (state.failureKind) {
        PostVoteFailureKind.CONFIGURATION ->
            "Voting isn't configured for this account."
        PostVoteFailureKind.ACCOUNT_CHANGED ->
            "The active account changed before the vote could be updated."
        PostVoteFailureKind.BROWSER_LAUNCH ->
            "Reddit authorization couldn't be opened."
        PostVoteFailureKind.CALLBACK ->
            "Reddit authorization didn't complete."
        PostVoteFailureKind.AUTHORIZATION ->
            "Reddit didn't grant vote access."
        PostVoteFailureKind.SESSION ->
            "The Reddit sign-in for this account needs attention."
        PostVoteFailureKind.VALIDATION ->
            "This item can't be voted on."
        PostVoteFailureKind.HTTP ->
            "Reddit rejected the vote request."
        PostVoteFailureKind.TRANSPORT ->
            "subshell couldn't reach Reddit to update your vote."
        PostVoteFailureKind.PROTOCOL ->
            "Reddit returned an unexpected response to the vote request."
        null -> "The vote couldn't be updated."
    },
    primaryAction = if (state.retryable) {
        ErrorAction(ErrorActionKind.RETRY, "Retry")
    } else {
        null
    },
    secondaryAction = ErrorAction(ErrorActionKind.DISMISS, "Dismiss"),
).withFailureDetails(state.failureDetails)

private fun writeErrorPresentation(
    title: String,
    message: String,
    details: FailureDetails?,
    retryable: Boolean,
    retryLabel: String = "Retry",
    dismissible: Boolean = false,
): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.INLINE,
    title = title,
    message = message,
    primaryAction = if (retryable) {
        ErrorAction(ErrorActionKind.RETRY, retryLabel)
    } else {
        null
    },
    secondaryAction = if (dismissible) {
        ErrorAction(ErrorActionKind.DISMISS, "Dismiss")
    } else {
        null
    },
).withFailureDetails(details)
