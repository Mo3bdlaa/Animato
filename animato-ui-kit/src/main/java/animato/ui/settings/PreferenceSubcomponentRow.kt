package animato.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A settings row whose content is a control rather than a label and a switch.
 *
 * Mihon has this — `BasePreferenceWidget` with a `subcomponent` — and it is `internal`, so a
 * settings screen in another module cannot lay a row out the way every row on the screen beside it
 * is laid out. That is the whole reason this exists.
 *
 * The measurements are Mihon's, so a row built with this sits flush with the rows above and below
 * it. What is left out is what a control-only row never used: the highlight for a searched-for
 * preference, the title, the icon and the trailing widget.
 */
@Composable
fun PreferenceSubcomponentRow(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = PreferenceRowMinHeight)
            .padding(vertical = PreferenceRowVerticalPadding),
        content = content,
    )
}

/**
 * The inset Mihon puts either side of a preference's content.
 */
val PreferenceRowHorizontalPadding = 16.dp

private val PreferenceRowVerticalPadding = 16.dp
private val PreferenceRowMinHeight = 56.dp
