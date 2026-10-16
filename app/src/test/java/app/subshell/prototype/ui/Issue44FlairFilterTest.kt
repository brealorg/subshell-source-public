package app.subshell.prototype.ui

import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #44: a flair filter matches the raw flair text and the text people see. */
class Issue44FlairFilterTest {
    private fun post(flair: String?) = Post(
        id = "t3_flair",
        community = "ratemyfitsfw",
        author = "poster",
        age = "1h",
        title = "Title",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
        flair = flair,
    )

    private fun flairFilter(value: String) = ContentFilters().adding(ContentFilterType.FLAIR, value)

    @Test
    fun theVisibleTextOfAnEmojiFlairMatches() {
        assertTrue(flairFilter("Outfit Check").hides(post(":upvote:Outfit Check")))
    }

    @Test
    fun anExistingFilterOnTheRawTextStillMatches() {
        assertTrue(flairFilter(":upvote:Outfit Check").hides(post(":upvote:Outfit Check")))
    }

    @Test
    fun plainFlairsAndNonMatchesBehaveAsBefore() {
        assertTrue(flairFilter("outfit check").hides(post("Outfit Check")))
        assertFalse(flairFilter("Outfit").hides(post(":upvote:Outfit Check")))
        assertFalse(flairFilter("Outfit Check").hides(post(null)))
    }

    @Test
    fun anEmojiOnlyFlairIsNotMatchedByAnEmptyVisibleText() {
        assertFalse(flairFilter(":upvote:").hides(post("Something else")))
        assertTrue(flairFilter(":upvote:").hides(post(":upvote:")))
    }
}
