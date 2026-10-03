package animato.app.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
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
 * ANIMATO in the brand's geometric capitals, the M in the accent blue. Two colours, so it cannot be
 * a single white mark tinted to the bar the way the previous wordmark was — a tint would paint the M
 * the same as the rest. Two files ship instead, ink letters and white letters, and this picks by the
 * surface rather than by the system's night flag, because the app's own theme setting can disagree
 * with the system and the bar is what the letters have to read against.
 *
 * Sized by height, never by width. The letters are nearly eight times wider than they are tall, and
 * pinning the height is what keeps the bar's rhythm the same as every other screen's title.
 */
@Composable
fun AnimatoWordmark(modifier: Modifier = Modifier) {
    val label = stringResource(MR.strings.app_name)
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Image(
        painter = painterResource(
            if (onDark) R.drawable.animato_wordmark_on_dark else R.drawable.animato_wordmark_on_light,
        ),
        contentDescription = null,
        modifier = modifier
            .height(WordmarkHeight)
            // The mark *is* the title, so it has to be read out as the name rather than skipped.
            .semantics { contentDescription = label },
        contentScale = ContentScale.FillHeight,
    )
}

/**
 * Under the cap height of the title it replaces. Geometric capitals with no descenders fill their
 * whole box, so matching the type's line height would make the mark shout over the bar's icons.
 */
private val WordmarkHeight = 18.dp
