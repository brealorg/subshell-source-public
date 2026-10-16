package app.subshell.prototype.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

val SubshellOrange = Color(0xFFFF8A3D)
val SubshellOrangeSoft = Color(0xFFFFB68C)
val SubshellVoteUp = Color(0xFFFF4500)
val SubshellVoteDown = Color(0xFF7193FF)
// ISSUE218_VOTE_TINTS_PER_THEME_V1: the light theme's vote tints. The tints
// above are light and read on dark surfaces only (#FFB68C on the light
// surface was 1.6:1); these reach 4.5:1 on the light surfaces.
val SubshellVoteNeutralLight = Color(0xFF914300)
val SubshellVoteUpLight = Color(0xFFC23500)
val SubshellVoteDownLight = Color(0xFF3A5BD9)
val SubshellInk = Color(0xFF17120F)
// M3221_DARK_SURFACE_IS_TONE_6_V1 (#221 foundations-09): the dark surface equalled
// surfaceContainerLow, so sheets and the drawer did not separate; like Material's
// baseline it now sits at the background tone.
val SubshellSurface = Color(0xFF17120F)
val SubshellCream = Color(0xFFFFF8F4)

val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = SubshellOrangeSoft,
    onPrimary = Color(0xFF542000),
    primaryContainer = Color(0xFF733008),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFE4BFAE),
    onSecondary = Color(0xFF422A20),
    secondaryContainer = Color(0xFF5A4035),
    onSecondaryContainer = Color(0xFFFFDBCB),
    tertiary = Color(0xFFD5C690),
    onTertiary = Color(0xFF393006),
    tertiaryContainer = Color(0xFF514719),
    onTertiaryContainer = Color(0xFFF2E2A8),
    background = SubshellInk,
    onBackground = Color(0xFFF2DFD7),
    surface = SubshellSurface,
    onSurface = Color(0xFFF2DFD7),
    surfaceVariant = Color(0xFF50443F),
    onSurfaceVariant = Color(0xFFD6C3BA),
    outline = Color(0xFF9E8E86),
    outlineVariant = Color(0xFF50443F),
    // LOWUI_WARM_SURFACE_CONTAINERS_V1 (OCB-292): Material's tones for these
    // roles (4/6/10/12/17/22/24) in Subshell's warm neutral instead of the
    // baseline's lavender grey; the steps between them are unchanged.
    surfaceDim = SubshellInk,
    surfaceBright = Color(0xFF403833),
    surfaceContainerLowest = Color(0xFF130D09),
    surfaceContainerLow = Color(0xFF211A16),
    surfaceContainer = Color(0xFF251E1A),
    surfaceContainerHigh = Color(0xFF302824),
    surfaceContainerHighest = Color(0xFF3B332F),
    // M3221_INVERSE_ROLES_IN_THE_WARM_PALETTE_V1 (#221 foundations-08): were baseline lavender.
    inverseSurface = Color(0xFFF2DFD7),
    inverseOnSurface = Color(0xFF382E2A),
    inversePrimary = Color(0xFF914300),
    scrim = Color.Black,
)

val LightColors = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF914300),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF301400),
    secondary = Color(0xFF755748),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCB),
    onSecondaryContainer = Color(0xFF2B160D),
    tertiary = Color(0xFF695F30),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1E3A8),
    onTertiaryContainer = Color(0xFF211B00),
    background = SubshellCream,
    onBackground = Color(0xFF211A17),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF211A17),
    surfaceVariant = Color(0xFFF4DED4),
    onSurfaceVariant = Color(0xFF53443D),
    outline = Color(0xFF85736B),
    outlineVariant = Color(0xFFD8C2B8),
    // LOWUI_WARM_SURFACE_CONTAINERS_V1 (OCB-292): tones 87/98/100/96/94/92/90.
    surfaceDim = Color(0xFFDFD9D5),
    surfaceBright = SubshellCream,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F2EE),
    surfaceContainer = Color(0xFFF3EDE9),
    surfaceContainerHigh = Color(0xFFEEE7E3),
    surfaceContainerHighest = Color(0xFFE8E1DD),
    // M3221_INVERSE_ROLES_IN_THE_WARM_PALETTE_V1
    inverseSurface = Color(0xFF382E2A),
    inverseOnSurface = Color(0xFFFFEDE5),
    inversePrimary = Color(0xFFFFB68C),
    scrim = Color.Black,
)

// ISSUE31_PURE_BLACK_DARK_THEME_V1: the dark scheme on true black for OLED
// screens. Background and base surfaces go black; the container tones step
// up in neutral greys so cards, sheets, dividers and comment depth bars stay
// distinguishable. Accent, outline and variant colours come from the base
// scheme, so it works on top of Subshell orange and dynamic colour alike.
internal fun pureBlackColorScheme(base: ColorScheme): ColorScheme = base.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF262626),
    surfaceBright = Color(0xFF2E2E2E),
)
