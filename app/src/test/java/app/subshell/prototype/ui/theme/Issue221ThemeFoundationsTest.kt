package app.subshell.prototype.ui.theme

import android.app.Application
import android.view.ContextThemeWrapper
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.R
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.SubshellApp
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.ThemeChoice
import app.subshell.prototype.ui.components.favoriteStarTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * #221 (M3 audit group 4, theme foundations): M3221_LIGHT_COLD_START_V1,
 * M3221_DARK_SURFACE_IS_TONE_6_V1, M3221_INVERSE_ROLES_IN_THE_WARM_PALETTE_V1,
 * M3221_SURFACE_LEVELS_ARE_COLOUR_ROLES_V1, M3221_COMPLETE_TYPE_SCALE_V1,
 * M3221_GROUPED_ROWS_USE_THE_SHAPE_SCALE_V1, M3221_ONE_FAVORITE_STAR_COLOUR_V1,
 * M3221_APP_LOCK_FOLLOWS_THE_APP_THEME_V1, M3221_ROWS_TAKE_THEIR_CONTAINERS_COLOUR_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue221ThemeFoundationsTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun windowBackground(): Int {
        val themed = ContextThemeWrapper(context, R.style.Theme_Subshell)
        val values = themed.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        try {
            return values.getColor(0, 0)
        } finally {
            values.recycle()
        }
    }

    @Test
    fun aLightThemeColdStartDrawsTheLightSurfaceNotADarkWindow() {
        assertEquals(LightColors.surface.toArgb(), windowBackground())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun aDarkThemeColdStartDrawsTheDarkSurface() {
        assertEquals(DarkColors.surface.toArgb(), windowBackground())
    }

    @Test
    fun darkSurfaceSitsBelowItsContainersSoSheetsSeparate() {
        assertNotEquals(DarkColors.surfaceContainerLow, DarkColors.surface)
        assertTrue(DarkColors.surface.luminance() < DarkColors.surfaceContainerLow.luminance())
        assertTrue(DarkColors.surfaceContainerLowest.luminance() < DarkColors.surface.luminance())
    }

    @Test
    fun inverseRolesComeFromTheWarmPalette() {
        // Material's baseline inverse roles are lavender (inversePrimary D0BCFF / 6750A4).
        assertEquals(Color(0xFF914300), DarkColors.inversePrimary)
        assertEquals(Color(0xFFFFB68C), LightColors.inversePrimary)
        assertEquals(Color(0xFFF2DFD7), DarkColors.inverseSurface)
        assertEquals(Color(0xFF382E2A), LightColors.inverseSurface)
        assertEquals(Color.Black, DarkColors.scrim)
        assertEquals(Color.Black, LightColors.scrim)
    }

    @Test
    fun noSurfaceIsLevelledWithTonalElevation() {
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readText().contains("tonalElevation =") }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun listRowsAndTabRowsTakeTheirContainersColour() {
        val call = Regex("""\b(ListItem|PrimaryTabRow|PrimaryScrollableTabRow|SecondaryTabRow|TabRow)\(""")
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                call.findAll(text).mapNotNull { match ->
                    var depth = 1
                    var end = match.range.last + 1
                    while (depth > 0) {
                        when (text[end]) {
                            '(' -> depth++
                            ')' -> depth--
                        }
                        end++
                    }
                    val arguments = text.substring(match.range.last + 1, end)
                        .lines()
                        .joinToString("\n") { line -> line.substringBefore("//") }
                    val colourName = if (match.groupValues[1] == "ListItem") "colors =" else "containerColor ="
                    if (arguments.contains(colourName)) null else "${file.name}: ${match.value}"
                }
            }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun everyTypeRoleIsDefinedInTheAppsFamilyWithItsOwnWeight() {
        lateinit var typography: Typography
        composeRule.setContent {
            SubshellTheme(manageSystemBarContrast = false) { typography = MaterialTheme.typography }
        }
        composeRule.waitForIdle()
        val baseline = Typography()
        val roles: Map<String, TextStyle> = mapOf(
            "displayLarge" to typography.displayLarge,
            "displayMedium" to typography.displayMedium,
            "displaySmall" to typography.displaySmall,
            "headlineLarge" to typography.headlineLarge,
            "headlineMedium" to typography.headlineMedium,
            "headlineSmall" to typography.headlineSmall,
            "titleLarge" to typography.titleLarge,
            "titleMedium" to typography.titleMedium,
            "titleSmall" to typography.titleSmall,
            "bodyLarge" to typography.bodyLarge,
            "bodyMedium" to typography.bodyMedium,
            "bodySmall" to typography.bodySmall,
            "labelLarge" to typography.labelLarge,
            "labelSmall" to typography.labelSmall,
        )
        roles.forEach { (name, style) -> assertEquals(name, FontFamily.SansSerif, style.fontFamily) }
        // Owner: labelMedium stays monospace.
        assertEquals(FontFamily.Monospace, typography.labelMedium.fontFamily)
        listOf(typography.headlineLarge, typography.headlineMedium, typography.headlineSmall)
            .forEach { assertEquals(FontWeight.Bold, it.fontWeight) }
        listOf(typography.titleLarge, typography.titleMedium, typography.titleSmall, typography.labelLarge)
            .forEach { assertEquals(FontWeight.SemiBold, it.fontWeight) }
        // The added small roles keep Material's sizes and spacing.
        assertEquals(baseline.bodySmall.fontSize, typography.bodySmall.fontSize)
        assertEquals(baseline.bodySmall.letterSpacing, typography.bodySmall.letterSpacing)
        assertEquals(baseline.labelSmall.fontSize, typography.labelSmall.fontSize)
        assertEquals(baseline.titleSmall.letterSpacing, typography.titleSmall.letterSpacing)
    }

    @Test
    fun groupedRowsRoundTheGroupWithTheOuterShapeAndMeetWithTheInnerOne() {
        lateinit var outer: CornerBasedShape
        lateinit var inner: CornerBasedShape
        composeRule.setContent {
            SubshellTheme(manageSystemBarContrast = false) {
                outer = MaterialTheme.shapes.large
                inner = MaterialTheme.shapes.extraSmall
            }
        }
        composeRule.waitForIdle()

        assertEquals(outer, groupedRowShape(index = 0, count = 1, outer = outer, inner = inner))
        val first = groupedRowShape(index = 0, count = 3, outer = outer, inner = inner)
        assertEquals(outer.topStart, first.topStart)
        assertEquals(outer.topEnd, first.topEnd)
        assertEquals(inner.bottomStart, first.bottomStart)
        assertEquals(inner.bottomEnd, first.bottomEnd)
        assertEquals(inner, groupedRowShape(index = 1, count = 3, outer = outer, inner = inner))
        val last = groupedRowShape(index = 2, count = 3, outer = outer, inner = inner)
        assertEquals(inner.topStart, last.topStart)
        assertEquals(outer.bottomStart, last.bottomStart)
        assertEquals(outer.bottomEnd, last.bottomEnd)
    }

    @Test
    fun aSetFavouriteStarIsPrimaryEverywhere() {
        var set = Color.Unspecified
        var unset = Color.Unspecified
        var primary = Color.Unspecified
        var onSurfaceVariant = Color.Unspecified
        composeRule.setContent {
            SubshellTheme(manageSystemBarContrast = false) {
                set = favoriteStarTint(favorite = true)
                unset = favoriteStarTint(favorite = false)
                primary = MaterialTheme.colorScheme.primary
                onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
        composeRule.waitForIdle()
        assertEquals(primary, set)
        assertEquals(onSurfaceVariant, unset)
    }

    @Test
    fun theAppReportsItsThemeChoicesForTheLockOverlay() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(initialThemeMode = ThemeMode.SYSTEM, initialInteractionSideConfigured = true)
        state.updateThemeChoice(ThemeChoice.PURE_BLACK)
        state.updateAppTextScale(AppTextScale.LARGER)
        var reported = SubshellThemeSettings()
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                onThemeSettingsChanged = { reported = it },
            )
        }
        composeRule.waitForIdle()
        assertEquals(
            SubshellThemeSettings(mode = ThemeMode.DARK, pureBlack = true, textScale = AppTextScale.LARGER.factor),
            reported,
        )

        composeRule.runOnIdle { state.updateThemeChoice(ThemeChoice.LIGHT) }
        composeRule.waitForIdle()
        assertEquals(ThemeMode.LIGHT, reported.mode)
        assertEquals(false, reported.pureBlack)
    }
}
