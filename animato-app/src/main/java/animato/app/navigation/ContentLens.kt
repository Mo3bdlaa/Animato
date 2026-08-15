package animato.app.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import animato.domain.content.ContentPreferences
import animato.domain.content.ContentType
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The anime/manga lens, as the destinations read and write it.
 *
 * A destination that shows one library's screens asks [contentType] which one. The switch itself
 * lives on the home screen, and re-selecting any other destination's tab flips it — those screens'
 * toolbars belong to their libraries, so there is nowhere on them to put a control of ours.
 */
@Composable
fun contentType(): ContentType {
    val preferences = remember { Injekt.get<ContentPreferences>() }
    val type by preferences.contentType.collectAsState()
    return type
}

fun setContentType(type: ContentType) {
    Injekt.get<ContentPreferences>().contentType.set(type)
}

fun toggleContentType() {
    val preference = Injekt.get<ContentPreferences>().contentType
    preference.set(
        when (preference.get()) {
            ContentType.MANGA -> ContentType.ANIME
            ContentType.ANIME -> ContentType.MANGA
        },
    )
}

/**
 * Two buttons, manga and anime, for a screen that shows one at a time.
 *
 * Deliberately not a tab row: these are not two places, they are one place seen two ways, and a tab
 * row would promise a back stack that does not exist.
 */
@Composable
fun ContentTypeSwitch(
    selected: ContentType,
    onSelect: (ContentType) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        ContentType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ContentType.entries.size),
                icon = {},
                label = {
                    Text(
                        text = when (type) {
                            ContentType.MANGA -> stringResource(AYMR.strings.label_manga)
                            ContentType.ANIME -> stringResource(AYMR.strings.label_anime)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}
