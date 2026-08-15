package animato.anime.ui.category

import android.content.Context
import androidx.compose.runtime.Composable
import animato.domain.category.AnimeCategory
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * What a category is called on screen.
 *
 * The default category has no name of its own — it is the catch-all every entry starts in — so it
 * shows a translated label instead of the empty string the database holds. Mihon has the same
 * extension on its own `Category`; this is the anime one, because the two models are separate.
 */
val AnimeCategory.visualName: String
    @Composable
    get() = when {
        isSystemCategory -> stringResource(MR.strings.label_default)
        else -> name
    }

fun AnimeCategory.visualName(context: Context): String =
    when {
        isSystemCategory -> context.stringResource(MR.strings.label_default)
        else -> name
    }
