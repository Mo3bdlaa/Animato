package animato.anime.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.tachiyomi.ui.category.anime.animeCategoryTab
import tachiyomi.i18n.MR

/**
 * Hosts the anime category editor.
 *
 * Aniyomi had a `CategoriesTab` holding two tabs, anime and manga, and the ported screens push it
 * when the user chooses "edit categories". That tab is not a shared component — it is a destination
 * that shows both content types, which makes it phase 6c's job, alongside the rest of the unified
 * navigation. Until then this gives the anime screens somewhere correct to go: the anime categories,
 * on their own. Mihon's own `CategoryScreen` already covers manga.
 *
 * When 6c builds the combined screen, this becomes one of its two tabs rather than being thrown
 * away — `animeCategoryTab()` is already a `TabContent`, which is the shape that screen will want.
 */
class AnimeCategoriesScreen : Screen() {

    @Composable
    override fun Content() {
        TabbedScreen(
            titleRes = MR.strings.action_edit_categories,
            tabs = listOf(animeCategoryTab()),
        )
    }
}
