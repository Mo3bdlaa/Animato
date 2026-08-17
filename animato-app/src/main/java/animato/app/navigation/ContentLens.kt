package animato.app.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
 * The control is a single top-bar button rather than a chip row, and the glyph is the state: a solid
 * disc for `ALL`, and one **half of it filled in blue** — a different half for each — when the lens
 * is narrowed. That is the answer to the hidden-filter problem — a filtered app has to *look*
 * filtered from across the room, or the first question anyone asks is why their library is empty —
 * without spending a whole 56 dp row on three words. [LensGlyph] has the three states and what a
 * device session found wrong with the first attempt. See docs/branding/design.md.
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
 * The lens button: the glyph, a quiet word beside it, and a menu behind them.
 *
 * Sits in the top bar of every screen that lists content — home, library, discover, updates and
 * search — in the same slot each time, so the control is learned once.
 *
 * The label arrived exactly the way [LensGlyph]'s note predicted it would: the glyph alone did
 * not read on a device — *"show inside it All or Ani or Man … just a mention, not too much
 * contrast."* Inside the circle a word is a smudge at 24 dp, so it sits beside it, in the
 * variant colour, small — a caption for the shape rather than a second control.
 */
@Composable
fun LensButton(modifier: Modifier = Modifier) {
    val lens = contentLens()
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LensGlyph(lens = lens)
            Text(
                text = stringResource(lens.shortLabelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
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
 * One circle in three states, and all three have to be told apart at a glance.
 *
 * ## What the first device session found
 *
 * The first version had two states, not three: `ALL` was a bare outline and *both* narrowed states
 * shaded the same half. So Anime and Manga were pixel-identical — the control could tell you that
 * you were filtered and never which way — and `ALL` read as an empty circle, which is to say as
 * nothing at all.
 *
 * ## The three states now
 *
 * - **All** — a full muted disc inside the ring. Solid, because *everything* is the state where
 *   nothing has been taken away, and an outline with nothing in it says the opposite.
 * - **Anime** — the leading half filled in the accent.
 * - **Manga** — the trailing half filled in the accent.
 *
 * Which half is arbitrary and that is fine; what matters is that they are opposites, so the two are
 * distinguishable without reading anything. The pair also mirrors under RTL along with the rest of
 * the layout, which is why the halves are described as leading and trailing rather than left and
 * right, and why the arcs are chosen from the layout direction rather than fixed.
 *
 * ## Why not a word inside the circle
 *
 * Because the same glyph is drawn at 24 dp in a top bar and inside onboarding's option rows, and a
 * three-letter word at 24 dp is a smudge. Shape survives the size; text does not. If this still
 * does not read on a device, the fix is a label *beside* the icon rather than inside it.
 *
 * Public because onboarding teaches the lens *with* the lens: its three options are drawn with this
 * glyph, so the first time anyone meets the icon they are choosing with it rather than decoding it
 * later in a top bar.
 */
@Composable
fun LensGlyph(lens: ContentFilter, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val active = MaterialTheme.colorScheme.primary
    val narrowed = lens != ContentFilter.ALL
    val ring = if (narrowed) active else outline
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl

    // 0° is at three o'clock and sweeps clockwise, so 90° starts at six and covers the left half.
    // Under RTL the leading side is the right one, so the two swap.
    val leadingHalf = if (mirrored) TRAILING_HALF_DEGREES else LEADING_HALF_DEGREES
    val trailingHalf = if (mirrored) LEADING_HALF_DEGREES else TRAILING_HALF_DEGREES

    Box(
        modifier = modifier
            .size(LENS_GLYPH_SIZE)
            .clip(CircleShape)
            .drawBehind {
                val inset = LENS_STROKE.toPx() / 2f
                val diameter = size.minDimension - LENS_STROKE.toPx()

                when (lens) {
                    // Solid, and in the ring's own colour so it reads as one object rather than as
                    // something switched on. "Everything" is not an active state, it is the absence
                    // of a narrowing.
                    ContentFilter.ALL -> drawArc(
                        color = outline.copy(alpha = ALL_FILL_ALPHA),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = true,
                        topLeft = Offset(inset, inset),
                        size = Size(diameter, diameter),
                    )

                    ContentFilter.ANIME -> drawArc(
                        color = active,
                        startAngle = leadingHalf,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(inset, inset),
                        size = Size(diameter, diameter),
                    )

                    ContentFilter.MANGA -> drawArc(
                        color = active,
                        startAngle = trailingHalf,
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

/** Six o'clock, sweeping clockwise to twelve: the left half. */
private const val LEADING_HALF_DEGREES = 90f

/** Twelve o'clock, sweeping clockwise to six: the right half. */
private const val TRAILING_HALF_DEGREES = 270f

/** Present enough to read as filled, quiet enough not to compete with a narrowed state. */
private const val ALL_FILL_ALPHA = 0.35f

private fun ContentFilter.labelRes() = when (this) {
    ContentFilter.ALL -> AYMR.strings.lens_all
    ContentFilter.ANIME -> AYMR.strings.label_anime
    ContentFilter.MANGA -> AYMR.strings.label_manga
}

/** The caption beside the glyph. Short on purpose: it is a mention, not a heading. */
private fun ContentFilter.shortLabelRes() = when (this) {
    ContentFilter.ALL -> AYMR.strings.lens_short_all
    ContentFilter.ANIME -> AYMR.strings.lens_short_anime
    ContentFilter.MANGA -> AYMR.strings.lens_short_manga
}
