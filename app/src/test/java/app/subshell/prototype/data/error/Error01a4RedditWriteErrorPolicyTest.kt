package app.subshell.prototype.data.error

import app.subshell.prototype.data.account.CommentDeleteFailureKind
import app.subshell.prototype.data.account.CommentDeletePhase
import app.subshell.prototype.data.account.CommentDeleteUiState
import app.subshell.prototype.data.account.CommentEditFailureKind
import app.subshell.prototype.data.account.CommentEditPhase
import app.subshell.prototype.data.account.CommentEditUiState
import app.subshell.prototype.data.account.CommentWriteFailureKind
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipFailureKind
import app.subshell.prototype.data.account.CommunityMembershipPhase
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.CustomFeedCreateFailureKind
import app.subshell.prototype.data.account.CustomFeedCreatePhase
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.data.account.NOT_CHECKED_MEMBERSHIP_MESSAGE
import app.subshell.prototype.data.account.NOT_CONFIRMED_MEMBERSHIP_MESSAGE
import app.subshell.prototype.data.account.PostDeleteFailureKind
import app.subshell.prototype.data.account.PostDeletePhase
import app.subshell.prototype.data.account.PostDeleteUiState
import app.subshell.prototype.data.account.PostManageFailureKind
import app.subshell.prototype.data.account.PostManagePhase
import app.subshell.prototype.data.account.PostManageUiState
import app.subshell.prototype.data.account.TextPostWriteFailureKind
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.commentDeleteErrorPresentation
import app.subshell.prototype.ui.error.commentEditErrorPresentation
import app.subshell.prototype.ui.error.commentWriteErrorPresentation
import app.subshell.prototype.ui.error.communityMembershipErrorPresentation
import app.subshell.prototype.ui.error.customFeedCreateErrorPresentation
import app.subshell.prototype.ui.error.postDeleteErrorPresentation
import app.subshell.prototype.ui.error.postManageErrorPresentation
import app.subshell.prototype.ui.error.textPostWriteErrorPresentation
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
                operation = app.subshell.prototype.data.account.PostManageOperation.SET_SPOILER,
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
