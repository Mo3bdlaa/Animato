package animato.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable

/**
 * Whether an extended FAB should show its label, for a grid.
 *
 * Mihon has this for `LazyListState`, because its chapter list is a list. The anime details screen
 * lays its items out in a grid — episodes share the screen with seasons and previews — so it needs
 * the same rule against [LazyGridState]. Identical logic: expanded while scrolling back, or when
 * there is nothing to scroll.
 */
@Composable
fun LazyGridState.shouldExpandFAB(): Boolean =
    lastScrolledBackward || !canScrollForward || !canScrollBackward
