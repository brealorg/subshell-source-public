package app.subshell.prototype.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-policy checks. These do not replace rendered Compose/UI tests. */
class Moderation02A2ExpressivePresentationPolicyTest {
    private fun source(name: String) = File("src/main/java/app/subshell/prototype/ui/components/$name.kt").readText()

    @Test fun bothQueuesUseOneHeaderAndWrapActionsWithoutChangingCallbacks() {
        listOf("InboxModeratorQueue", "ModeratorToolsSheet").forEach { name ->
            val s = source(name)
            assertTrue(s.contains("ModeratorQueueItemSummary("))
            assertTrue(s.contains("FlowRow("))
            assertTrue(s.contains("Symbols.Check"))
            assertTrue(s.contains("Symbols.Block"))
            assertTrue(s.contains("Text(\"Approve\")"))
            assertTrue(s.contains("Text(\"Remove\")"))
            assertTrue(s.contains("action = RedditModeratorAction.APPROVE"))
            assertEquals(1, Regex("ModeratorSpamAction\\(").findAll(s).count())
            assertFalse(s.contains("action = RedditModeratorAction.SPAM"))
        }
        assertTrue(source("ModeratorReviewPresentation").contains("moderatorReportCountLabel(item)"))
    }
    @Test fun chooserPrioritizesLoadedQueueOverManualInput() {
        val s = source("ModeratorReports")
        val chooser = s.substringAfter("// MODERATION02A2R3_QUEUE_FIRST_COLLAPSIBLE_MANUAL_LOOKUP_V1")
            .substringBefore("val target = state.target")
        assertTrue(chooser.indexOf("itemsIndexed(lookupContent.queueItems") < chooser.indexOf("OutlinedTextField("))
        assertTrue(chooser.contains("if (lookupExpanded) {"))
        assertTrue(chooser.contains("lookupExpanded = !lookupExpanded"))
        assertTrue(chooser.contains("Modifier.animateContentSize()"))
        assertTrue(s.contains("var lookupExpanded by remember(accountStableId, state.target) { mutableStateOf(false) }"))
    }
    @Test fun noEmptyRecentHistorySectionOrPermanentDeveloperFooter() {
        val s = source("ModeratorReports")
        assertTrue(s.contains("if (lookupContent.recentTargets.isNotEmpty()) {"))
        assertFalse(s.contains("No other items opened in this session."))
        assertFalse(s.contains("item(\"bottom\")"))
        assertFalse(s.contains("item(\"detail-navigation\")"))
    }
    @Test fun refreshAndChooseAreCompactReadOnlyHeaderActions() {
        val s = source("ModeratorReports")
        val header = s.substringAfter("// MODERATION02A2R3_COMPACT_REPORT_TOP_BAR_V1")
            .substringBefore("LazyColumn(")
        assertTrue(header.contains("onClick = { onOpen(null) }, enabled = !busy"))
        assertTrue(header.contains("IconButton(onClick = onRefresh, enabled = !busy)"))
        assertTrue(header.contains("contentDescription = \"Choose another item\""))
        assertTrue(header.contains("contentDescription = \"Refresh reports\""))
        assertTrue(header.contains("contentDescription = \"Close reports\""))
        assertFalse(header.contains("onConfirm"))
    }
    @Test fun shortPanelWrapsButLongContentRemainsScrollableAndKeyboardAware() {
        val s = source("ModeratorReports")
        // Successor-aware (#225, M3225_FORM_DIALOGS_ARE_FULL_SCREEN_ON_PHONES_V1): the
        // panel is the shared adaptive full-screen dialog (keyboard-aware there).
        if (s.contains("AdaptiveFullScreenDialog(")) {
            assertTrue(s.contains("modifier = Modifier.weight(1f),"))
            assertTrue(s.contains("state = reportListState"))
            return
        }
        assertTrue(s.contains("BoxWithConstraints("))
        assertTrue(s.contains("val panelMaxHeight = maxHeight * 0.94f"))
        assertTrue(s.contains("heightIn(max = panelMaxHeight)"))
        assertTrue(s.contains("Modifier.weight(1f, fill = false)"))
        assertTrue(s.contains("imePadding()"))
        assertFalse(s.contains("fillMaxHeight(0.90f)"))
        assertTrue(s.contains("state = reportListState"))
    }
    @Test fun unknownAndIncompleteReportsRetainExplicitWarnings() {
        val s = source("ModeratorReports")
        assertTrue(s.contains("ReportStatusBadge(reports.ignored)"))
        assertTrue(s.contains("null -> \"Report status unavailable\""))
        assertTrue(s.contains("unavailable = userReasons == null || reports.incomplete"))
        assertTrue(s.contains("unavailable = modReasons == null || reports.incomplete"))
        assertTrue(s.contains("Some report details were unavailable or shortened."))
        assertTrue(s.contains("if (confirmation != null)"))
        assertFalse(s.contains("reports.ignored ?: false"))
    }
    @Test fun reportConfirmationRetainsPreciseTargetConsentAndReversalCopy() {
        val s = source("ModeratorReports")
        assertTrue(s.contains("Text(pinned.target.fullname"))
        assertTrue(s.contains("pinned.target.communityName"))
        assertTrue(s.contains("Undo with Unignore reports."))
        assertTrue(s.contains("This does not approve, remove or mark the content as spam, or ban its author."))
        assertTrue(s.contains("current != reportConfirmationOrNull(state, accountStableId) || busy"))
        assertTrue(s.contains("enabled = pinned == confirmation && !busy"))
        assertTrue(s.contains("pending = null\n                    onConfirm(current)"))
        assertEquals(1, Regex("onConfirm\\(current\\)").findAll(s).count())
    }
    @Test fun themeRolesAndAccessibleTouchSizesReplaceHardcodedVisuals() {
        val ui = source("ModeratorReports") + source("ModeratorReviewPresentation")
        assertTrue(ui.contains("MaterialTheme.colorScheme.surfaceContainer"))
        assertTrue(ui.contains("MaterialTheme.shapes.large"))
        assertTrue(ui.contains("MaterialTheme.typography.headlineSmall"))
        assertTrue(ui.contains("heightIn(min = 48.dp)"))
        assertTrue(ui.contains("Role.Button"))
        assertTrue(ui.contains("LiveRegionMode.Polite"))
        listOf("Color(0x", "Color.White", "Color.Black", "fontSize =", "@Suppress", "rememberSaveable").forEach {
            assertFalse(ui.contains(it))
        }
    }
    @Test fun sharedSummaryAndChooserDoNotCreateWriteAuthority() {
        val summary = source("ModeratorReviewPresentation")
        listOf("onModerate(", "onConfirm(", "loadReportTarget(", "performAction(", "MutableStateFlow", "LaunchedEffect").forEach {
            assertFalse(summary.contains(it))
        }
        val ui = source("ModeratorReports")
        assertFalse(ui.contains("LaunchedEffect"))
        assertFalse(ui.contains("println("))
        assertFalse(ui.contains("Log."))
    }
}
