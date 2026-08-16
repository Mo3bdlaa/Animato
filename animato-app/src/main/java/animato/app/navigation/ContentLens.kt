package animato.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import animato.domain.content.ContentFilter
import animato.domain.content.ContentPreferences
import animato.domain.content.ContentType
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The lens: one global answer to "am I looking at anime, manga, or everything".
 *
 * ## One value, not two
 *
 * This used to be a pair — a `contentType` with no `ALL` for the single-half destinations, and a
 * `libraryFilter` with one for the library — and the pair is what produced the bug the first device
 * session found: the home screen's two-state switch said Anime while the Continue rail, which read
 * neither preference, showed manga chapters. Two sources of truth for one question always resolve
 * that way eventually.
 *
 * So [ContentFilter] is now the whole of it. Screens that genuinely cannot draw both halves resolve
 * `ALL` to a default through [contentTypeOrDefault]; every screen that can draw both reads the
 * filter directly. Writing it anywhere means every other screen agrees when you arrive.
 *
 * ## Why the icon carries the state
 *
 * The control is a single top-bar button rather than a chip row, and the glyph is the state: a full
 * outlined circle for `ALL`, the same circle **half-filled in blue** when the lens is narrowed. That
 * is the answer to the hidden-filter problem — a filtered app has to *look* filtered from across the
 * room, or the first question anyone asks is why their library is empty — without spending a whole
 * 56 dp row on three words. See docs/branding/design.md.
 */
@Composable
fun contentLens(): ContentFilter {
    val preferences = remember { Injekt.get<ContentPreferences>() }
    val filter by preferences.contentFilter.collectAsState()
    return filter
}

fun setContentLens(filter: ContentFilter) {
    Injekt.get<ContentPreferences>().contentFilter.set(filter)
}

/**
 * The lens as a single content type, for a destination that can only draw one half.
 *
 * `ALL` has to become something concrete on those screens; it becomes [fallback]. That is a
 * rendering decision and not a write — the lens itself stays `ALL`, so leaving the screen does not
 * silently narrow what everything else shows.
 */
@Composable
fun contentTypeOrDefault(fallback: ContentType = ContentType.MANGA): ContentType =
    when (contentLens()) {
        ContentFilter.ALL -> fallback
        ContentFilter.ANIME -> ContentType.ANIME
        ContentFilter.MANGA -> ContentType.MANGA
    }

/**
 * The lens button: one icon that is its own state, and a menu behind it.
 *
 * Sits in the top bar of every screen that lists content — home, library, discover, updates and
 * search — in the same slot each time, so the control is learned once.
 */
@Composable
fun LensButton(modifier: Modifier = Modifier) {
    val lens = contentLens()
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            LensGlyph(lens = lens)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ContentFilter.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = {
                        setContentLens(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == lens) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
            HorizontalDivider()
            // The caption is the whole reason the menu is not just three taps: it tells you the
            // choice is global before you discover that by surprise on another screen.
            Text(
                text = stringResource(AYMR.strings.lens_applies_everywhere),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * A circle, half-filled when the lens is narrowed.
 *
 * Drawn rather than assembled from two icons because the two states have to be the *same* circle —
 * an outline that gains a filled half, not one shape swapped for another. Anything that changes
 * silhouette reads as a different control rather than the same control in a different state.
 *
 * The filled half is on the leading side in both directions: the drawing is mirrored under RTL by
 * Compose's layout direction, which is why the arc starts at 90° and sweeps 180° rather than being
 * positioned by hand.
 */
@Composable
private fun LensGlyph(lens: ContentFilter) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val active = MaterialTheme.colorScheme.primary
    val narrowed = lens != ContentFilter.ALL
    val ring = if (narrowed) active else outline

    Box(
        modifier = Modifier
            .size(LENS_GLYPH_SIZE)
            .clip(CircleShape)
            .drawBehind {
                val inset = LENS_STROKE.toPx() / 2f
                val diameter = size.minDimension - LENS_STROKE.toPx()

                if (narrowed) {
                    drawArc(
                        color = active,
                        startAngle = 90f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(inset, inset),
                        size = Size(diameter, diameter),
                    )
                }

                drawArc(
                    color = ring,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                    style = Stroke(width = LENS_STROKE.toPx()),
                )
            },
    )
}

private val LENS_GLYPH_SIZE = 24.dp
private val LENS_STROKE = 2.dp

private fun ContentFilter.labelRes() = when (this) {
    ContentFilter.ALL -> AYMR.strings.lens_all
    ContentFilter.ANIME -> AYMR.strings.label_anime
    ContentFilter.MANGA -> AYMR.strings.label_manga
}
