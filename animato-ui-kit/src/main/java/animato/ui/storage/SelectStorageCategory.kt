package animato.ui.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.SelectItem
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The category picker above the storage breakdown.
 *
 * Two of the entries have no stored name — the all-categories pseudo-entry and the default category
 * — so both are labelled here rather than in the view model, where the strings would need a context.
 */
@Composable
fun SelectStorageCategory(
    selectedCategory: StorageCategory,
    categories: List<StorageCategory>,
    uncategorizedId: Long,
    onCategorySelected: (StorageCategory) -> Unit,
) {
    val all = stringResource(AYMR.strings.label_all)
    val default = stringResource(MR.strings.label_default)
    // The dropdown renders whatever it is given with toString(), so it is handed the labels and the
    // picked index maps back to the category it came from.
    val labels = remember(categories, all, default) {
        categories.map {
            when (it.id) {
                StorageCategory.ALL_ID -> all
                uncategorizedId -> default
                else -> it.name
            }
        }.toTypedArray()
    }
    val selectedIndex = categories.indexOfFirst { it.id == selectedCategory.id }.coerceAtLeast(0)

    SelectItem(
        label = stringResource(AYMR.strings.label_category),
        selectedIndex = selectedIndex,
        options = labels,
        onSelect = { index ->
            onCategorySelected(categories[index])
        },
    )
}
