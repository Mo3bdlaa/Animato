package animato.ui.storage

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A category as the storage screen needs it.
 *
 * The screen groups downloads by category and lets the user switch between them, and for that it
 * needs an id to filter on and a name to show. It does not need the rest of a category — the sort
 * flags, the display mode, the hidden flag — and Aniyomi's version taking the full model is what
 * tied this screen to one library's category type. Manga categories and anime categories are
 * separate tables with separate id spaces; both map to this.
 */
@Immutable
data class StorageCategory(
    val id: Long,
    val name: String,
) {
    companion object {
        /** The pseudo-category that shows every entry, whichever category it is really in. */
        const val ALL_ID = -1L
    }
}

/**
 * One entry's share of the downloaded bytes.
 *
 * [color] is not a theme colour: the screen draws a proportional arc per entry, and each needs a
 * distinguishable one. It is seeded from the entry id so it stays the same across recompositions.
 */
@Immutable
data class StorageItem(
    val id: Long,
    val title: String,
    val size: Long,
    val thumbnail: String?,
    val entriesCount: Int,
    val color: Color,
)

sealed interface StorageScreenState {

    @Immutable
    data object Loading : StorageScreenState

    @Immutable
    data class Success(
        val selectedCategory: StorageCategory,
        val items: List<StorageItem>,
        val categories: List<StorageCategory>,
    ) : StorageScreenState
}

/**
 * Formats a byte count the way a file manager would.
 *
 * Decimal units, matching what Android itself reports for app and download sizes, so the number
 * here and the number in system settings agree.
 */
fun Long.toSize(): String {
    val kb = 1000
    val mb = kb * kb
    val gb = mb * kb
    return when {
        this >= gb -> "%.2f GB".format(this.toFloat() / gb)
        this >= mb -> "%.2f MB".format(this.toFloat() / mb)
        this >= kb -> "%.2f KB".format(this.toFloat() / kb)
        else -> "$this B"
    }
}
