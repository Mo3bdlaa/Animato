package animato.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A theme, expressed as the handful of decisions that actually differ between themes.
 *
 * Everything Material needs — some forty colour roles, twice over for light and dark — is derived
 * from these by [colorScheme]. That is the point: changing how Animato looks means editing the
 * values below, not auditing eighty hand-written tokens for the ones that no longer agree.
 *
 * Adding a second theme is adding one more object to [AnimatoPalettes]. Nothing else has to change,
 * which is what leaves the door open to a theme picker later without reworking any of this.
 *
 * The defaults come from docs/BRANDING.md section 2 and must stay in step with
 * `animato-app/src/main/res/values/animato_brand.xml`, which is what the platform draws the launcher
 * icon and splash window from before any of this code runs.
 */
data class AnimatoPalette(
    /** Shown in a theme picker. Not used for anything else yet. */
    val name: String,

    /** Primary actions, progress, active states. An accent — never the interface. */
    val accent: Color,

    /** Text and icons drawn on top of [accent]. */
    val onAccent: Color,

    /** Dark-mode background. Also the typography colour in light mode. */
    val ink: Color,

    /** Light-mode background. Warm manga paper, deliberately not pure white. */
    val paper: Color,

    /** Secondary text, in both modes. */
    val muted: Color,

    /**
     * Warnings and destructive actions.
     *
     * Kept separate from [accent] on purpose: if a palette's accent is itself red, a red error means
     * "do this" and "something is wrong" look alike, which dilutes both. That was the case while the
     * accent was red and is why the error colour was orange then. With a blue accent there is no
     * clash, so this is a conventional red again — which is what users read as an error without
     * being taught.
     */
    val error: Color,
)

object AnimatoPalettes {
    /**
     * Animato's own. Blue on ink black, warm paper in the light.
     *
     * The accent was red until the brand moved to blue: calmer to read for long sessions, and it
     * keeps a thread back to Tachiyomi and Mihon without looking like a clone of either. It also
     * measures better where it matters most — white on the accent, which is every filled button —
     * going from 4.24:1 to 5.59:1, clearing AA for button labels where the red did not.
     *
     * The trade is the other direction: accent drawn *as text* on the ink background is 3.58:1
     * against the red's 4.72:1. That passes AA for large text and UI components, which is what the
     * accent is used for — tab labels, icons, progress — and no single colour clears 4.5:1 in both
     * directions at once, so this is the side worth being good at.
     */
    val Default = AnimatoPalette(
        name = "Animato",
        accent = Color(0xFF4169A1),
        onAccent = Color(0xFFFFFFFF),
        ink = Color(0xFF08080C),
        paper = Color(0xFFF2EEE5),
        muted = Color(0xFF9A9690),
        error = Color(0xFFBA1A1A),
    )

    /** Every palette the app knows about. A theme picker would iterate this. */
    val all: List<AnimatoPalette> = listOf(Default)
}

/**
 * Derives a full Material colour scheme from a palette.
 *
 * The derivation is all blending towards or away from the background, which is why six inputs are
 * enough. Surfaces step away from the background by a fixed ladder, containers are the accent mixed
 * into the background, and outlines sit between the background and the muted tone. Change the
 * accent and every accented surface in the app moves with it, consistently.
 */
fun AnimatoPalette.colorScheme(isDark: Boolean, isAmoled: Boolean = false): ColorScheme {
    val background = when {
        !isDark -> paper
        isAmoled -> Color.Black
        else -> ink
    }
    val onBackground = if (isDark) paper else ink

    // In dark mode raised surfaces get lighter; in light mode they get darker. `step` is a small
    // move towards the opposite end, applied repeatedly to build the elevation ladder.
    val far = if (isDark) Color.White else ink
    fun raise(fraction: Float) = lerp(background, far, fraction)

    // AMOLED means the background is true black on purpose, so the ladder has to stay tight or the
    // cards read as grey boxes floating on black instead of the near-flat surface the brand asks for.
    val ladder = if (isDark && isAmoled) {
        listOf(0f, 0.03f, 0.05f, 0.08f, 0.11f)
    } else if (isDark) {
        listOf(0.01f, 0.03f, 0.06f, 0.09f, 0.12f)
    } else {
        // Light mode's lowest container is the brand's white — lighter than the paper, not darker.
        listOf(-1f, 0.02f, 0.04f, 0.08f, 0.12f)
    }
    fun container(index: Int): Color =
        if (ladder[index] < 0f) Color.White else raise(ladder[index])

    // `muted` is picked to sit on the dark background. On paper it lands at about 2.5:1 against the
    // background, well under the 4.5:1 that body text needs, so in light mode it is pulled towards
    // the foreground until it reads. Secondary text is most of the text on a library screen.
    val secondaryText = if (isDark) muted else lerp(muted, onBackground, 0.55f)

    // The error colour is drawn as text and icons on the background too, and needs the same
    // treatment — but in whichever direction that mode requires. Blending towards `onBackground`
    // does both: it darkens a red on paper and lightens the same red on ink, from one rule. A
    // conventional error red is dark, and left alone it reads at 3.1:1 against the ink background.
    val errorOnBackground = lerp(error, onBackground, 0.35f)

    val accentContainer = lerp(background, accent, if (isDark) 0.28f else 0.18f)
    val onAccentContainer = if (isDark) lerp(accent, Color.White, 0.7f) else lerp(accent, ink, 0.6f)

    val errorContainer = lerp(background, errorOnBackground, if (isDark) 0.30f else 0.20f)
    val onErrorContainer =
        if (isDark) lerp(errorOnBackground, Color.White, 0.6f) else lerp(errorOnBackground, onBackground, 0.65f)

    // Borders are thin and low-contrast per BRANDING.md section 5: elevation comes from surface,
    // not from shadow, so the outline must not do the shadow's job.
    val outline = lerp(background, muted, 0.65f)
    val outlineVariant = lerp(background, muted, 0.25f)

    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        inversePrimary = lerp(accent, if (isDark) ink else paper, 0.35f),

        secondary = lerp(onBackground, background, 0.25f),
        onSecondary = background,
        secondaryContainer = container(2),
        onSecondaryContainer = onBackground,

        tertiary = lerp(onBackground, background, 0.35f),
        onTertiary = background,
        tertiaryContainer = container(3),
        onTertiaryContainer = onBackground,

        background = background,
        onBackground = onBackground,
        surface = background,
        onSurface = onBackground,
        surfaceVariant = container(2),
        onSurfaceVariant = secondaryText,

        surfaceContainerLowest = container(0),
        surfaceContainerLow = container(1),
        surfaceContainer = container(2),
        surfaceContainerHigh = container(3),
        surfaceContainerHighest = container(4),

        outline = outline,
        outlineVariant = outlineVariant,

        error = errorOnBackground,
        onError = if (isDark) lerp(errorOnBackground, ink, 0.8f) else Color.White,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,

        inverseSurface = onBackground,
        inverseOnSurface = background,
        scrim = Color.Black,
    )
}
