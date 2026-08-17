package animato.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The one shape an empty screen takes: a mark, a sentence, and at most one way forward.
 *
 * ## Why it is a component and not a paragraph
 *
 * Every empty screen was growing its own — a centred sentence here, a card with a button there,
 * nothing at all on the download queue. From a device, of that last one: *"why doesn't the empty
 * list look like the design I gave you?"* Fair: the design sheet is specific, and says an empty
 * state is a brand illustration, **one** sentence and **one** button, and that the sentence names
 * the cause rather than shrugging.
 *
 * The rule that matters most is the last one. *Nothing here* tells somebody the screen is empty,
 * which they can see. What they cannot see is why, or what would change it, and that is the whole
 * job of the two lines below the mark.
 *
 * ## The mark
 *
 * A speed-line sweep, drawn rather than shipped as an asset — it has to hold its edges at any size
 * and take the theme's own colour, and a PNG does neither. It carries no meaning by itself and is
 * not asked to: it is there so the screen reads as composed rather than as broken, which is exactly
 * what a lone line of grey text in the middle of a black screen reads as.
 */
@Composable
fun AnimatoEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EmptyStateSidePadding, vertical = EmptyStateTopPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EmptyStateGap),
    ) {
        SpeedLineSweep()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * Four lines of motion, fading as they trail off.
 *
 * Mirrored under RTL along with everything else — a sweep that trails the wrong way in an Arabic
 * layout reads as arriving rather than leaving.
 */
@Composable
private fun SpeedLineSweep() {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        modifier = Modifier
            .size(width = SweepWidth, height = SweepHeight)
            .drawBehind {
                val stroke = SweepStroke.toPx()
                val gap = size.height / (SWEEP_LINES + 1)
                repeat(SWEEP_LINES) { index ->
                    // The longest line leads and the rest fall behind it, which is what makes the
                    // shape read as one movement instead of four rules.
                    val fraction = 1f - index * SWEEP_FALLOFF
                    val length = size.width * fraction
                    val y = gap * (index + 1)
                    val startX = if (mirrored) size.width else 0f
                    val endX = if (mirrored) size.width - length else length
                    drawLine(
                        color = if (index == 0) accent else ink.copy(alpha = SWEEP_ALPHA * fraction),
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            },
    )
}

private val EmptyStateSidePadding = 32.dp
private val EmptyStateTopPadding = 48.dp
private val EmptyStateGap = 16.dp
private val SweepWidth = 96.dp
private val SweepHeight = 40.dp
private val SweepStroke = 4.dp

private const val SWEEP_LINES = 4
private const val SWEEP_FALLOFF = 0.22f
private const val SWEEP_ALPHA = 0.45f
