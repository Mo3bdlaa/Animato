package animato.anime.player

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The ripple the player's controls use: white on a dark video, black on a light one, and far
 * fainter than the app's, because a ripple over moving footage reads as a flaw rather than feedback.
 *
 * Aniyomi declared this inside Mihon's `TachiyomiTheme.kt`, which is why it vanished when we stopped
 * editing that file. It describes the player and nothing else, so it belongs with the player.
 */
val playerRippleConfiguration
    @Composable get() = RippleConfiguration(
        color = if (isSystemInDarkTheme()) Color.White else Color.Black,
        rippleAlpha = RippleAlpha(
            draggedAlpha = RIPPLE_ALPHA,
            focusedAlpha = RIPPLE_ALPHA,
            hoveredAlpha = RIPPLE_ALPHA,
            pressedAlpha = RIPPLE_ALPHA,
        ),
    )

private const val RIPPLE_ALPHA = .1f
