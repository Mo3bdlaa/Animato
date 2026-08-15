package animato.anime.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus

/**
 * [viewModelScope] with the IO dispatcher instead of the main one.
 *
 * Aniyomi had this as an extension on Voyager's `ScreenModel`, built out of `ScreenModelStore`'s
 * dependency map with an explicit `onDispose` to cancel the scope. None of that is needed once the
 * screen models are androidx `ViewModel`s: adding a dispatcher to a scope keeps the original `Job`,
 * so this is still cancelled when the view model clears, and there is nothing to dispose.
 */
val ViewModel.ioCoroutineScope: CoroutineScope
    get() = viewModelScope + Dispatchers.IO
