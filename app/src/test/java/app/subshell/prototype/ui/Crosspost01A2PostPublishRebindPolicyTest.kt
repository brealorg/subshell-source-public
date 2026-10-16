package app.subshell.prototype.ui

import app.subshell.prototype.data.account.SubmittedTextPost
import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Crosspost01A2PostPublishRebindPolicyTest {
    private val submitted = SubmittedTextPost(
        fullname = "t3_created123",
        id = "created123",
        community = "target",
        title = "Crosspost title",
        text = "",
        permalink = "/r/target/comments/created123/",
        crosspostFullname = "t3_source123",
    )

    private fun fallback(): Post = Post(
        id = "t3_created123",
        community = "target",
        author = "crossposter",
        age = "0m",
        title = "Crosspost title",
        score = 1,
        comments = 0,
        kind = PostKind.CROSSPOST,
        crosspost = CrosspostPreview(
            id = "t3_source123",
            community = "source",
            author = "source-author",
            title = "Source title",
            score = 0,
            kind = PostKind.VIDEO,
        ),
    )

    private fun canonical(): Post = fallback().copy(
        age = "1m",
        score = 2,
        crosspost = requireNotNull(fallback().crosspost).copy(
            remoteVideoUri = "https://v.redd.it/example/DASH_720.mp4",
            remoteVideoHlsUri = "https://v.redd.it/example/HLSPlaylist.m3u8",
        ),
    )

    @Test
    fun matchingCanonicalWrapperAndSourceReplaceSyntheticProjection() {
        val fallback = fallback()
        val canonical = canonical()

        val result = canonicalCrosspostCompletionOrFallback(
            submitted = submitted,
            fallback = fallback,
            candidate = canonical,
        )

        assertEquals(canonical, result)
        assertEquals(
            "https://v.redd.it/example/DASH_720.mp4",
            result.crosspost?.remoteVideoUri,
        )
    }

    @Test
    fun wrongWrapperOrWrongSourceCannotReplaceSyntheticProjection() {
        val fallback = fallback()

        val wrongWrapper = canonical().copy(id = "t3_other")
        val wrongSource = canonical().copy(
            crosspost = requireNotNull(canonical().crosspost).copy(id = "t3_other"),
        )

        assertSame(
            fallback,
            canonicalCrosspostCompletionOrFallback(submitted, fallback, wrongWrapper),
        )
        assertSame(
            fallback,
            canonicalCrosspostCompletionOrFallback(submitted, fallback, wrongSource),
        )
    }

    @Test
    fun wrongDestinationTitleOrFlagsCannotReplaceSyntheticProjection() {
        val fallback = fallback()

        for (
            candidate in listOf(
                canonical().copy(community = "other"),
                canonical().copy(title = "Other"),
                canonical().copy(nsfw = true),
                canonical().copy(spoiler = true),
            )
        ) {
            assertSame(
                fallback,
                canonicalCrosspostCompletionOrFallback(submitted, fallback, candidate),
            )
        }
    }

    @Test
    fun canonicalRebindUpdatesOnlyStillOpenSubmittedWrapper() {
        val state = SubshellState()
        val fallback = fallback()
        val canonical = canonical()

        state.open(fallback)
        assertTrue(
            state.rebindCompletedCrosspostToCanonical(
                submittedPost = submitted,
                canonicalPost = canonical,
            ),
        )
        assertEquals(canonical, state.selectedPost)
    }

    @Test
    fun canonicalRebindCannotStealNavigationAfterBack() {
        val state = SubshellState()
        state.open(fallback())
        state.closePost()

        assertFalse(
            state.rebindCompletedCrosspostToCanonical(
                submittedPost = submitted,
                canonicalPost = canonical(),
            ),
        )
    }
}
