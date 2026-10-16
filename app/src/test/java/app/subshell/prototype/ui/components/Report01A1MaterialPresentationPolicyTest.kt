package app.subshell.prototype.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Report01A1MaterialPresentationPolicyTest {
    @Test fun reportFlowUsesMaterial3Components() {
        val source = File("src/main/java/app/subshell/prototype/ui/components/ContentSafetyDialogs.kt").readText()
        assertTrue(source.contains("REPORT01A1_MATERIAL3_NATIVE_REPORT_REASON_SHEET_V1"))
        assertTrue(source.contains("ModalBottomSheet("))
        assertTrue(source.contains("ListItem("))
        assertTrue(source.contains("RadioButton("))
        assertTrue(source.contains("OutlinedTextField("))
        assertTrue(source.contains("MaterialTheme.colorScheme.secondaryContainer"))
        assertTrue(source.contains("REPORT01A1_M3_SYSTEM_INSET_AWARE_ACTION_FOOTER_V1"))
        assertTrue(source.contains(".navigationBarsPadding()"))
        assertTrue(source.contains(".imePadding()"))
        assertTrue(source.contains(".padding(top = 4.dp, bottom = 12.dp)"))
        assertTrue(source.contains("REPORT01A1_M3_ALWAYS_EXPANDED_SHEET_V1"))
        assertTrue(source.contains("rememberBottomSheetState("))
        assertTrue(source.contains("enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)"))
        assertTrue(source.contains("sheetState = reportSheetState"))
    }
}
