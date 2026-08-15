package animato.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import eu.kanade.domain.ui.UiPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Wraps the app in the Animato palette.
 *
 * This restyles Mihon's screens as well as ours, and does so without editing any of them: Mihon's
 * 186 presentation files carry only 4 hard-coded colours between them and read
 * `MaterialTheme.colorScheme` for everything else. Whoever owns the root composable therefore owns
 * the look of every screen below it.
 *
 * The composition locals below are not decoration. Mihon's `setComposeContent` sets exactly these
 * two, so its screens are written assuming them; a root that omits them would leave Mihon's text
 * at the wrong default size and its icons the wrong colour. Matching them is what makes this a
 * drop-in replacement rather than a reskin that breaks things.
 */
@Composable
fun AnimatoTheme(
    isDark: Boolean? = null,
    isAmoled: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    BaseAnimatoTheme(
        // Mihon's ThemingDelegate has already pushed the user's light/dark/system choice into
        // AppCompatDelegate by the time any of this composes, so the system value is the setting.
        isDark = isDark ?: isSystemInDarkTheme(),
        isAmoled = isAmoled ?: uiPreferences.themeDarkAmoled.get(),
        content = content,
    )
}

/**
 * The same theme without the Injekt lookup, for `@Preview` and for tests. Composable previews run
 * outside the application, where the dependency graph does not exist.
 */
@Composable
fun AnimatoPreviewTheme(
    isDark: Boolean = true,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) = BaseAnimatoTheme(isDark = isDark, isAmoled = isAmoled, content = content)

@Composable
private fun BaseAnimatoTheme(
    isDark: Boolean,
    isAmoled: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(isDark, isAmoled) {
        animatoColorScheme(isDark = isDark, isAmoled = isAmoled)
    }
    MaterialExpressiveTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodySmall,
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}

private fun animatoColorScheme(isDark: Boolean, isAmoled: Boolean): ColorScheme = when {
    !isDark -> AnimatoLightColorScheme
    isAmoled -> AnimatoDarkColorScheme.toAmoled()
    else -> AnimatoDarkColorScheme
}
