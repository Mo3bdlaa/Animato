package animato.di

import android.app.Application
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.domain.download.service.AnimeDownloadPreferences
import aniyomi.domain.library.service.AnimeLibraryPreferences
import aniyomi.domain.source.service.AnimeSourcePreferences
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * The anime half of Mihon's PreferenceModule.
 *
 * `PreferenceStore` itself is Mihon's and is bound by their module, so these read the same store
 * and the same keys an Aniyomi install already wrote — splitting the classes changed where the
 * declarations live, not where the values are kept.
 */
class AnimePreferenceModule(@Suppress("unused") val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory { AnimeLibraryPreferences(get()) }
        addSingletonFactory { AnimeDownloadPreferences(get()) }
        addSingletonFactory { AnimeSourcePreferences(get()) }
        addSingletonFactory { TorrentPreferences(get()) }
    }
}
