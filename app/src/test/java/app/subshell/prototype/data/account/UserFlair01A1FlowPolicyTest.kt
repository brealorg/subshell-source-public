package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFlair01A1FlowPolicyTest {
    @Test
    fun mutationIsAccountCommunityTemplatePinnedAndTextBounded() {
        val request = UserFlairMutationRequest(
            accountStableId = " acct ",
            communityName = "r/SubShell",
            templateId = " template ",
            text = " Custom ",
        ).normalizedUserFlairMutationRequest()
        assertEquals("acct", request.accountStableId)
        assertEquals("subshell", request.communityName)
        assertEquals("template", request.templateId)
        assertEquals("Custom", request.text)
    }

    @Test
    fun uiStateMatchesOnlyExactAccountAndNormalizedCommunity() {
        val state = UserFlairUiState(accountStableId = "acct", communityName = "subshell")
        assertTrue(state.matches("acct", "r/SubShell"))
    }
}
