package io.github.brealorg.subshell.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.data.account.CommentWriteFailureKind
import io.github.brealorg.subshell.data.account.CommentWritePhase
import io.github.brealorg.subshell.data.account.CommentWriteUiState
import io.github.brealorg.subshell.data.account.TextPostWriteFailureKind
import io.github.brealorg.subshell.data.account.TextPostWritePhase
import io.github.brealorg.subshell.data.account.TextPostWriteUiState
import io.github.brealorg.subshell.data.error.redditWriteProviderFailureDetails
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.ui.error.commentWriteErrorPresentation
import io.github.brealorg.subshell.ui.error.textPostWriteErrorPresentation
import io.github.brealorg.subshell.ui.s
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMPOSERERR01 (Pixel 11, 2026-11-18): r/astronotyet's Automation refused a short post
 * with POST_GUIDANCE_VALIDATION_FAILED and "This is under 25 words". The card read only
 * "Reddit rejected the post." and sat above the screen while the body was focused, so
 * Post seemed to do nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp")
class Composererr01RefusedPostComposeTest {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private fun refused(revision: Long, reason: String? = "This is under 25 words") = TextPostWriteUiState(
        phase = TextPostWritePhase.FAILED,
        accountStableId = "reddit:test",
        requestKey = "draft-1",
        failureKind = TextPostWriteFailureKind.API,
        failureDetails = redditWriteProviderFailureDetails(
            code = "POST_GUIDANCE_VALIDATION_FAILED",
            message = reason,
        ),
        completionRevision = revision,
    )

    @Test
    fun theCardSaysWhatRedditSaid() {
        val presentation = textPostWriteErrorPresentation(refused(revision = 1))

        assertEquals("Reddit rejected the post: This is under 25 words", presentation.message.s)
        // Shown once: the details keep the code, not the words again.
        assertEquals(
            "POST_GUIDANCE_VALIDATION_FAILED",
            presentation.failureDetails?.providerCode?.value,
        )
        assertNull(presentation.failureDetails?.providerMessage)
    }

    @Test
    fun withoutRedditsWordsTheCardKeepsItsOwn() {
        val presentation = textPostWriteErrorPresentation(refused(revision = 1, reason = null))

        assertEquals("Reddit rejected the post.", presentation.message.s)
    }

    @Test
    fun aRefusedCommentSaysWhatRedditSaid() {
        val presentation = commentWriteErrorPresentation(
            CommentWriteUiState(
                phase = CommentWritePhase.FAILED,
                failureKind = CommentWriteFailureKind.VALIDATION,
                failureDetails = redditWriteProviderFailureDetails(
                    code = "COMMENT_GUIDANCE_VALIDATION_FAILED",
                    message = "Comments here need at least 10 words",
                ),
            ),
        )

        assertEquals(
            "Reddit rejected the comment: Comments here need at least 10 words",
            presentation.message.s,
        )
        assertNull(presentation.failureDetails?.providerMessage)
    }

    @Test
    fun aRefusalScrollsItsNoticeIntoViewOncePerResult() {
        var writeState by mutableStateOf(TextPostWriteUiState())
        lateinit var listState: LazyListState
        composeRule.setContent {
            listState = rememberLazyListState()
            RevealTextPostWriteNotice(
                writeState = writeState,
                listState = listState,
                noticeItemIndex = 0,
                draftKey = "draft-1",
            )
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(40) { index -> Text("row $index", Modifier.height(80.dp)) }
            }
        }
        composeRule.runOnIdle { runBlocking { listState.scrollToItem(20) } }
        composeRule.waitForIdle()
        assertEquals(20, listState.firstVisibleItemIndex)

        writeState = refused(revision = 1)
        composeRule.waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex)

        // The reader scrolls away again; the same result does not pull them back.
        composeRule.runOnIdle { runBlocking { listState.scrollToItem(20) } }
        writeState = writeState.copy()
        composeRule.waitForIdle()
        assertEquals(20, listState.firstVisibleItemIndex)

        // The next refusal does.
        writeState = refused(revision = 2)
        composeRule.waitForIdle()
        assertTrue(listState.firstVisibleItemIndex == 0)
    }
}
