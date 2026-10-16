package app.subshell.prototype.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserStatus01A1AccountIdentityPresentationPolicyTest {
    @Test
    fun accountSurfacesUseRedditIdentityAvatarWithStockFallback() {
        val app =
            File("src/main/java/app/subshell/prototype/ui/app/AccountSwitcherSheet.kt").readText()
        val settings =
            File("src/main/java/app/subshell/prototype/ui/screens/SettingsScreen.kt")
                .readText()

        val switcher =
            app.substringAfter("internal fun AccountSwitcherSheet(")
                .substringBefore("\n@Composable")

        assertTrue(
            switcher.contains(
                "USERSTATUS01A1_ACCOUNT_IDENTITY_AVATAR_V1",
            ),
        )
        assertTrue(switcher.contains("state.ensureUserIdentity(account.username)"))
        assertTrue(
            switcher.contains(
                "state.userFor(account.username).avatarImageUri",
            ),
        )
        assertTrue(switcher.contains("RemoteIdentityImage("))
        assertTrue(switcher.contains("RedditDefaultProfileAvatar("))
        assertFalse(
            switcher.contains(
                "leadingContent = { SnooAvatar(Modifier.size(40.dp)) }",
            ),
        )

        assertTrue(
            settings.contains(
                "USERSTATUS01A1_SETTINGS_ACCOUNT_IDENTITY_AVATAR_V1",
            ),
        )
        assertTrue(settings.contains("state.ensureUserIdentity(account.username)"))
        assertTrue(
            settings.contains(
                "state.userFor(account.username).avatarImageUri",
            ),
        )
        assertTrue(settings.contains("private fun AccountNavigationRow("))
        assertTrue(settings.contains("RemoteIdentityImage("))
        assertTrue(settings.contains("RedditDefaultProfileAvatar("))
    }

    @Test
    fun settingsLogoutKeepsDestructiveMaterialRole() {
        val settings =
            File("src/main/java/app/subshell/prototype/ui/screens/SettingsScreen.kt")
                .readText()

        val logout =
            settings.substringAfter("item(\"account-logout\")")
                .substringBefore("account-privacy-title")

        assertTrue(logout.contains("DestructiveNavigationRow("))
        assertTrue(logout.contains("Icons.AutoMirrored.Rounded.ExitToApp"))
        assertTrue(settings.contains("private fun DestructiveNavigationRow("))
        // Successor-aware (#225, M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1): the
        // destructive row is a list item in the error colour, not an
        // errorContainer card.
        assertTrue(
            (settings.contains("MaterialTheme.colorScheme.errorContainer") &&
                settings.contains("MaterialTheme.colorScheme.onErrorContainer")) ||
                settings.contains("destructive = true,"),
        )
    }
}
