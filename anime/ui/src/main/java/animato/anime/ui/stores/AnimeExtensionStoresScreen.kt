package animato.anime.ui.stores

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.anime.ui.stores.components.AnimeExtensionStoreConfirmDialog
import animato.anime.ui.stores.components.AnimeExtensionStoreCreateDialog
import animato.anime.ui.stores.components.AnimeExtensionStoreDeleteDialog
import animato.anime.ui.stores.components.AnimeExtensionStoresScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser
import tachiyomi.presentation.core.screens.LoadingScreen

class AnimeExtensionStoresScreen(
    private val url: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = viewModel<AnimeExtensionStoresViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(url) {
            url?.let { viewModel.addFromDeeplink(url) }
        }

        if (state is AnimeExtensionStoreScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AnimeExtensionStoreScreenState.Success

        AnimeExtensionStoresScreen(
            state = successState,
            onClickCreate = { viewModel.showDialog(AnimeExtensionStoreDialog.Create()) },
            onCopy = { context.copyToClipboard(it.indexUrl, it.indexUrl) },
            onOpenWebsite = { it.contact.website.let(context::openInBrowser) },
            onOpenDiscord = { it.contact.discord?.let(context::openInBrowser) },
            onClickDelete = { viewModel.showDialog(AnimeExtensionStoreDialog.Delete(it)) },
            onClickRefresh = { viewModel.refreshRepos() },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            is AnimeExtensionStoreDialog.Create -> {
                AnimeExtensionStoreCreateDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onCreate = { viewModel.createRepo(it) },
                    storeIndexUrls = successState.stores.map { it.indexUrl }.toSet(),
                    processing = dialog.processing,
                    errorMessage = dialog.errorMessage,
                )
            }
            is AnimeExtensionStoreDialog.Delete -> {
                AnimeExtensionStoreDeleteDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onDelete = { viewModel.deleteRepo(dialog.store.indexUrl) },
                    storeName = dialog.store.name,
                    storeIndexUrl = dialog.store.indexUrl,
                )
            }
            is AnimeExtensionStoreDialog.Confirm -> {
                AnimeExtensionStoreConfirmDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onCreate = { viewModel.createRepo(dialog.url) },
                    storeIndexUrl = dialog.url,
                    storeAlreadyExists = dialog.alreadyExists,
                    processing = dialog.processing,
                    errorMessage = dialog.errorMessage,
                )
            }
        }
    }
}
