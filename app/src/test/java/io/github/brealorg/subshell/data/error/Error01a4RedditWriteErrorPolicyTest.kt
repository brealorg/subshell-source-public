package io.github.brealorg.subshell.data.error

import io.github.brealorg.subshell.data.account.CommentDeleteFailureKind
import io.github.brealorg.subshell.data.account.CommentDeletePhase
import io.github.brealorg.subshell.data.account.CommentDeleteUiState
import io.github.brealorg.subshell.data.account.CommentEditFailureKind
import io.github.brealorg.subshell.data.account.CommentEditPhase
import io.github.brealorg.subshell.data.account.CommentEditUiState
import io.github.brealorg.subshell.data.account.CommentWriteFailureKind
import io.github.brealorg.subshell.data.account.CommentWritePhase
import io.github.brealorg.subshell.data.account.CommentWriteUiState
import io.github.brealorg.subshell.data.account.CommunityMembershipAction
import io.github.brealorg.subshell.data.account.CommunityMembershipFailureKind
import io.github.brealorg.subshell.data.account.CommunityMembershipPhase
import io.github.brealorg.subshell.data.account.CommunityMembershipUiState
import io.github.brealorg.subshell.data.account.CustomFeedCreateFailureKind
import io.github.brealorg.subshell.data.account.CustomFeedCreatePhase
import io.github.brealorg.subshell.data.account.CustomFeedsUiState
import io.github.brealorg.subshell.data.account.NOT_CHECKED_MEMBERSHIP_MESSAGE
import io.github.brealorg.subshell.data.account.NOT_CONFIRMED_MEMBERSHIP_MESSAGE
import io.github.brealorg.subshell.data.account.PostDeleteFailureKind
import io.github.brealorg.subshell.data.account.PostDeletePhase
import io.github.brealorg.subshell.data.account.PostDeleteUiState
import io.github.brealorg.subshell.data.account.PostManageFailureKind
import io.github.brealorg.subshell.data.account.PostManagePhase
import io.github.brealorg.subshell.data.account.PostManageUiState
import io.github.brealorg.subshell.data.account.TextPostWriteFailureKind
import io.github.brealorg.subshell.data.account.TextPostWritePhase
import io.github.brealorg.subshell.data.account.TextPostWriteUiState
import io.github.brealorg.subshell.data.network.NetworkTransportFailureKind
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.commentDeleteErrorPresentation
import io.github.brealorg.subshell.ui.error.commentEditErrorPresentation
import io.github.brealorg.subshell.ui.error.commentWriteErrorPresentation
import io.github.brealorg.subshell.ui.error.communityMembershipErrorPresentation
import io.github.brealorg.subshell.ui.error.customFeedCreateErrorPresentation
import io.github.brealorg.subshell.ui.error.postDeleteErrorPresentation
import io.github.brealorg.subshell.ui.error.postManageErrorPresentation
import io.github.brealorg.subshell.ui.error.textPostWriteErrorPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Error01a4RedditWriteErrorPolicyTest {
    @Test
    fun commentTransportFailureKeepsNetworkCauseAndRetry() {
        val presentation = commentWriteErrorPresentation(
            CommentWriteUiState(
                phase = CommentWritePhase.FAILED,
                accountStableId = "reddit:u/test",
                postId = "post",
                targetStableKey = "target",
                failureKind = CommentWriteFailureKind.TRANSPORT,
                retryable = true,
                failureDetails = FailureDetails(
                    retryable = true,
                    transportKind = NetworkTransportFailureKind.TIMEOUT,
                ),
            ),
        )

        assertEquals("Couldn't post comment", presentation.title)
        assertEquals("Network: Timeout", presentation.technicalDetail)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
    }

    @Test
    fun composerFailuresPreserveDismissOwnership() {
        val comment = commentWriteErrorPresentation(
            CommentWriteUiState(
                phase = CommentWritePhase.FAILED,
                failureKind = CommentWriteFailureKind.HTTP,
            ),
        )
        val post = textPostWriteErrorPresentation(
            TextPostWriteUiState(
                phase = TextPostWritePhase.FAILED,
                failureKind = TextPostWriteFailureKind.HTTP,
            ),
        )
        val edit = commentEditErrorPresentation(
            CommentEditUiState(
                phase = CommentEditPhase.FAILED,
                failureKind = CommentEditFailureKind.API,
            ),
        )

        assertEquals(ErrorActionKind.DISMISS, comment.secondaryAction?.kind)
        assertEquals(ErrorActionKind.DISMISS, post.secondaryAction?.kind)
        assertEquals(ErrorActionKind.DISMISS, edit.secondaryAction?.kind)
    }

    @Test
    fun commentOutcomeUnknownNeverOffersBlindRetry() {
        val presentation = commentWriteErrorPresentation(
            CommentWriteUiState(
                phase = CommentWritePhase.OUTCOME_UNKNOWN,
                accountStableId = "reddit:u/test",
                postId = "post",
                targetStableKey = "target",
                failureKind = CommentWriteFailureKind.API,
                retryable = true,
                failureDetails = FailureDetails(
                    retryable = true,
                    transportKind = NetworkTransportFailureKind.IO,
                ),
            ),
        )

        assertEquals("Comment may have been posted", presentation.title)
        assertTrue(presentation.message.contains("Check the thread"))
        // COMPOSER01_COMMENT_OUTCOME_READ_BACK_V1: the predecessor offered no
        // action because Retry re-sent the comment; the coordinator now reads
        // the thread back instead of sending the same comment twice.
        assertEquals("Check again", presentation.primaryAction?.label)
    }

    @Test
    fun postOutcomeUnknownUsesReconciliationCopyInsteadOfRepublishCopy() {
        val presentation = textPostWriteErrorPresentation(
            TextPostWriteUiState(
                phase = TextPostWritePhase.OUTCOME_UNKNOWN,
                accountStableId = "reddit:u/test",
                requestKey = "draft",
                failureKind = TextPostWriteFailureKind.API,
                failureDetails = FailureDetails(
                    retryable = false,
                    transportKind = NetworkTransportFailureKind.TIMEOUT,
                ),
            ),
        )

        assertEquals("Post may have been published", presentation.title)
        assertEquals("Check again", presentation.primaryAction?.label)
        assertTrue(presentation.message.contains("Check your profile"))
    }

    @Test
    fun providerMessageIsSanitizedBeforeCommentEditPresentation() {
        val details = FailureDetails(retryable = false).withProviderDetails(
            code = "BAD_COMMENT",
            message = "access_token=secret-token invalid body",
        )
        val presentation = commentEditErrorPresentation(
            CommentEditUiState(
                phase = CommentEditPhase.FAILED,
                accountStableId = "reddit:u/test",
                postId = "post",
                commentFullname = "t1_abc",
                failureKind = CommentEditFailureKind.API,
                failureDetails = details,
            ),
        )
        val visible = listOf(
            presentation.title,
            presentation.message,
            presentation.technicalDetail.orEmpty(),
        ).joinToString(" ")

        assertTrue(visible.contains("BAD_COMMENT"))
        assertTrue(visible.contains("[redacted]"))
        assertFalse(visible.contains("secret-token"))
    }

    @Test
    fun destructiveDeleteUnknownOutcomeRequiresCheckBeforeRetry() {
        val presentation = commentDeleteErrorPresentation(
            CommentDeleteUiState(
                phase = CommentDeletePhase.FAILED,
                accountStableId = "reddit:u/test",
                postId = "post",
                commentFullname = "t1_abc",
                failureKind = CommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED,
                retryable = false,
                failureDetails = FailureDetails(retryable = false),
            ),
        )

        assertEquals("Comment deletion not confirmed", presentation.title)
        assertTrue(presentation.message.contains("Check the thread"))
        assertNull(presentation.primaryAction)
    }

    @Test
    fun membershipNotConfirmedAfterTheLastCheckSaysSoWithoutRetry() {
        val presentation = communityMembershipErrorPresentation(
            CommunityMembershipUiState(
                phase = CommunityMembershipPhase.FAILED,
                accountStableId = "reddit:u/test",
                communityName = "kotlin",
                action = CommunityMembershipAction.JOIN,
                failureKind = CommunityMembershipFailureKind.NOT_CONFIRMED,
                failureRetryable = false,
            ),
        )

        assertEquals("Membership change not confirmed", presentation.title)
        assertEquals(NOT_CONFIRMED_MEMBERSHIP_MESSAGE, presentation.message)
        assertNull(presentation.primaryAction)
    }

    @Test
    fun membershipNotConfirmedWithoutAnyListShowsItsOwnMessage() {
        val presentation = communityMembershipErrorPresentation(
            CommunityMembershipUiState(
                phase = CommunityMembershipPhase.FAILED,
                accountStableId = "reddit:u/test",
                communityName = "kotlin",
                action = CommunityMembershipAction.JOIN,
                failureKind = CommunityMembershipFailureKind.NOT_CONFIRMED,
                message = NOT_CHECKED_MEMBERSHIP_MESSAGE,
            ),
        )

        assertEquals(NOT_CHECKED_MEMBERSHIP_MESSAGE, presentation.message)
        assertNull(presentation.primaryAction)
    }

    @Test
    fun membershipHttpStatusLivesInTechnicalDetailNotPrimaryCopy() {
        val presentation = communityMembershipErrorPresentation(
            CommunityMembershipUiState(
                phase = CommunityMembershipPhase.FAILED,
                accountStableId = "reddit:u/test",
                communityName = "subshell",
                action = CommunityMembershipAction.JOIN,
                failureKind = CommunityMembershipFailureKind.HTTP,
                failureRetryable = true,
                failureStatusCode = 429,
                failureDetails = FailureDetails(
                    httpStatusCode = 429,
                    retryAfterMs = 12_000L,
                    retryable = true,
                ),
            ),
        )

        assertFalse(presentation.message.contains("HTTP 429"))
        assertEquals("HTTP 429 · Retry after 12 s", presentation.technicalDetail)
        assertEquals(ErrorActionKind.RETRY, presentation.primaryAction?.kind)
    }

    @Test
    fun customFeedOutcomeUnknownDoesNotOfferDuplicateCreateRetry() {
        val presentation = customFeedCreateErrorPresentation(
            CustomFeedsUiState(
                accountStableId = "reddit:u/test",
                createPhase = CustomFeedCreatePhase.FAILED,
                createFailureKind = CustomFeedCreateFailureKind.OUTCOME_UNKNOWN,
                createFailureDetails = FailureDetails(
                    retryable = true,
                    transportKind = NetworkTransportFailureKind.IO,
                ),
            ),
        )

        assertEquals("Feed creation not confirmed", presentation.title)
        assertNull(presentation.primaryAction)
        assertTrue(presentation.message.contains("checking your feeds"))
    }

    @Test
    fun ambiguousPostDeleteTransportDoesNotOfferBlindDeleteRetry() {
        val presentation = postDeleteErrorPresentation(
            PostDeleteUiState(
                phase = PostDeletePhase.FAILED,
                accountStableId = "reddit:u/test",
                postFullname = "t3_abc",
                failureKind = PostDeleteFailureKind.TRANSPORT,
                retryable = false,
                failureDetails = FailureDetails(
                    retryable = false,
                    transportKind = NetworkTransportFailureKind.TIMEOUT,
                ),
            ),
        )

        assertNull(presentation.primaryAction)
        assertEquals("Network: Timeout", presentation.technicalDetail)
    }

    @Test
    fun postManageMutationFailureDoesNotOfferBlindMutationRetry() {
        val presentation = postManageErrorPresentation(
            PostManageUiState(
                phase = PostManagePhase.FAILED,
                operation = io.github.brealorg.subshell.data.account.PostManageOperation.SET_SPOILER,
                failureKind = PostManageFailureKind.TRANSPORT,
                retryable = true,
                failureDetails = FailureDetails(
                    retryable = true,
                    transportKind = NetworkTransportFailureKind.TIMEOUT,
                ),
            ),
        )

        assertNull(presentation.primaryAction)
        assertEquals("Network: Timeout", presentation.technicalDetail)
    }
}
