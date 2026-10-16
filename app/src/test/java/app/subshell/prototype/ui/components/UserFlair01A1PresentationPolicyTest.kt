package app.subshell.prototype.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFlair01A1PresentationPolicyTest {
    @Test
    fun communityOwnFlairUsesMaterial3ExpandedInsetAwareSheet() {
        val sheet = File(
            "src/main/java/app/subshell/prototype/ui/components/UserFlairSheet.kt",
        ).readText()
        assertTrue(sheet.contains("USERFLAIR01A1_MATERIAL3_OWN_COMMUNITY_FLAIR_SHEET_V1"))
        assertTrue(sheet.contains("ModalBottomSheet("))
        assertTrue(sheet.contains("rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))"))
        assertTrue(sheet.contains(".navigationBarsPadding()"))
        assertTrue(sheet.contains(".imePadding()"))
        assertTrue(sheet.contains("RadioButton("))
        assertTrue(sheet.contains("OutlinedTextField("))
    }

    @Test
    fun communityOverflowOwnsEntryAndRemovalIsNotGuessed() {
        val community = File(
            "src/main/java/app/subshell/prototype/ui/screens/CommunityScreen.kt",
        ).readText()
        val sheet = File(
            "src/main/java/app/subshell/prototype/ui/components/UserFlairSheet.kt",
        ).readText()
        assertTrue(community.contains("USERFLAIR01A1_COMMUNITY_OVERFLOW_ENTRY_V1"))
        assertTrue(community.contains("Text(\"User flair\")"))
        assertFalse(sheet.contains("Remove flair"))
        assertFalse(sheet.contains("No flair"))
    }

    @Test
    fun writeIsNonRetryingAndAuthorizationDoesNotReplayMutation() {
        // The user flair manager is built in AccountWriteRuntimeParts since R7d.
        val runtime = File(
            "src/main/java/app/subshell/prototype/auth/AccountWriteRuntimeParts.kt",
        ).readText()
        val coordinator = File(
            "src/main/java/app/subshell/prototype/auth/UserFlairCoordinator.kt",
        ).readText()
        assertTrue(runtime.contains("writeCallFactory = SubshellNetworkTransport.nonRetryingWriteCallFactory"))
        assertTrue(coordinator.contains("No pending mutation is stored"))
        assertTrue(coordinator.contains("refreshAfterAuthorization"))
    }
}
