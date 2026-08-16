package animato.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService

/**
 * Whether this is a television.
 *
 * A composition local rather than a lookup at each call site so that the system service is asked
 * once, and so a preview can draw the television treatment on a development machine.
 */
val LocalIsTelevision = staticCompositionLocalOf { false }

/**
 * Provides [LocalIsTelevision] from the device's own answer.
 *
 * `UI_MODE_TYPE_TELEVISION` is what the platform sets for a leanback device — the same signal the
 * launcher uses to decide which of the app's intent filters it can see, so the app and the launcher
 * agree about what this device is instead of each guessing.
 */
@Composable
fun ProvideIsTelevision(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isTelevision = remember(context) { context.isTelevision() }
    CompositionLocalProvider(LocalIsTelevision provides isTelevision, content = content)
}

fun Context.isTelevision(): Boolean =
    getSystemService<UiModeManager>()?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

/**
 * Clickable, and obviously reached when a D-pad reaches it.
 *
 * ## Why this exists rather than a theme change
 *
 * On a phone, focus is invisible and irrelevant: the thing you touch is the thing you meant. On a
 * television the only way to know which of forty covers is selected is that it looks different from
 * the other thirty-nine — from three metres away, across a room. Material's focus indication is a
 * low-contrast ripple meant for someone holding the device, and no change of colour turns that into
 * a television affordance.
 *
 * Two cues at once, then: a bright border in the accent colour and a small scale up. A border alone
 * disappears against a cover whose artwork happens to be bright; scale alone is invisible on an item
 * with no neighbours to be bigger than.
 *
 * ## Why it replaces `clickable` rather than wrapping it
 *
 * `clickable` already makes a node focusable and already handles the D-pad centre key. Adding a
 * separate `focusable` beside it produces two focus targets for one item, so a remote needs two
 * presses to cross a row. This *is* the clickable, sharing one interaction source with the
 * highlight, which is also what keeps the ripple and the border talking about the same event.
 *
 * Off a television it is exactly `clickable` and nothing else — no border, no scale, and no change
 * to what a hardware keyboard walks through.
 */
fun Modifier.tvClickable(
    shape: Shape = RoundedCornerShape(FocusRadius),
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isTelevision = LocalIsTelevision.current
    val focused by interactionSource.collectIsFocusedAsState()
    val highlighted = isTelevision && focused

    val scale by animateFloatAsState(
        targetValue = if (highlighted) FOCUS_SCALE else 1f,
        label = "tv-focus-scale",
    )

    val decorated = if (isTelevision) {
        this
            .scale(scale)
            .border(
                width = if (highlighted) FocusBorderWidth else 0.dp,
                color = if (highlighted) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            )
    } else {
        this
    }

    decorated.clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = onClick,
    )
}

private const val FOCUS_SCALE = 1.06f
private val FocusBorderWidth = 3.dp
private val FocusRadius = 12.dp
