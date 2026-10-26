package app.subshell.prototype.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import app.subshell.prototype.media.findActivity
import android.content.res.Configuration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat
import app.subshell.prototype.ui.LocalCodeFontFamily

enum class ThemeMode { SYSTEM, LIGHT, DARK }

internal fun systemBarIconsAreDark(darkTheme: Boolean): Boolean = !darkTheme

private val BaselineTypography = Typography()

// M3221_COMPLETE_TYPE_SCALE_V1 (#221): every role is defined here, so a screen
// takes its weight from the role instead of overriding it per call. Titles and
// large labels are SemiBold, headlines Bold, body Normal; labelMedium stays
// monospace (owner, 2026-10-12). Roles added in #221 keep Material's
// baseline sizes and spacing in the app's family.
private val SubshellTypography = Typography(
    displayLarge = BaselineTypography.displayLarge.copy(fontFamily = FontFamily.SansSerif),
    displayMedium = BaselineTypography.displayMedium.copy(fontFamily = FontFamily.SansSerif),
    displaySmall = BaselineTypography.displaySmall.copy(fontFamily = FontFamily.SansSerif),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = BaselineTypography.headlineMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = BaselineTypography.titleSmall.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = BaselineTypography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = BaselineTypography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
)

private val SubshellShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

// M3221_GROUPED_ROWS_USE_THE_SHAPE_SCALE_V1 (#221): rows that read as one group
// (chat runs, the diagnostics log) round the group's outer corners with [outer]
// and meet each other with [inner], both taken from the theme's shape scale.
internal fun groupedRowShape(
    index: Int,
    count: Int,
    outer: CornerBasedShape,
    inner: CornerBasedShape,
): CornerBasedShape = when {
    count <= 1 -> outer
    index == 0 -> outer.copy(bottomStart = inner.bottomStart, bottomEnd = inner.bottomEnd)
    index == count - 1 -> outer.copy(topStart = inner.topStart, topEnd = inner.topEnd)
    else -> inner
}

// M3221_APP_LOCK_FOLLOWS_THE_APP_THEME_V1 (#221): the reader's theme choices,
// reported by SubshellApp so the app-lock overlay (drawn outside it, over every
// window) uses the same theme, dynamic colour and text size.
data class SubshellThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = app.subshell.prototype.ui.DefaultSubshellSettings.dynamicColor,
    val pureBlack: Boolean = false,
    val textScale: Float = 1f,
)

// M3221_SURFACE_LEVELS_ARE_COLOUR_ROLES_V1 (#221): a level is chosen with the
// surfaceContainer* roles, never with tonalElevation. Tonal elevation tints only
// a Surface whose colour is exactly colorScheme.surface; on every other colour it
// did nothing, and the tint does not follow the palette's own container tones.
// M3221_ROWS_TAKE_THEIR_CONTAINERS_COLOUR_V1: a ListItem or tab row would paint
// colorScheme.surface; it is transparent, so inside a sheet or pane it takes the
// container's colour (on a screen background and surface are the same colour).
@Composable
fun SubshellTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    pureBlack: Boolean = false,
    textScale: Float = 1f,
    codeFontFamily: FontFamily = FontFamily.Monospace,
    manageSystemBarContrast: Boolean = true,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val view = LocalView.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }.let { scheme -> if (dark && pureBlack) pureBlackColorScheme(scheme) else scheme }

    if (manageSystemBarContrast && !view.isInEditMode) {
        // ISSUE45_BAR_ICONS_FOLLOW_THE_APP_THEME_AFTER_ROTATION_V1 (#45): a
        // rotation is handled in place, and MainActivity.onConfigurationChanged
        // calls enableEdgeToEdge(), which sets the bar icons from the system's
        // night mode. Keyed on the configuration, the theme sets its own again.
        key(LocalConfiguration.current) {
            SideEffect {
                val window = view.context.findActivity()?.window ?: return@SideEffect
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = systemBarIconsAreDark(dark)
                    isAppearanceLightNavigationBars = systemBarIconsAreDark(dark)
                }
            }
        }
    }

    // ISSUE32_APP_TEXT_SIZE_V1: the in-app text size multiplies the system font scale.
    // ISSUE220_APP_TEXT_SIZE_KEEPS_NONLINEAR_SCALING_V1: Density(Float, Float) is
    // linear and dropped Android 14's non-linear font scaling (large headlines
    // grew in full). The combined scale goes through the platform's converter
    // up to its largest table (2.0); only the part above that is linear, so
    // sizes keep growing smoothly instead of jumping at 2.0.
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scaledDensity = remember(density, textScale, configuration, context) {
        if (textScale == 1f) {
            density
        } else {
            appTextScaleDensity(context, configuration, density.fontScale * textScale)
        }
    }
    CompositionLocalProvider(
        LocalCodeFontFamily provides codeFontFamily,
        LocalDensity provides scaledDensity,
        LocalReduceMotion provides reduceMotion,
    ) {
        // EXPRESSIVE01_SPRING_MOTION_V1 (owner 2026-10-18, test build): M3
        // Expressive's spring motion scheme; the app's own colours, type and
        // shapes stay. Reduce motion keeps the standard scheme, which does not
        // overshoot.
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            typography = SubshellTypography,
            shapes = SubshellShapes,
            content = content,
        )
    }
}

private const val PLATFORM_MAX_FONT_SCALE_TABLE = 2f

/**
 * ISSUE249_IN_SCREEN_PAGES_FOLLOW_THE_BACK_GESTURE_V1: the app's Reduce motion
 * (its own switch or Android's Remove animations), for motion that has no
 * SubshellState at hand, such as the predictive back of a page inside a screen.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** A Density for [combinedScale]: the platform's non-linear curve, linear above 2.0. */
internal fun appTextScaleDensity(
    context: Context,
    configuration: Configuration,
    combinedScale: Float,
): Density {
    val curveScale = combinedScale.coerceAtMost(PLATFORM_MAX_FONT_SCALE_TABLE)
    val curve = Density(
        context.createConfigurationContext(Configuration(configuration).apply { fontScale = curveScale }),
    )
    val extra = combinedScale / curveScale
    return if (extra <= 1f) curve else LinearAboveCurveDensity(curve, extra)
}

private class LinearAboveCurveDensity(
    private val curve: Density,
    private val extra: Float,
) : Density {
    override val density: Float get() = curve.density
    override val fontScale: Float get() = curve.fontScale * extra
    override fun TextUnit.toDp(): Dp = with(curve) { this@toDp.toDp() } * extra
    override fun Dp.toSp(): TextUnit = with(curve) { (this@toSp / extra).toSp() }
}
