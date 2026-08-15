package animato.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The Animato palette, as Material 3 colour schemes.
 *
 * Mihon builds its schemes through an `internal abstract class BaseColorScheme`, selected from a
 * `private` map keyed by an `enum`. None of the three can be extended from another module, so this
 * is not a theme registered with Mihon — it is our own, applied above Mihon's screens by whoever
 * owns the root composable. See ARCHITECTURE.md, "Why phase 6 has to own the launcher activity".
 *
 * The tokens come from docs/BRANDING.md section 2 and must stay in step with
 * `animato-app/src/main/res/values/animato_brand.xml`, which is what the platform draws the
 * launcher icon and splash window from before any of this code runs.
 */
internal object AnimatoPalette {
    /** Primary actions, progress, active states. An accent — never the interface. */
    val AccentRed = Color(0xFFE5392F)

    /** Dark background; light-mode typography. */
    val InkBlack = Color(0xFF08080C)

    /** Cards and elevated surfaces in dark mode. */
    val Surface = Color(0xFF151516)

    /** Light background. Warm manga paper, deliberately not pure white. */
    val Paper = Color(0xFFF2EEE5)

    /** Secondary text. */
    val Muted = Color(0xFF9A9690)

    val White = Color(0xFFFFFFFF)
}

/**
 * Red is the accent, and Material also wants a red for errors. Keeping the default error red would
 * put two similar reds on screen meaning different things — "primary action" and "something is
 * wrong" — which is exactly the dilution BRANDING.md warns about.
 *
 * So errors are pushed towards orange. It stays clearly a warning colour, it does not read as a
 * call to action, and it survives being next to the accent.
 */
private val ErrorDark = Color(0xFFFFB4A0)
private val ErrorContainerDark = Color(0xFF6B2410)
private val ErrorLight = Color(0xFF8F3A16)
private val ErrorContainerLight = Color(0xFFFFDBCC)

internal val AnimatoDarkColorScheme: ColorScheme = darkColorScheme(
    primary = AnimatoPalette.AccentRed,
    onPrimary = AnimatoPalette.White,
    primaryContainer = Color(0xFF5C1410),
    onPrimaryContainer = Color(0xFFFFDAD5),
    inversePrimary = Color(0xFFB3261E),

    secondary = Color(0xFFC9C4BC),
    onSecondary = Color(0xFF17171A),
    secondaryContainer = Color(0xFF2A2A2C),
    onSecondaryContainer = Color(0xFFE5E1DA),

    tertiary = Color(0xFFD8D2C6),
    onTertiary = Color(0xFF17171A),
    tertiaryContainer = Color(0xFF33322E),
    onTertiaryContainer = Color(0xFFEFE9DC),

    background = AnimatoPalette.InkBlack,
    onBackground = AnimatoPalette.Paper,
    surface = AnimatoPalette.InkBlack,
    onSurface = AnimatoPalette.Paper,
    surfaceVariant = AnimatoPalette.Surface,
    onSurfaceVariant = AnimatoPalette.Muted,

    surfaceContainerLowest = Color(0xFF050507),
    surfaceContainerLow = Color(0xFF101012),
    surfaceContainer = AnimatoPalette.Surface,
    surfaceContainerHigh = Color(0xFF1D1D1F),
    surfaceContainerHighest = Color(0xFF252527),

    // Thin and low-contrast, per BRANDING.md section 5. Elevation comes from surface, not shadow.
    outline = Color(0xFF4A4A4D),
    outlineVariant = Color(0xFF2A2A2C),

    error = ErrorDark,
    onError = Color(0xFF3D1000),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFFFDBCC),

    inverseSurface = AnimatoPalette.Paper,
    inverseOnSurface = AnimatoPalette.InkBlack,
    scrim = Color(0xFF000000),
)

internal val AnimatoLightColorScheme: ColorScheme = lightColorScheme(
    primary = AnimatoPalette.AccentRed,
    onPrimary = AnimatoPalette.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410002),
    inversePrimary = Color(0xFFFFB4AB),

    secondary = Color(0xFF5A5751),
    onSecondary = AnimatoPalette.White,
    secondaryContainer = Color(0xFFE6E1D7),
    onSecondaryContainer = Color(0xFF1B1A17),

    tertiary = Color(0xFF4C4A44),
    onTertiary = AnimatoPalette.White,
    tertiaryContainer = Color(0xFFDFD9CC),
    onTertiaryContainer = Color(0xFF17160F),

    background = AnimatoPalette.Paper,
    onBackground = AnimatoPalette.InkBlack,
    surface = AnimatoPalette.Paper,
    onSurface = AnimatoPalette.InkBlack,
    surfaceVariant = Color(0xFFE6E1D7),
    onSurfaceVariant = Color(0xFF55524C),

    // Light mode runs the other way: lowest is the lightest, which is where the brand's white
    // surfaces sit — cards read as raised off the paper without needing a shadow.
    surfaceContainerLowest = AnimatoPalette.White,
    surfaceContainerLow = Color(0xFFFBF9F4),
    surfaceContainer = Color(0xFFF7F4EC),
    surfaceContainerHigh = Color(0xFFEFEADF),
    surfaceContainerHighest = Color(0xFFE8E2D5),

    outline = Color(0xFF87837B),
    outlineVariant = Color(0xFFD5D0C4),

    error = ErrorLight,
    onError = AnimatoPalette.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF351000),

    inverseSurface = AnimatoPalette.InkBlack,
    inverseOnSurface = AnimatoPalette.Paper,
    scrim = Color(0xFF000000),
)

/**
 * AMOLED screens draw true black for free, so Mihon offers it as a preference and users of dark
 * themes expect it. The container ladder has to move too: leaving it at the values above would put
 * visible grey cards on a black background instead of the near-flat surface the brand asks for.
 */
internal fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    onBackground = AnimatoPalette.Paper,
    surface = Color.Black,
    onSurface = AnimatoPalette.Paper,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0B),
    surfaceContainer = Color(0xFF101011),
    surfaceContainerHigh = Color(0xFF17171A),
    surfaceContainerHighest = Color(0xFF1E1E21),
)
