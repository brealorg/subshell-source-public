package app.subshell.prototype.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (wiki01a2r1-r4): while the wiki workspace is open the community sheet
 * refuses to hide, and Back steps from a page to the wiki to the community
 * info before it closes the sheet; wiki Markdown turns "#Title" headings and
 * lines of three dashes, stars or underscores into headings and rules, and
 * a preview keeps them.
 */
@OptIn(ExperimentalMaterial3Api::class)
class Step3WikiSheetAndMarkdownTest {
    @Test
    fun theSheetRefusesToHideWhileTheWikiIsOpen() {
        assertFalse(communityAboutSheetMayMoveTo(SheetValue.Hidden, wikiWorkspaceVisible = true))
        assertTrue(communityAboutSheetMayMoveTo(SheetValue.Hidden, wikiWorkspaceVisible = false))
        assertTrue(communityAboutSheetMayMoveTo(SheetValue.Expanded, wikiWorkspaceVisible = true))
    }

    @Test
    fun backStepsFromPageToWikiToCommunityInfoThenCloses() {
        assertEquals(WikiBackStep.CLOSE_PAGE, wikiBackStep(selectedPage = "faq", workspaceVisible = true))
        assertEquals(WikiBackStep.CLOSE_WORKSPACE, wikiBackStep(selectedPage = null, workspaceVisible = true))
        assertEquals(WikiBackStep.DISMISS_SHEET, wikiBackStep(selectedPage = null, workspaceVisible = false))
    }

    @Test
    fun wikiMarkdownHasHeadingsAndRules() {
        val blocks = promoteMarkdownHeadings(parseMarkdownBlocks("#Rules\n\nfirst\n\n---\n\n***\n\n___\n\nlast"))

        assertEquals(MarkdownBlock.Heading(level = 1, text = "Rules"), blocks.first())
        assertEquals(3, blocks.count { it == MarkdownBlock.HorizontalRule })
        val preview = markdownPreviewSource(blocks)
        assertTrue(preview, preview.startsWith("Rules\n"))
        assertTrue(preview, "---" in preview)
    }
}
