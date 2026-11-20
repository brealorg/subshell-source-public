package io.github.brealorg.subshell.ui.error

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.data.account.CommentDeleteFailureKind
import io.github.brealorg.subshell.data.account.CommentDeleteUiState
import io.github.brealorg.subshell.data.account.CommentEditFailureKind
import io.github.brealorg.subshell.data.account.CommentEditUiState
import io.github.brealorg.subshell.data.account.CommentWriteFailureKind
import io.github.brealorg.subshell.data.account.CommentWritePhase
import io.github.brealorg.subshell.data.account.CommentWriteUiState
import io.github.brealorg.subshell.data.account.CommunityMembershipAction
import io.github.brealorg.subshell.data.account.CommunityMembershipFailureKind
import io.github.brealorg.subshell.data.account.CommunityMembershipUiState
import io.github.brealorg.subshell.data.account.CustomFeedCreateFailureKind
import io.github.brealorg.subshell.data.account.CustomFeedManageFailureKind
import io.github.brealorg.subshell.data.account.CustomFeedsUiState
import io.github.brealorg.subshell.data.account.PostDeleteFailureKind
import io.github.brealorg.subshell.data.account.PostDeleteUiState
import io.github.brealorg.subshell.data.account.PostEditFailureKind
import io.github.brealorg.subshell.data.account.PostEditUiState
import io.github.brealorg.subshell.data.account.PostManageFailureKind
import io.github.brealorg.subshell.data.account.PostManageUiState
import io.github.brealorg.subshell.data.account.PostVoteFailureKind
import io.github.brealorg.subshell.data.account.PostVoteUiState
import io.github.brealorg.subshell.data.account.PostSaveFailureKind
import io.github.brealorg.subshell.data.account.PostSaveUiState
import io.github.brealorg.subshell.data.account.PostFollowFailureKind
import io.github.brealorg.subshell.data.account.PostFollowUiState
import io.github.brealorg.subshell.data.account.ContentSafetyFailureKind
import io.github.brealorg.subshell.data.account.ContentSafetyOperation
import io.github.brealorg.subshell.data.account.ContentSafetyUiState
import io.github.brealorg.subshell.data.account.isCommentSaveFullname
import io.github.brealorg.subshell.data.account.TextPostWriteFailureKind
import io.github.brealorg.subshell.data.account.TextPostWritePhase
import io.github.brealorg.subshell.data.account.MessageReplyFailureKind
import io.github.brealorg.subshell.data.account.MessageReplyPhase
import io.github.brealorg.subshell.data.account.MessageReplyUiState
import io.github.brealorg.subshell.data.account.NOT_CONFIRMED_MEMBERSHIP_MESSAGE
import io.github.brealorg.subshell.data.account.TextPostWriteUiState
import io.github.brealorg.subshell.data.error.FailureDetails


internal fun contentSafetyErrorPresentation(
    state: ContentSafetyUiState,
): ErrorPresentation {
    val outcomeUnknown =
        state.failureKind == ContentSafetyFailureKind.OUTCOME_UNKNOWN
    val title = when {
        outcomeUnknown && state.operation == ContentSafetyOperation.REPORT_CONTENT ->
            UiText.Resource(R.string.error_write_report_status_not_confirmed)
        state.operation == ContentSafetyOperation.REPORT_CONTENT ->
            UiText.Resource(R.string.error_write_couldn_t_report_content)
        state.operation == ContentSafetyOperation.SET_USER_BLOCKED && state.blocked == false ->
            UiText.Resource(R.string.error_write_couldn_t_unblock_user)
        state.operation == ContentSafetyOperation.SET_USER_BLOCKED ->
            UiText.Resource(R.string.error_write_couldn_t_block_user)
        state.hidden == false ->
            UiText.Resource(R.string.error_write_couldn_t_unhide_post)
        else ->
            UiText.Resource(R.string.error_write_couldn_t_hide_post)
    }
    return ErrorPresentation(
        // SAFETY01A31_CONTENT_SAFETY_VIEWPORT_DEGRADED_V1
        kind = ErrorPresentationKind.DEGRADED,
        title = title,
        message = when (state.failureKind) {
            ContentSafetyFailureKind.CONFIGURATION ->
                UiText.Resource(R.string.error_write_reddit_safety_actions_aren_t_configured_for)
            ContentSafetyFailureKind.ACCOUNT_CHANGED ->
                UiText.Resource(R.string.error_write_the_active_account_changed_before_the_action)
            ContentSafetyFailureKind.BROWSER_LAUNCH ->
                UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_opened)
            ContentSafetyFailureKind.CALLBACK ->
                UiText.Resource(R.string.error_write_reddit_authorization_didn_t_complete)
            ContentSafetyFailureKind.AUTHORIZATION ->
                UiText.Resource(R.string.error_write_reddit_didn_t_grant_the_permission_required)
            ContentSafetyFailureKind.SESSION ->
                UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
            ContentSafetyFailureKind.VALIDATION ->
                UiText.Resource(R.string.error_write_the_safety_action_contains_invalid_data)
            ContentSafetyFailureKind.HTTP ->
                UiText.Resource(R.string.error_write_reddit_rejected_the_safety_request)
            ContentSafetyFailureKind.TRANSPORT ->
                UiText.Resource(R.string.error_write_subshell_couldn_t_reach_reddit_for_this)
            ContentSafetyFailureKind.PROTOCOL ->
                UiText.Resource(R.string.error_write_reddit_returned_a_response_subshell_couldn_t)
            // LOWUI_SAFETY_COPY_NAMES_ITS_ACTION_V1 (OCB-170): a block or hide
            // failure no longer reads as a report.
            ContentSafetyFailureKind.API ->
                UiText.Resource(R.string.error_write_reddit_rejected_the, contentSafetyActionNoun(state))
            ContentSafetyFailureKind.PROVIDER_RESTRICTION ->
                UiText.Resource(R.string.error_write_reddit_only_allows_this_block_action_for)
            ContentSafetyFailureKind.OUTCOME_UNKNOWN ->
                when (state.operation) {
                    ContentSafetyOperation.REPORT_CONTENT, null ->
                        UiText.Resource(R.string.error_write_reddit_may_have_received_this_report_check)
                    ContentSafetyOperation.SET_USER_BLOCKED ->
                        UiText.Resource(R.string.error_write_reddit_may_have_received_the_check_the, contentSafetyActionNoun(state))
                    ContentSafetyOperation.SET_POST_HIDDEN ->
                        UiText.Resource(R.string.error_write_reddit_may_have_received_the_check_the_2, contentSafetyActionNoun(state))
                }
            null ->
                UiText.Resource(R.string.error_write_the_reddit_safety_action_couldn_t_be)
        },
        primaryAction = if (state.retryable && !outcomeUnknown) {
            ErrorAction(ErrorActionKind.RETRY, UiText.Resource(R.string.error_write_retry))
        } else {
            null
        },
        secondaryAction = ErrorAction(ErrorActionKind.DISMISS, UiText.Resource(R.string.error_write_dismiss)),
    ).withFailureDetails(state.failureDetails)
}

private fun contentSafetyActionNoun(state: ContentSafetyUiState): UiText = when (state.operation) {
    ContentSafetyOperation.REPORT_CONTENT, null -> UiText.Resource(R.string.error_write_noun_report)
    ContentSafetyOperation.SET_USER_BLOCKED ->
        if (state.blocked == false) {
            UiText.Resource(R.string.error_write_noun_unblock_request)
        } else {
            UiText.Resource(R.string.error_write_noun_block_request)
        }
    ContentSafetyOperation.SET_POST_HIDDEN ->
        if (state.hidden == false) {
            UiText.Resource(R.string.error_write_noun_unhide_request)
        } else {
            UiText.Resource(R.string.error_write_noun_hide_request)
        }
}

internal fun commentWriteErrorPresentation(
    state: CommentWriteUiState,
): ErrorPresentation {
    if (state.phase == CommentWritePhase.OUTCOME_UNKNOWN) {
        return writeErrorPresentation(
            title = UiText.Resource(R.string.error_write_comment_may_have_been_posted),
            // COMPOSER01_COMMENT_OUTCOME_READ_BACK_V1: Check again reads the
            // thread; the same comment is never sent twice without it.
            message = UiText.Resource(R.string.error_write_reddit_may_have_received_this_comment_check),
            details = state.failureDetails,
            retryable = true,
            retryLabel = UiText.Resource(R.string.error_write_check_again),
            dismissible = true,
        )
    }
    // COMPOSERERR01_REDDITS_REASON_IS_SHOWN_V1: as for posts.
    val redditReason = state.failureDetails?.providerMessage?.value
        ?.takeIf {
            state.failureKind == CommentWriteFailureKind.API ||
                state.failureKind == CommentWriteFailureKind.VALIDATION
        }
    val redditReasonText = redditReason?.let { reason ->
        UiText.Resource(R.string.error_write_reddit_rejected_the_comment_saying, reason)
    }
    return writeErrorPresentation(
        title = UiText.Resource(R.string.error_write_couldn_t_post_comment),
        message = when (state.failureKind) {
            CommentWriteFailureKind.CONFIGURATION ->
                UiText.Resource(R.string.error_write_comment_posting_isn_t_configured_for_this)
            CommentWriteFailureKind.ACCOUNT_CHANGED ->
                UiText.Resource(R.string.error_write_the_active_account_changed_review_the_draft)
            CommentWriteFailureKind.BROWSER_LAUNCH ->
                UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_opened)
            CommentWriteFailureKind.CALLBACK ->
                UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_completed)
            CommentWriteFailureKind.AUTHORIZATION ->
                UiText.Resource(R.string.error_write_comment_access_wasn_t_granted)
            CommentWriteFailureKind.SESSION ->
                UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
            CommentWriteFailureKind.RATE_LIMIT ->
                UiText.Resource(R.string.error_write_reddit_asked_you_to_wait_before_posting)
            CommentWriteFailureKind.PARENT_UNAVAILABLE ->
                UiText.Resource(R.string.error_write_this_thread_can_no_longer_accept_replies)
            CommentWriteFailureKind.PERMISSION ->
                UiText.Resource(R.string.error_write_this_account_can_t_comment_in_this)
            CommentWriteFailureKind.VALIDATION -> redditReasonText
                ?: UiText.Resource(R.string.error_write_review_the_comment_text_and_try_again)
            CommentWriteFailureKind.NOT_POSTED ->
                UiText.Resource(R.string.error_write_reddit_doesn_t_show_this_comment_you)
            CommentWriteFailureKind.MEDIA_NOT_ALLOWED ->
                UiText.Resource(R.string.error_write_this_community_doesn_t_allow_this_kind)
            CommentWriteFailureKind.MEDIA_TOO_LARGE ->
                UiText.Resource(R.string.error_write_this_image_is_too_large_to_post)
            CommentWriteFailureKind.MEDIA_UNSUPPORTED ->
                UiText.Resource(R.string.error_write_this_image_type_can_t_be_posted)
            CommentWriteFailureKind.MEDIA_UNAVAILABLE ->
                UiText.Resource(R.string.error_write_this_image_can_no_longer_be_read)
            CommentWriteFailureKind.API -> redditReasonText
                ?: UiText.Resource(R.string.error_write_reddit_rejected_the_comment)
            CommentWriteFailureKind.HTTP ->
                UiText.Resource(R.string.error_write_reddit_rejected_the_comment_request)
            CommentWriteFailureKind.TRANSPORT ->
                UiText.Resource(R.string.error_write_subshell_couldn_t_reach_reddit_to_post)
            CommentWriteFailureKind.ALREADY_SUBMITTING ->
                UiText.Resource(R.string.error_write_this_comment_is_already_being_posted)
            null -> UiText.Resource(R.string.error_write_the_comment_couldn_t_be_posted)
        },
        details = if (redditReason != null) {
            state.failureDetails.copy(providerMessage = null)
        } else {
            state.failureDetails
        },
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
    // TRANSLATE14_MESSAGE_AND_REPLY_ERRORS_ARE_WHOLE_SENTENCES_V1: a translation
    // cannot inflect an English noun put into a sentence; one sentence per noun.
    fun pick(message: Int, reply: Int): UiText = UiText.Resource(if (directChat) message else reply)

    // ISSUE303_AN_UNCLEAR_IMAGE_IS_NOT_CALLED_SENT_V1 (Pixel 11, 2026-11-14): an
    // image whose own send answer was unclear (no event id) said "Media sent;
    // text may have been sent" although the image had not arrived. It says the
    // image may have been sent, and that the text was not.
    if (
        directChat &&
        state.sentHadMedia &&
        state.completionRevision > 0L &&
        state.phase == MessageReplyPhase.OUTCOME_UNKNOWN &&
        state.sentMatrixEventId.isNullOrBlank()
    ) {
        return writeErrorPresentation(
            title = UiText.Resource(R.string.error_write_image_may_have_been_sent),
            message = UiText.Resource(R.string.error_write_reddit_did_not_confirm_the_image),
            details = null,
            retryable = canRefresh,
            retryLabel = UiText.Resource(R.string.error_write_refresh),
            dismissible = false,
        ).copy(
            secondaryAction = ErrorAction(ErrorActionKind.DISMISS, UiText.Resource(R.string.error_write_checked)),
        )
    }

    // SUBSHELL_MESSAGES02E2A2_REDDIT_PARITY_DUAL_WRITE_V1: the image was
    // confirmed (it has an event id); only the text after it was not.
    if (directChat && state.sentHadMedia && state.completionRevision > 0L && !state.sentMatrixEventId.isNullOrBlank()) {
        if (state.phase == MessageReplyPhase.OUTCOME_UNKNOWN) {
            return writeErrorPresentation(
                title = UiText.Resource(R.string.error_write_media_sent_text_may_have_been_sent),
                message = UiText.Resource(R.string.error_write_the_image_gif_was_sent_but_reddit),
                details = null,
                retryable = canRefresh,
                retryLabel = UiText.Resource(R.string.error_write_refresh),
                dismissible = false,
            ).copy(
                secondaryAction = ErrorAction(ErrorActionKind.DISMISS, UiText.Resource(R.string.error_write_checked)),
            )
        }
        if (state.phase == MessageReplyPhase.FAILED) {
            return writeErrorPresentation(
                title = UiText.Resource(R.string.error_write_media_sent_text_not_sent),
                message = UiText.Resource(R.string.error_write_the_image_gif_was_sent_but_the),
                details = null,
                retryable = false,
                dismissible = true,
            )
        }
    }

    if (state.phase == MessageReplyPhase.OUTCOME_UNKNOWN) {
        return writeErrorPresentation(
            title = pick(R.string.error_write_message_may_have_been_sent, R.string.error_write_reply_may_have_been_sent),
            message = pick(R.string.error_write_may_have_received_message, R.string.error_write_may_have_received_reply),
            details = null,
            retryable = canRefresh,
            retryLabel = UiText.Resource(R.string.error_write_refresh),
            dismissible = false,
        ).copy(
            secondaryAction = ErrorAction(ErrorActionKind.DISMISS, UiText.Resource(R.string.error_write_checked)),
        )
    }

    val primaryAction = when (state.failureKind) {
        MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED ->
            ErrorAction(ErrorActionKind.SIGN_IN, UiText.Resource(R.string.error_write_grant_media_access))
        // CHAT01_CHAT_SESSION_FAILURE_STEP_V1: the Direct Chat media session is
        // Reddit's web session, not the OAuth grant "Reconnect" renews, so no
        // button that cannot help.
        MessageReplyFailureKind.AUTHORIZATION_REQUIRED,
        -> ErrorAction(ErrorActionKind.SIGN_IN, UiText.Resource(R.string.error_write_reconnect))
        // ISSUE303B_AN_EXPIRED_WEB_SIGN_IN_OFFERS_SIGN_IN_AGAIN_V1: signing in
        // again in the app's own login window renews Reddit's web sign-in (seen
        // on the Pixel 11), so here the button helps.
        MessageReplyFailureKind.CHAT_WEB_SIGN_IN_EXPIRED ->
            ErrorAction(ErrorActionKind.SIGN_IN, UiText.Resource(R.string.chat_web_sign_in_again))
        else -> null
    }

    val presentation = writeErrorPresentation(
        title = if (directChat) UiText.Resource(R.string.error_write_couldn_t_send_message) else UiText.Resource(R.string.error_write_couldn_t_send_reply),
        message = when (state.failureKind) {
            MessageReplyFailureKind.VALIDATION ->
                pick(R.string.error_write_review_message, R.string.error_write_review_reply)
            MessageReplyFailureKind.CHAT_SESSION_REQUIRED ->
                UiText.Resource(R.string.error_write_subshell_couldn_t_open_reddit_s_chat)
            MessageReplyFailureKind.CHAT_WEB_SIGN_IN_EXPIRED ->
                UiText.Resource(R.string.chat_web_sign_in_expired_images)
            MessageReplyFailureKind.CHAT_ROOM_UNAVAILABLE ->
                UiText.Resource(R.string.error_write_subshell_couldn_t_match_this_conversation_to)
            MessageReplyFailureKind.MEDIA_CAPTION_UNSUPPORTED ->
                UiText.Resource(R.string.error_write_send_direct_chat_text_and_media_separately)
            MessageReplyFailureKind.MEDIA_UNSUPPORTED ->
                UiText.Resource(R.string.error_write_choose_a_jpeg_png_webp_or_gif)
            MessageReplyFailureKind.MEDIA_TOO_LARGE ->
                UiText.Resource(R.string.error_write_image_gif_must_be_20_mb_or)
            MessageReplyFailureKind.MEDIA_UNAVAILABLE ->
                UiText.Resource(R.string.error_write_this_image_gif_can_no_longer_be)
            MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED ->
                UiText.Resource(R.string.error_write_reddit_media_access_is_required_to_send)
            MessageReplyFailureKind.AUTHORIZATION_REQUIRED ->
                pick(R.string.error_write_sign_in_before_message, R.string.error_write_sign_in_before_reply)
            MessageReplyFailureKind.RATE_LIMIT ->
                pick(R.string.error_write_wait_before_message, R.string.error_write_wait_before_reply)
            MessageReplyFailureKind.API ->
                pick(R.string.error_write_rejected_message, R.string.error_write_rejected_reply)
            MessageReplyFailureKind.HTTP ->
                pick(R.string.error_write_rejected_message_request, R.string.error_write_rejected_reply_request)
            MessageReplyFailureKind.TRANSPORT ->
                pick(R.string.error_write_could_not_reach_message, R.string.error_write_could_not_reach_reply)
            MessageReplyFailureKind.SESSION ->
                UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
            // LOWUI4 (OCB-171): not reached; MessageReplyCoordinator turns this
            // kind into phase OUTCOME_UNKNOWN, answered above. Kept for an
            // exhaustive when.
            MessageReplyFailureKind.OUTCOME_UNKNOWN ->
                UiText.Resource(R.string.error_write_reddit_returned_a_send_result_subshell_couldn)
            MessageReplyFailureKind.UNAVAILABLE ->
                pick(R.string.error_write_message_sending_unavailable, R.string.error_write_reply_sending_unavailable)
            null ->
                pick(R.string.error_write_message_not_sent, R.string.error_write_reply_not_sent)
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
            title = UiText.Resource(R.string.error_write_post_may_have_been_published),
            message = UiText.Resource(R.string.error_write_reddit_may_have_received_this_post_check),
            details = state.failureDetails,
            retryable = true,
            retryLabel = UiText.Resource(R.string.error_write_check_again),
        )
    }
    // COMPOSERERR01_REDDITS_REASON_IS_SHOWN_V1: a refusal the app has no words
    // for (post guidance, Automations) reads as Reddit wrote it, once: not
    // again among the details.
    val redditReason = state.failureDetails?.providerMessage?.value
        ?.takeIf { state.failureKind == TextPostWriteFailureKind.API }
    return writeErrorPresentation(
        title = UiText.Resource(R.string.error_write_couldn_t_publish_post),
        message = when (state.failureKind) {
            TextPostWriteFailureKind.CONFIGURATION ->
                UiText.Resource(R.string.error_write_post_publishing_isn_t_configured_for_this)
            TextPostWriteFailureKind.ACCOUNT_CHANGED ->
                UiText.Resource(R.string.error_write_the_active_account_changed_review_the_draft)
            TextPostWriteFailureKind.BROWSER_LAUNCH ->
                UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_opened)
            TextPostWriteFailureKind.CALLBACK ->
                UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_completed)
            TextPostWriteFailureKind.AUTHORIZATION ->
                UiText.Resource(R.string.error_write_post_access_wasn_t_granted)
            TextPostWriteFailureKind.SESSION ->
                UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
            TextPostWriteFailureKind.RATE_LIMIT ->
                UiText.Resource(R.string.error_write_reddit_asked_you_to_wait_before_posting)
            TextPostWriteFailureKind.COMMUNITY_UNAVAILABLE ->
                UiText.Resource(R.string.error_write_this_community_is_unavailable)
            TextPostWriteFailureKind.PERMISSION ->
                UiText.Resource(R.string.error_write_this_account_can_t_post_in_this)
            TextPostWriteFailureKind.SUBMISSION_RESTRICTED ->
                UiText.Resource(R.string.error_write_this_community_isn_t_accepting_this_post)
            TextPostWriteFailureKind.CHALLENGE_REQUIRED ->
                UiText.Resource(R.string.error_write_reddit_requires_an_additional_verification_step)
            TextPostWriteFailureKind.VALIDATION ->
                UiText.Resource(R.string.error_write_review_the_highlighted_field_before_publishing)
            TextPostWriteFailureKind.API -> redditReason
                ?.let { reason -> UiText.Resource(R.string.error_write_reddit_rejected_the_post_saying, reason) }
                ?: UiText.Resource(R.string.error_write_reddit_rejected_the_post)
            TextPostWriteFailureKind.HTTP ->
                UiText.Resource(R.string.error_write_reddit_rejected_the_publish_request)
            TextPostWriteFailureKind.TRANSPORT ->
                UiText.Resource(R.string.error_write_subshell_couldn_t_reach_reddit_to_publish)
            TextPostWriteFailureKind.ALREADY_SUBMITTING ->
                UiText.Resource(R.string.error_write_this_post_is_already_being_published)
            TextPostWriteFailureKind.NOT_POSTED ->
                UiText.Resource(R.string.error_write_reddit_doesn_t_show_this_post_you)
            TextPostWriteFailureKind.EARLIER_POST_UNCONFIRMED ->
                UiText.Resource(R.string.error_write_reddit_may_have_received_an_earlier_post)
            null -> UiText.Resource(R.string.error_write_the_post_couldn_t_be_published)
        },
        details = if (redditReason != null) {
            state.failureDetails.copy(providerMessage = null)
        } else {
            state.failureDetails
        },
        retryable = state.retryable &&
            state.failureKind != TextPostWriteFailureKind.ALREADY_SUBMITTING,
        // COMPOSER02_SUBMIT_ATTEMPT_PER_ACCOUNT_V1: the action reads the earlier
        // post back; it never sends this one.
        retryLabel = if (
            state.failureKind == TextPostWriteFailureKind.EARLIER_POST_UNCONFIRMED
        ) {
            UiText.Resource(R.string.error_write_check_again)
        } else {
            UiText.Resource(R.string.error_write_retry)
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
        UiText.Resource(R.string.error_write_edit_not_confirmed)
    } else {
        UiText.Resource(R.string.error_write_couldn_t_edit_comment)
    },
    message = when (state.failureKind) {
        CommentEditFailureKind.CONFIGURATION ->
            UiText.Resource(R.string.error_write_comment_editing_isn_t_configured_for_this)
        CommentEditFailureKind.ACCOUNT_CHANGED ->
            UiText.Resource(R.string.error_write_the_active_account_changed_before_the_comment)
        CommentEditFailureKind.NOT_OWNER ->
            UiText.Resource(R.string.error_write_only_comments_authored_by_the_active_account)
        CommentEditFailureKind.BROWSER_LAUNCH ->
            UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_opened)
        CommentEditFailureKind.CALLBACK ->
            UiText.Resource(R.string.error_write_reddit_authorization_didn_t_complete)
        CommentEditFailureKind.AUTHORIZATION ->
            UiText.Resource(R.string.error_write_reddit_didn_t_grant_the_permission_required_2)
        CommentEditFailureKind.SESSION ->
            UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
        CommentEditFailureKind.RATE_LIMIT ->
            UiText.Resource(R.string.error_write_reddit_rate_limited_the_edit_request_try)
        CommentEditFailureKind.VALIDATION ->
            UiText.Resource(R.string.error_write_reddit_rejected_the_edited_comment_text)
        CommentEditFailureKind.API ->
            UiText.Resource(R.string.error_write_reddit_rejected_the_comment_edit)
        CommentEditFailureKind.HTTP ->
            UiText.Resource(R.string.error_write_reddit_rejected_the_edit_request)
        CommentEditFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_write_subshell_couldn_t_reach_reddit_the_comment)
        CommentEditFailureKind.ALREADY_EDITING ->
            UiText.Resource(R.string.error_write_this_comment_is_already_being_edited)
        CommentEditFailureKind.OUTCOME_UNKNOWN ->
            UiText.Resource(R.string.error_write_reddit_may_have_saved_the_edit_but)
        CommentEditFailureKind.NOT_SAVED ->
            UiText.Resource(R.string.error_write_reddit_didn_t_save_the_edit_your)
        null -> UiText.Resource(R.string.error_write_the_comment_couldn_t_be_edited)
    },
    details = state.failureDetails,
    retryable = state.failureKind == CommentEditFailureKind.OUTCOME_UNKNOWN || (
        state.retryable &&
            state.failureKind != CommentEditFailureKind.ALREADY_EDITING
        ),
    retryLabel = if (state.failureKind == CommentEditFailureKind.OUTCOME_UNKNOWN) UiText.Resource(R.string.error_write_check_again) else UiText.Resource(R.string.error_write_retry),
    dismissible = true,
)

internal fun postEditErrorPresentation(
    state: PostEditUiState,
): ErrorPresentation = writeErrorPresentation(
    // H2_POST_EDIT_OUTCOME_UNKNOWN_V1: an unconfirmed edit offers Check
    // again (a read), never a blind re-send.
    title = if (state.failureKind == PostEditFailureKind.OUTCOME_UNKNOWN) {
        UiText.Resource(R.string.error_write_edit_not_confirmed)
    } else {
        UiText.Resource(R.string.error_write_couldn_t_edit_post)
    },
    message = when (state.failureKind) {
        PostEditFailureKind.CONFIGURATION ->
            UiText.Resource(R.string.error_write_post_editing_isn_t_configured_for_this)
        PostEditFailureKind.ACCOUNT_CHANGED ->
            UiText.Resource(R.string.error_write_the_active_account_changed_before_the_post)
        PostEditFailureKind.NOT_OWNER ->
            UiText.Resource(R.string.error_write_only_self_posts_authored_by_the_active)
        PostEditFailureKind.BROWSER_LAUNCH ->
            UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_opened)
        PostEditFailureKind.CALLBACK ->
            UiText.Resource(R.string.error_write_reddit_authorization_didn_t_complete)
        PostEditFailureKind.AUTHORIZATION ->
            UiText.Resource(R.string.error_write_reddit_didn_t_grant_the_permission_required_3)
        PostEditFailureKind.SESSION ->
            UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
        PostEditFailureKind.RATE_LIMIT ->
            UiText.Resource(R.string.error_write_reddit_rate_limited_the_edit_request_try)
        PostEditFailureKind.VALIDATION ->
            UiText.Resource(R.string.error_write_reddit_rejected_the_edited_post_body)
        PostEditFailureKind.API ->
            UiText.Resource(R.string.error_write_reddit_rejected_the_post_edit)
        PostEditFailureKind.HTTP ->
            UiText.Resource(R.string.error_write_reddit_rejected_the_edit_request)
        PostEditFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_write_subshell_couldn_t_reach_reddit_the_post)
        PostEditFailureKind.ALREADY_EDITING ->
            UiText.Resource(R.string.error_write_this_post_is_already_being_edited)
        PostEditFailureKind.OUTCOME_UNKNOWN ->
            UiText.Resource(R.string.error_write_reddit_may_have_saved_the_edit_but)
        PostEditFailureKind.NOT_SAVED ->
            UiText.Resource(R.string.error_write_reddit_didn_t_save_the_edit_your)
        null -> UiText.Resource(R.string.error_write_the_post_couldn_t_be_edited)
    },
    details = state.failureDetails,
    retryable = state.failureKind == PostEditFailureKind.OUTCOME_UNKNOWN || (
        state.retryable &&
            state.failureKind != PostEditFailureKind.ALREADY_EDITING
        ),
    retryLabel = if (state.failureKind == PostEditFailureKind.OUTCOME_UNKNOWN) UiText.Resource(R.string.error_write_check_again) else UiText.Resource(R.string.error_write_retry),
)

internal fun commentDeleteErrorPresentation(
    state: CommentDeleteUiState,
): ErrorPresentation {
    val outcomeUnconfirmed =
        state.failureKind == CommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED
    return writeErrorPresentation(
        title = if (outcomeUnconfirmed) {
            UiText.Resource(R.string.error_write_comment_deletion_not_confirmed)
        } else {
            UiText.Resource(R.string.error_write_couldn_t_delete_comment)
        },
        message = when (state.failureKind) {
            CommentDeleteFailureKind.CONFIGURATION ->
                UiText.Resource(R.string.error_write_comment_deletion_isn_t_configured_for_this)
            CommentDeleteFailureKind.ACCOUNT_CHANGED ->
                UiText.Resource(R.string.error_write_the_active_account_changed_before_the_comment_2)
            CommentDeleteFailureKind.NOT_OWNER ->
                UiText.Resource(R.string.error_write_only_comments_authored_by_the_active_account_2)
            CommentDeleteFailureKind.BROWSER_LAUNCH ->
                UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_opened)
            CommentDeleteFailureKind.CALLBACK ->
                UiText.Resource(R.string.error_write_reddit_authorization_didn_t_complete)
            CommentDeleteFailureKind.AUTHORIZATION ->
                UiText.Resource(R.string.error_write_reddit_didn_t_grant_the_permission_required_4)
            CommentDeleteFailureKind.SESSION ->
                UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
            CommentDeleteFailureKind.RATE_LIMIT ->
                UiText.Resource(R.string.error_write_reddit_rate_limited_the_delete_request_try)
            CommentDeleteFailureKind.HTTP ->
                UiText.Resource(R.string.error_write_reddit_rejected_the_delete_request)
            CommentDeleteFailureKind.TRANSPORT ->
                UiText.Resource(R.string.error_write_subshell_couldn_t_reach_reddit_the_comment_2)
            CommentDeleteFailureKind.PROTOCOL ->
                UiText.Resource(R.string.error_write_reddit_returned_a_response_subshell_couldn_t)
            CommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED ->
                // LOWUI_DELETE_NOT_CONFIRMED_SAYS_MAY_V1 (OCB-172): a timeout or a
                // dropped connection is not an accepted request.
                UiText.Resource(R.string.error_write_reddit_may_have_received_the_delete_request)
            CommentDeleteFailureKind.ALREADY_DELETING ->
                UiText.Resource(R.string.error_write_this_comment_is_already_being_deleted)
            null -> UiText.Resource(R.string.error_write_the_comment_couldn_t_be_deleted)
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
        UiText.Resource(R.string.error_write_deletion_not_confirmed)
    } else {
        UiText.Resource(R.string.error_write_couldn_t_delete_post)
    },
    message = when (state.failureKind) {
        PostDeleteFailureKind.CONFIGURATION ->
            UiText.Resource(R.string.error_write_post_deletion_isn_t_configured_for_this)
        PostDeleteFailureKind.ACCOUNT_CHANGED ->
            UiText.Resource(R.string.error_write_the_active_account_changed_reopen_the_post)
        PostDeleteFailureKind.NOT_OWNER ->
            UiText.Resource(R.string.error_write_this_post_isn_t_owned_by_the)
        PostDeleteFailureKind.BROWSER_LAUNCH ->
            UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_opened)
        PostDeleteFailureKind.CALLBACK ->
            UiText.Resource(R.string.error_write_reddit_authorization_didn_t_complete)
        PostDeleteFailureKind.AUTHORIZATION ->
            UiText.Resource(R.string.error_write_reddit_didn_t_grant_the_permission_required_5)
        PostDeleteFailureKind.SESSION ->
            UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
        PostDeleteFailureKind.RATE_LIMIT ->
            UiText.Resource(R.string.error_write_reddit_rate_limited_the_delete_request_try)
        PostDeleteFailureKind.HTTP ->
            UiText.Resource(R.string.error_write_reddit_rejected_the_delete_request)
        PostDeleteFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_write_subshell_couldn_t_reach_reddit_the_post_2)
        PostDeleteFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_write_reddit_returned_a_response_subshell_couldn_t)
        PostDeleteFailureKind.ALREADY_DELETING ->
            UiText.Resource(R.string.error_write_a_post_deletion_is_already_in_progress)
        PostDeleteFailureKind.OUTCOME_UNKNOWN ->
            UiText.Resource(R.string.error_write_reddit_may_have_deleted_the_post_but)
        PostDeleteFailureKind.NOT_DELETED ->
            UiText.Resource(R.string.error_write_reddit_didn_t_delete_the_post_you)
        null -> UiText.Resource(R.string.error_write_the_post_couldn_t_be_deleted)
    },
    details = state.failureDetails,
    retryable = state.failureKind == PostDeleteFailureKind.OUTCOME_UNKNOWN || (
        state.retryable &&
            state.failureKind != PostDeleteFailureKind.ALREADY_DELETING
        ),
    retryLabel = if (state.failureKind == PostDeleteFailureKind.OUTCOME_UNKNOWN) UiText.Resource(R.string.error_write_check_again) else UiText.Resource(R.string.error_write_retry),
)

internal fun postManageErrorPresentation(
    state: PostManageUiState,
): ErrorPresentation = writeErrorPresentation(
    title = if (state.operation == null) {
        UiText.Resource(R.string.error_write_couldn_t_load_post_controls)
    } else {
        UiText.Resource(R.string.error_write_couldn_t_update_post)
    },
    message = when (state.failureKind) {
        PostManageFailureKind.CONFIGURATION ->
            UiText.Resource(R.string.error_write_post_management_is_unavailable_for_this_account)
        PostManageFailureKind.ACCOUNT_CHANGED ->
            UiText.Resource(R.string.error_write_the_active_account_changed_reopen_the_post)
        PostManageFailureKind.NOT_OWNER ->
            UiText.Resource(R.string.error_write_this_post_isn_t_owned_by_the)
        PostManageFailureKind.BROWSER_LAUNCH ->
            UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_opened)
        PostManageFailureKind.CALLBACK ->
            UiText.Resource(R.string.error_write_reddit_authorization_didn_t_complete)
        PostManageFailureKind.AUTHORIZATION ->
            UiText.Resource(R.string.error_write_reddit_didn_t_grant_the_permission_required_6)
        PostManageFailureKind.SESSION ->
            UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
        PostManageFailureKind.RATE_LIMIT ->
            UiText.Resource(R.string.error_write_reddit_rate_limited_the_request_try_again)
        PostManageFailureKind.HTTP ->
            UiText.Resource(R.string.error_write_reddit_rejected_the_post_management_request)
        PostManageFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_write_subshell_couldn_t_reach_reddit_for_this_2)
        PostManageFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_write_reddit_returned_a_response_subshell_couldn_t_2)
        PostManageFailureKind.ALREADY_MUTATING ->
            UiText.Resource(R.string.error_write_another_post_change_is_already_in_progress)
        null -> UiText.Resource(R.string.error_write_post_management_couldn_t_be_completed)
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
        CommunityMembershipAction.JOIN -> UiText.Resource(R.string.error_write_couldn_t_join_community)
        CommunityMembershipAction.LEAVE -> UiText.Resource(R.string.error_write_couldn_t_leave_community)
        null -> UiText.Resource(R.string.error_write_couldn_t_change_community_membership)
    }
    return writeErrorPresentation(
        title = if (outcomeUnknown || notConfirmed) {
            UiText.Resource(R.string.error_write_membership_change_not_confirmed)
        } else {
            failureTitle
        },
        message = when (state.failureKind) {
            CommunityMembershipFailureKind.VALIDATION ->
                UiText.Resource(R.string.error_write_this_community_membership_request_is_invalid)
            CommunityMembershipFailureKind.ACCOUNT_CHANGED ->
                UiText.Resource(R.string.error_write_the_active_reddit_account_changed_try_again)
            CommunityMembershipFailureKind.CONFIGURATION ->
                UiText.Resource(R.string.error_write_community_membership_changes_are_unavailable_for_this)
            CommunityMembershipFailureKind.AUTHORIZATION,
            CommunityMembershipFailureKind.CALLBACK,
            CommunityMembershipFailureKind.BROWSER_LAUNCH,
            -> UiText.Resource(R.string.error_write_reddit_authorization_didn_t_complete)
            CommunityMembershipFailureKind.SESSION ->
                UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
            CommunityMembershipFailureKind.HTTP ->
                if (state.failureDetails?.httpStatusCode == 429) {
                    UiText.Resource(R.string.error_write_reddit_is_rate_limiting_this_action_try)
                } else {
                    UiText.Resource(R.string.error_write_reddit_rejected_the_membership_change)
                }
            CommunityMembershipFailureKind.PROTOCOL ->
                UiText.Resource(R.string.error_write_reddit_returned_a_response_subshell_couldn_t_2)
            CommunityMembershipFailureKind.OUTCOME_UNKNOWN ->
                UiText.Resource(R.string.error_write_reddit_may_have_received_this_membership_change)
            CommunityMembershipFailureKind.NOT_CONFIRMED ->
                // TRANSLATE03: the message is kept in state as text (slice 10 moves state text).
                UiText.Plain(state.message ?: NOT_CONFIRMED_MEMBERSHIP_MESSAGE)
            null -> UiText.Resource(R.string.error_write_the_membership_change_couldn_t_be_completed)
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
            UiText.Resource(R.string.error_write_feed_creation_not_confirmed)
        } else {
            UiText.Resource(R.string.error_write_couldn_t_create_custom_feed)
        },
        message = when (state.createFailureKind) {
            CustomFeedCreateFailureKind.CONFIGURATION ->
                UiText.Resource(R.string.error_write_custom_feed_creation_is_unavailable_for_this)
            CustomFeedCreateFailureKind.ACCOUNT_CHANGED ->
                UiText.Resource(R.string.error_write_the_active_reddit_account_changed)
            CustomFeedCreateFailureKind.AUTHORIZATION,
            CustomFeedCreateFailureKind.CALLBACK,
            -> UiText.Resource(R.string.error_write_reddit_authorization_didn_t_complete)
            CustomFeedCreateFailureKind.SESSION ->
                UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
            CustomFeedCreateFailureKind.NAME_CONFLICT ->
                UiText.Resource(R.string.error_write_a_custom_feed_with_that_name_already)
            CustomFeedCreateFailureKind.HTTP ->
                UiText.Resource(R.string.error_write_reddit_rejected_the_create_request)
            CustomFeedCreateFailureKind.OUTCOME_UNKNOWN ->
                UiText.Resource(R.string.error_write_reddit_may_have_received_this_request_subshell)
            CustomFeedCreateFailureKind.PROTOCOL ->
                UiText.Resource(R.string.error_write_reddit_returned_a_response_subshell_couldn_t_2)
            CustomFeedCreateFailureKind.VALIDATION ->
                UiText.Resource(R.string.error_write_choose_a_valid_name_and_at_least)
            null -> UiText.Resource(R.string.error_write_the_custom_feed_couldn_t_be_created)
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
            outcomeUnknown && deleting -> UiText.Resource(R.string.error_write_feed_deletion_not_confirmed)
            outcomeUnknown -> UiText.Resource(R.string.error_write_feed_change_not_confirmed)
            deleting -> UiText.Resource(R.string.error_write_couldn_t_delete_custom_feed)
            else -> UiText.Resource(R.string.error_write_couldn_t_update_custom_feed)
        },
        message = when (state.manageFailureKind) {
            CustomFeedManageFailureKind.CONFIGURATION ->
                UiText.Resource(R.string.error_write_custom_feed_management_is_unavailable_for_this)
            CustomFeedManageFailureKind.ACCOUNT_CHANGED ->
                UiText.Resource(R.string.error_write_the_active_reddit_account_changed)
            CustomFeedManageFailureKind.AUTHORIZATION,
            CustomFeedManageFailureKind.CALLBACK,
            -> UiText.Resource(R.string.error_write_reddit_authorization_didn_t_complete)
            CustomFeedManageFailureKind.SESSION ->
                UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
            CustomFeedManageFailureKind.HTTP ->
                UiText.Resource(R.string.error_write_reddit_rejected_the_custom_feed_request)
            CustomFeedManageFailureKind.OUTCOME_UNKNOWN ->
                UiText.Resource(R.string.error_write_reddit_may_have_received_this_request_subshell_2)
            CustomFeedManageFailureKind.PROTOCOL ->
                UiText.Resource(R.string.error_write_reddit_returned_a_response_subshell_couldn_t_2)
            CustomFeedManageFailureKind.VALIDATION ->
                UiText.Resource(R.string.error_write_the_custom_feed_details_are_invalid)
            null -> UiText.Resource(R.string.error_write_the_custom_feed_change_couldn_t_be)
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
    title = UiText.Resource(R.string.error_write_couldn_t_update_followed_post),
    message = when (state.failureKind) {
        PostFollowFailureKind.CONFIGURATION ->
            UiText.Resource(R.string.error_write_following_posts_isn_t_configured_for_this)
        PostFollowFailureKind.ACCOUNT_CHANGED ->
            UiText.Resource(R.string.error_write_the_active_account_changed_before_the_followed)
        PostFollowFailureKind.BROWSER_LAUNCH ->
            UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_opened)
        PostFollowFailureKind.CALLBACK ->
            UiText.Resource(R.string.error_write_reddit_authorization_didn_t_complete)
        PostFollowFailureKind.AUTHORIZATION ->
            UiText.Resource(R.string.error_write_reddit_didn_t_grant_post_follow_access)
        PostFollowFailureKind.SESSION ->
            UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
        PostFollowFailureKind.VALIDATION ->
            UiText.Resource(R.string.error_write_this_post_can_t_be_followed)
        PostFollowFailureKind.HTTP ->
            UiText.Resource(R.string.error_write_reddit_rejected_the_post_follow_request)
        PostFollowFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_write_subshell_couldn_t_reach_reddit_to_update)
        PostFollowFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_write_reddit_returned_an_unexpected_response_to_the)
        null ->
            UiText.Resource(R.string.error_write_the_followed_state_couldn_t_be_updated)
    },
    primaryAction = if (state.retryable) {
        ErrorAction(
            ErrorActionKind.RETRY,
            UiText.Resource(R.string.error_write_retry),
        )
    } else {
        null
    },
    secondaryAction =
        ErrorAction(
            ErrorActionKind.DISMISS,
            UiText.Resource(R.string.error_write_dismiss),
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
            UiText.Resource(R.string.error_write_couldn_t_update_saved_comment)
        } else {
            UiText.Resource(R.string.error_write_couldn_t_update_saved_post)
        },
        message = when (state.failureKind) {
            PostSaveFailureKind.CONFIGURATION ->
                if (commentTarget) {
                    UiText.Resource(R.string.error_write_saving_comments_isn_t_configured_for_this)
                } else {
                    UiText.Resource(R.string.error_write_saving_posts_isn_t_configured_for_this)
                }
            PostSaveFailureKind.ACCOUNT_CHANGED ->
                UiText.Resource(R.string.error_write_the_active_account_changed_before_the_saved)
            PostSaveFailureKind.BROWSER_LAUNCH ->
                UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_opened)
            PostSaveFailureKind.CALLBACK ->
                UiText.Resource(R.string.error_write_reddit_authorization_didn_t_complete)
            PostSaveFailureKind.AUTHORIZATION ->
                UiText.Resource(R.string.error_write_reddit_didn_t_grant_save_access)
            PostSaveFailureKind.SESSION ->
                UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
            PostSaveFailureKind.VALIDATION ->
                if (commentTarget) {
                    UiText.Resource(R.string.error_write_this_comment_can_t_be_saved)
                } else {
                    UiText.Resource(R.string.error_write_this_post_can_t_be_saved)
                }
            PostSaveFailureKind.HTTP ->
                UiText.Resource(R.string.error_write_reddit_rejected_the_save_request)
            PostSaveFailureKind.TRANSPORT ->
                UiText.Resource(R.string.error_write_subshell_couldn_t_reach_reddit_to_update_2)
            PostSaveFailureKind.PROTOCOL ->
                UiText.Resource(R.string.error_write_reddit_returned_an_unexpected_response_to_the_2)
            null -> UiText.Resource(R.string.error_write_the_saved_state_couldn_t_be_updated)
        },
        primaryAction = if (state.retryable) {
            ErrorAction(ErrorActionKind.RETRY, UiText.Resource(R.string.error_write_retry))
        } else {
            null
        },
        secondaryAction = ErrorAction(ErrorActionKind.DISMISS, UiText.Resource(R.string.error_write_dismiss)),
    ).withFailureDetails(state.failureDetails)
}

internal fun postVoteErrorPresentation(
    state: PostVoteUiState,
): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = UiText.Resource(R.string.error_write_couldn_t_update_vote),
    message = when (state.failureKind) {
        PostVoteFailureKind.CONFIGURATION ->
            UiText.Resource(R.string.error_write_voting_isn_t_configured_for_this_account)
        PostVoteFailureKind.ACCOUNT_CHANGED ->
            UiText.Resource(R.string.error_write_the_active_account_changed_before_the_vote)
        PostVoteFailureKind.BROWSER_LAUNCH ->
            UiText.Resource(R.string.error_write_reddit_authorization_couldn_t_be_opened)
        PostVoteFailureKind.CALLBACK ->
            UiText.Resource(R.string.error_write_reddit_authorization_didn_t_complete)
        PostVoteFailureKind.AUTHORIZATION ->
            UiText.Resource(R.string.error_write_reddit_didn_t_grant_vote_access)
        PostVoteFailureKind.SESSION ->
            UiText.Resource(R.string.error_write_the_reddit_sign_in_for_this_account)
        PostVoteFailureKind.VALIDATION ->
            UiText.Resource(R.string.error_write_this_item_can_t_be_voted_on)
        PostVoteFailureKind.HTTP ->
            UiText.Resource(R.string.error_write_reddit_rejected_the_vote_request)
        PostVoteFailureKind.TRANSPORT ->
            UiText.Resource(R.string.error_write_subshell_couldn_t_reach_reddit_to_update_3)
        PostVoteFailureKind.PROTOCOL ->
            UiText.Resource(R.string.error_write_reddit_returned_an_unexpected_response_to_the_3)
        null -> UiText.Resource(R.string.error_write_the_vote_couldn_t_be_updated)
    },
    primaryAction = if (state.retryable) {
        ErrorAction(ErrorActionKind.RETRY, UiText.Resource(R.string.error_write_retry))
    } else {
        null
    },
    secondaryAction = ErrorAction(ErrorActionKind.DISMISS, UiText.Resource(R.string.error_write_dismiss)),
).withFailureDetails(state.failureDetails)

private fun writeErrorPresentation(
    title: UiText,
    message: UiText,
    details: FailureDetails?,
    retryable: Boolean,
    retryLabel: UiText = UiText.Resource(R.string.error_write_retry),
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
        ErrorAction(ErrorActionKind.DISMISS, UiText.Resource(R.string.error_write_dismiss))
    } else {
        null
    },
).withFailureDetails(details)
