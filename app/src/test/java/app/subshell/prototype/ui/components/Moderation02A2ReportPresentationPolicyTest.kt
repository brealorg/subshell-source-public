package app.subshell.prototype.ui.components

import app.subshell.prototype.ui.symbols.Symbols
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class Moderation02A2ReportPresentationPolicyTest {
    private fun source(name: String) = File("src/main/java/app/subshell/prototype/$name").readText()

    /** SubshellApp plus the hosts it was split into (ui/app/). */
    private fun appSource(): String =
        source("ui/SubshellApp.kt") +
            File("src/main/java/app/subshell/prototype/ui/app")
                .listFiles()
                .orEmpty()
                .filter { it.name.endsWith(".kt") }
                .sortedBy { it.name }
                .joinToString("") { it.readText() }
    @Test fun oneRootDialogOutlivesBothQueueRowsAndSupportsLookup() {
        assertTrue(appSource().contains("ModeratorReportsDialog("))
        assertTrue(source("ui/screens/InboxScreen.kt").contains("ModeratorReportLookupButton()"))
        assertTrue(source("ui/components/ModeratorToolsSheet.kt").contains("ModeratorReportLookupButton(communityName)"))
        assertTrue(source("ui/components/ModeratorReports.kt").contains("Recently opened · this session"))
    }
    @Test fun postAndCommentOverflowsProvideQueueIndependentReversal() {
        assertTrue(source("ui/components/Common.kt").contains("MODERATION02A2_POST_REPORT_REOPEN_OUTSIDE_QUEUE_V1"))
        assertTrue(source("ui/screens/PostScreen.kt").contains("MODERATION02A2_COMMENT_REPORT_REOPEN_OUTSIDE_QUEUE_V1"))
    }
    @Test fun confirmationIsConsumedAndNoReportBodiesArePersistedOrLogged() {
        val ui = source("ui/components/ModeratorReports.kt")
        assertTrue(ui.contains("pending = null\n                    onConfirm(current)"))
        assertTrue(ui.contains("remember(accountStableId, state.target, state.revision)"))
        assertFalse(ui.contains("rememberSaveable"))
        val controller = source("auth/ModeratorReportsCoordinator.kt")
        assertFalse(controller.contains("Log.")); assertFalse(controller.contains("println("))
        assertTrue(controller.contains(".distinct().take(20)"))
    }
    @Test fun reportMenuUsesAutoMirroredFactCheckWithoutWarningSuppression() {
        val ui = source("ui/components/ModeratorReports.kt")
        // MATERIALSYMBOLS01: the symbol itself mirrors in right-to-left layouts.
        assertTrue(ui.contains("Symbols.FactCheck"))
        assertTrue(Symbols.FactCheck.autoMirror)
        assertFalse(ui.contains("@Suppress"))
        assertFalse(ui.contains("@file:Suppress"))
    }
    @Test fun authorizationNoticeKeepsOwnerGuardBeforeSmartCastTargetAccess() {
        val ui = source("ui/components/ModeratorReports.kt")
        val guard = "actionState.request?.accountStableId == accountStableId &&"
        val target = "actionState.request.thingFullname == state.target?.fullname &&"
        assertTrue(ui.contains(guard))
        assertTrue(ui.contains(target))
        assertTrue(ui.indexOf(guard) < ui.indexOf(target))
        assertTrue(ui.contains("actionState.phase == RedditModeratorActionPhase.AUTHORIZATION_REQUIRED"))
        assertFalse(ui.contains("actionState.request?.thingFullname"))
        assertFalse(ui.contains("actionState.request!!"))
    }
    @Test fun lookupAndRowEntriesAreButtonsRatherThanBareText() {
        val ui = source("ui/components/ModeratorReports.kt")
        val lookup = ui.substringAfter("internal fun ModeratorReportLookupButton(").substringBefore("@Composable")
        val row = ui.substringAfter("internal fun ModeratorReportsButton(").substringBefore("@Composable")
        assertTrue(lookup.contains("FilledTonalButton("))
        assertTrue(lookup.contains("Symbols.Search"))
        assertTrue(lookup.contains("Text(\"Find reports\")"))
        assertTrue(row.contains("OutlinedButton("))
        assertTrue(row.contains("View reports"))
        assertFalse(lookup.contains("TextButton("))
        assertFalse(row.contains("TextButton("))
    }
    @Test fun lookupListsLoadedQueueBeforeSessionHistoryAndReadsOnlyOnSelection() {
        val ui = source("ui/components/ModeratorReports.kt")
        assertTrue(appSource().contains("moderationState = redditModerationState"))
        assertTrue(ui.contains("moderatorReportLookupContent(accountStableId, moderationState, state)"))
        assertTrue(ui.contains("itemsIndexed(lookupContent.queueItems"))
        assertTrue(ui.indexOf("In the loaded queue") < ui.indexOf("Recently opened · this session"))
        assertTrue(ui.contains("onClick = { onOpen(item.reportTarget()) }"))
        assertFalse(ui.contains("LaunchedEffect"))
    }
    @Test fun selectedTargetDoesNotStartWithAnEmptyLookupForm() {
        val ui = source("ui/components/ModeratorReports.kt")
        // V4 moves the manual input below the loaded queue, within the chooser branch.
        val chooser = ui.substringAfter("// MODERATION02A2R3_QUEUE_FIRST_COLLAPSIBLE_MANUAL_LOOKUP_V1")
            .substringBefore("val target = state.target")
        assertTrue(chooser.contains("if (state.target == null) {"))
        assertTrue(chooser.contains("if (lookupExpanded) {"))
        assertTrue(chooser.contains("OutlinedTextField("))
        assertTrue(chooser.indexOf("itemsIndexed(lookupContent.queueItems") < chooser.indexOf("OutlinedTextField("))
        assertTrue(ui.contains("Choose another item"))
        assertTrue(ui.contains("key(accountStableId, state.target?.fullname) { rememberLazyListState() }"))
        assertTrue(ui.contains("state = reportListState"))
        assertTrue(ui.contains("onClick = { onOpen(null) }, enabled = !busy"))
        assertTrue(ui.contains("moderatorReportQuantity(it)"))
        assertFalse(ui.contains("\$it reports"))
    }
}
