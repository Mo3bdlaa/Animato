package animato.ui.tv

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * What focus looks like, for every clickable in the app at once.
 *
 * ## Why this exists instead of more call sites
 *
 * [tvClickable] was the first answer and it does not scale: it has to be written at each call site,
 * it reached six screens, and a television has dozens. The screens it missed were the ones a remote
 * needs most — settings, sources, the Stremio addon list — where a row is reachable, because
 * `Modifier.clickable` is focusable by definition, and reaching it looks like nothing at all. A
 * control you can select but cannot see selected is indistinguishable from a broken screen, which
 * is exactly what a television reported it as: *"I cannot scroll down or select anything."*
 *
 * `Modifier.clickable` reads `LocalIndication` for its feedback. Replacing that one value at the
 * root therefore reaches every clickable in the app — including the screens inherited from Mihon,
 * which this fork does not edit and could not have annotated one by one.
 *
 * ## Why it replaces the ripple rather than joining it
 *
 * A ripple is a touch affordance: it answers "my finger landed there". No television has a finger,
 * and the thing a remote needs answered is the question a ripple never addresses — *which* of the
 * forty rows is selected right now, from three metres away. So on a television this is the whole
 * indication and the ripple is not drawn; on everything else `LocalIndication` is untouched and the
 * ripple is exactly as it was.
 *
 * A border alone, and no scale: this draws on rows and list items of every shape, where growing an
 * item would shove its neighbours around. The grid covers that *can* afford to grow keep using
 * [tvClickable], which adds the scale on top.
 */
data class TvFocusIndication(
    private val color: Color,
    private val width: Dp = DefaultWidth,
    private val cornerRadius: Dp = DefaultCornerRadius,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FocusRingNode(interactionSource, color, width, cornerRadius)

    private class FocusRingNode(
        private val interactionSource: InteractionSource,
        private val color: Color,
        private val width: Dp,
        private val cornerRadius: Dp,
    ) : Modifier.Node(), DrawModifierNode {

        private var isFocused = false

        override fun onAttach() {
            coroutineScope.launch {
                /*
                 * Counted rather than toggled.
                 *
                 * One node can hold more than one focus interaction at a time — a row that is both
                 * focused and hosting a focused child reports two — and treating Unfocus as "off"
                 * would drop the ring while the item is still selected. Counting makes the ring
                 * leave when the last one does.
                 */
                var focusCount = 0
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is FocusInteraction.Focus -> focusCount++
                        is FocusInteraction.Unfocus -> focusCount--
                    }
                    val focused = focusCount > 0
                    if (focused != isFocused) {
                        isFocused = focused
                        invalidateDraw()
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (!isFocused) return

            // Inset by half the stroke so the border sits inside the item's own bounds. Drawn on
            // the centre line instead, half of it falls outside and is clipped by whatever the
            // item is sitting in — which reads as a thinner ring on some rows and not others.
            val stroke = width.toPx()
            val inset = stroke / 2
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(cornerRadius.toPx()),
                style = Stroke(width = stroke),
            )
        }
    }

    private companion object {
        val DefaultWidth = 3.dp
        val DefaultCornerRadius = 10.dp
    }
}
