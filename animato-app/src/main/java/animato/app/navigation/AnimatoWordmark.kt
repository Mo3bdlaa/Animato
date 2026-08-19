package animato.app.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mo3bdlaa.animato.R
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The brand's own letters where Home used to print its name.
 *
 * Home's top bar said "Animato" in the interface font — a label, not a mark. The actual logo could
 * not be put there: it is a picture, with a cream ground and a black panel, and on an ink bar that
 * panel reads as a grey slab. So the brush letters were cut out of the artwork and left white on
 * transparency, which is what makes this a *tinted* image rather than a picture: the mark takes the
 * bar's own content colour, so it is white in the dark theme and ink in the light one without a
 * second asset existing.
 *
 * Sized by height, never by width. The letters are 2.6 times wider than they are tall, and pinning
 * the height is what keeps the bar's rhythm the same as every other screen's title.
 */
@Composable
fun AnimatoWordmark(modifier: Modifier = Modifier) {
    val label = stringResource(MR.strings.app_name)
    Image(
        painter = painterResource(R.drawable.animato_wordmark),
        contentDescription = null,
        modifier = modifier
            .height(WordmarkHeight)
            // The mark *is* the title, so it has to be read out as the name rather than skipped.
            .semantics { contentDescription = label },
        contentScale = ContentScale.FillHeight,
        colorFilter = ColorFilter.tint(LocalContentColor.current),
    )
}

/**
 * Slightly under the cap height of the title it replaces.
 *
 * The brush strokes have ragged tops and tails, so matching the type's height exactly makes the
 * mark look bigger than the text ever did and crowds the bar.
 */
private val WordmarkHeight = 22.dp
