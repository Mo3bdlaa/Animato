package animato.app.settings

import androidx.compose.runtime.Composable
import animato.anime.ui.category.visualName
import animato.domain.category.AnimeCategory
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * "Include: A, B  ·  Exclude: C", for the anime categories.
 *
 * Mihon has this for its own `Category`, and there is nothing to share: the two category types are
 * separate models, and the whole function is three joins and a plural.
 */
@Composable
internal fun categoriesLabel(
    allCategories: List<AnimeCategory>,
    included: Set<String>,
    excluded: Set<String>,
): String {
    val includedCategories = included
        .mapNotNull { id -> allCategories.find { it.id == id.toLongOrNull() } }
        .sortedBy { it.order }
    val excludedCategories = excluded
        .mapNotNull { id -> allCategories.find { it.id == id.toLongOrNull() } }
        .sortedBy { it.order }
    val allExcluded = excludedCategories.size == allCategories.size

    // visualName is composable, so the names are resolved first and joined after.
    val includedNames = includedCategories.map { it.visualName }
    val excludedNames = excludedCategories.map { it.visualName }

    val includedText = when {
        includedNames.isNotEmpty() && includedNames.size != allCategories.size -> includedNames.joinToString()
        includedNames.size == allCategories.size -> stringResource(MR.strings.all)
        allExcluded -> stringResource(MR.strings.none)
        else -> stringResource(MR.strings.all)
    }
    val excludedText = when {
        excludedNames.isEmpty() -> stringResource(MR.strings.none)
        allExcluded -> stringResource(MR.strings.all)
        else -> excludedNames.joinToString()
    }
    return stringResource(MR.strings.include, includedText) + "\n" +
        stringResource(MR.strings.exclude, excludedText)
}
