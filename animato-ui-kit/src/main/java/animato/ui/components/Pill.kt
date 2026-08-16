package animato.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import animato.ui.theme.LocalAnimatoPalette
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The small label that sits on top of artwork.
 *
 * One component for every pill in the app — NEW, the unread count, the type mark on a mixed grid,
 * the update badge on a source row — because they are the same object with different words in it,
 * and three near-copies is how a set of them drifts into three sizes.
 *
 * The measurements are the brand's: 20 dp tall, 7 dp of side padding, 11 sp at weight 600. That is
 * small, deliberately — a pill is an annotation on a cover, and anything larger starts competing
 * with the cover for the glance.
 */
@Composable
fun Pill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(PillHeight)
            // A one-digit count would otherwise be narrower than it is tall and read as a smudge.
            .defaultMinSize(minWidth = PillHeight)
            .background(containerColor, CircleShape)
            .padding(horizontal = PillSidePadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = PillFontSize,
            fontWeight = FontWeight.SemiBold,
            lineHeight = PillFontSize,
            maxLines = 1,
        )
    }
}

/** Something arrived here since you last looked. Never an action — see the palette's `highlight`. */
@Composable
fun NewPill(modifier: Modifier = Modifier) {
    val palette = LocalAnimatoPalette.current
    Pill(
        text = stringResource(AYMR.strings.label_new),
        containerColor = palette.highlight,
        contentColor = palette.ink,
        modifier = modifier,
    )
}

/** How many items are waiting. Blue, because unread is the thing the app is for. */
@Composable
fun UnviewedPill(count: Long, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Pill(
        text = count.toString(),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    )
}

private val PillHeight = 20.dp
private val PillSidePadding = 7.dp
private val PillFontSize = 11.sp
