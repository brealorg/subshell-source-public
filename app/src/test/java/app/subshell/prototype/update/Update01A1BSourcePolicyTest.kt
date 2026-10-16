package app.subshell.prototype.update

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Update01A1BSourcePolicyTest {
    private val root =
        File(requireNotNull(System.getProperty("user.dir"))).parentFile

    @Test
    fun mainActivityConsumesUpdatePresentationColdAndWarm() {
        val main = source(
            "app/src/main/java/app/subshell/prototype/MainActivity.kt",
        )
        assertTrue(main.split("consumeUpdatePresentationIntent(").size - 1 == 2)
        assertTrue(main.contains("currentVersionCode = BuildConfig.VERSION_CODE"))
        assertTrue(main.contains("debug = BuildConfig.DEBUG"))
    }

    @Test
    fun mainPresentationHostRemainsInternalWithoutExpandingSubshellAppApi() {
        val app = appSource()

        assertTrue(app.contains("UPDATE01A1B_MAIN_PRESENTATION_HOST_V1"))
        assertTrue(app.contains("UpdatePresentationHost("))
        assertTrue(
            app.contains(
                "automaticPresentationEnabled = updatePresentationHomeReady",
            ),
        )

        // A1B1 adds readiness to the internal presentation host. It must not
        // expand the public/root SubshellApp parameter surface.
        assertFalse(app.contains("updatePresentationRequest:"))
    }

    @Test
    fun sharedHelpScreenRendersPermanentChangelogInlineForBothEntrypoints() {
        val settings = source(
            "app/src/main/java/app/subshell/prototype/ui/screens/SettingsScreen.kt",
        )
        val app = appSource()
        val help = source(
            "app/src/main/java/app/subshell/prototype/ui/screens/HelpScreen.kt",
        )
        val presentation = source(
            "app/src/main/java/app/subshell/prototype/update/UpdatePresentationUi.kt",
        )
        val search = source(
            "app/src/main/java/app/subshell/prototype/ui/screens/SettingsSearchIndex.kt",
        )

        assertTrue(settings.contains("if (destination == SettingsDestination.ABOUT)"))
        assertTrue(settings.contains("HelpScreen("))
        assertTrue(app.contains("SecondaryDestination.HELP"))
        assertTrue(app.contains("HelpScreen("))

        assertTrue(help.contains("UPDATE01A1B_HELP_ABOUT_INLINE_CHANGELOG_V3"))
        // UPDATE05: the changelog is read off the main thread.
        assertTrue(
            help.contains("UpdateChangelogRepository(appContext).load()") ||
                help.contains("rememberBundledChangelog(appContext)"),
        )
        assertTrue(help.contains("LazyColumn("))
        assertTrue(help.contains("\"Changelog\""))
        assertTrue(help.contains("UpdateChangelogReleaseItem("))
        assertFalse(help.contains("View changelog"))
        assertFalse(help.contains("changelogVisible"))
        assertFalse(help.contains("UpdateChangelogScreen("))

        assertTrue(presentation.contains("internal fun UpdateChangelogReleaseItem("))
        assertTrue(
            presentation.contains(
                "UPDATE01A1B2_MATERIAL3_CHANGELOG_RELEASE_CARD_V1",
            ),
        )
        assertTrue(presentation.contains("shape = MaterialTheme.shapes.large"))
        assertTrue(
            presentation.contains(
                "color = MaterialTheme.colorScheme.surfaceContainerLow",
            ),
        )
        assertTrue(presentation.contains("modifier = Modifier.padding(18.dp)"))
        assertTrue(presentation.contains("style = MaterialTheme.typography.titleLarge"))
        assertTrue(presentation.contains("style = MaterialTheme.typography.labelLarge"))
        assertTrue(presentation.contains("style = MaterialTheme.typography.bodyLarge"))
        assertTrue(presentation.contains("if (release.highlights.isNotEmpty())"))
        assertTrue(presentation.contains("HorizontalDivider()"))
        assertFalse(presentation.contains("showDivider: Boolean"))

        assertTrue(presentation.contains("internal fun UpdateChangelogScreen("))
        assertTrue(presentation.contains("onViewChangelog ="))

        assertTrue(search.contains("changelog release notes what is new updates history"))
    }

    @Test
    fun debugPreviewDoesNotEnablePublicUpdateRuntime() {
        val presentation = source(
            "app/src/main/java/app/subshell/prototype/update/UpdatePresentation.kt",
        )
        val model = source(
            "app/src/main/java/app/subshell/prototype/update/UpdateModels.kt",
        )
        assertTrue(presentation.contains("UPDATE01_ACTION_DEBUG_PREVIEW_WHATS_NEW"))
        assertTrue(presentation.contains("if (!debug) return false"))
        assertTrue(model.contains("!debug && applicationId == UPDATE01_PRODUCTION_APPLICATION_ID"))
    }

    @Test
    fun updatePresentationIsHomeReadyOnboardingSafeAndSideEffectOwned() {
        val app = appSource()
        val backup = source(
            "app/src/main/java/app/subshell/prototype/ui/screens/BackupFolderOnboarding.kt",
        )
        val presentation = source(
            "app/src/main/java/app/subshell/prototype/update/UpdatePresentationUi.kt",
        )

        assertTrue(app.contains("UPDATE01A1B1_PRESENTATION_READINESS_V1"))
        assertTrue(app.contains("(state.currentNavigationRoute as? SubshellRoute.Main)"))
        assertTrue(app.contains("?.destination == MainDestination.HOME"))
        assertTrue(app.contains("var backupFolderOnboardingVisible by remember"))
        assertTrue(app.contains("onVisibilityChanged = { visible ->"))
        assertTrue(app.contains("if (!backupFolderOnboardingVisible)"))
        assertTrue(app.contains("automaticPresentationEnabled = updatePresentationHomeReady"))

        assertTrue(backup.contains("UPDATE01A1B1_BACKUP_ONBOARDING_VISIBILITY_OWNER_V1"))
        assertTrue(backup.contains("onVisibilityChanged: (Boolean) -> Unit"))
        assertTrue(backup.contains("LaunchedEffect(visible)"))
        assertTrue(backup.contains("onVisibilityChanged(visible)"))

        assertTrue(presentation.contains("automaticPresentationEnabled: Boolean"))
        assertTrue(presentation.contains("LaunchedEffect(request, automaticPresentationEnabled)"))
        assertTrue(presentation.contains("autoAvailableRelease?.takeUnless { cached ->"))
        assertTrue(presentation.contains("cached.versionCode == explicit.release.versionCode"))
        assertTrue(presentation.contains("LaunchedEffect(request, previewWhatsNewRelease)"))
        assertFalse(presentation.contains("} ?: UpdatePresentationCoordinator.clear(request)"))
        assertTrue(presentation.contains("automaticPresentationEnabled &&"))
        assertTrue(presentation.contains("?.takeIf { automaticPresentationEnabled }"))
    }

    @Test
    fun bundledChangelogIsOfflineAndNoSelfInstallerOrPlayCoreExists() {
        val ui = source(
            "app/src/main/java/app/subshell/prototype/update/UpdatePresentationUi.kt",
        )
        val changelog = source(
            "app/src/main/java/app/subshell/prototype/update/UpdateChangelog.kt",
        )
        val manifest = source("app/src/main/AndroidManifest.xml")
        val gradle = source("app/build.gradle.kts")

        assertTrue(changelog.contains("context.applicationContext"))
        assertTrue(changelog.contains("assets"))
        assertFalse(ui.contains("HttpURLConnection"))
        assertFalse(manifest.contains("REQUEST_INSTALL_PACKAGES"))
        assertFalse(gradle.contains("app-update"))
    }

    /** SubshellApp plus the composables it extracts into under ui/app. */
    private fun appSource(): String {
        val extracted = File(root, "app/src/main/java/app/subshell/prototype/ui/app")
            .listFiles { file -> file.extension == "kt" }
            .orEmpty()
            .sortedBy { file -> file.name }
            .joinToString("") { file -> file.readText() }
        return source("app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt") + extracted
    }

    private fun source(relative: String): String =
        File(root, relative).also { file ->
            check(file.exists()) { "Missing fixture: ${file.path}" }
        }.readText()
}
