package animato.di

import android.app.Application
import animato.anime.net.ProxyPreferences
import animato.app.downloads.DownloadCleanupPreferences
import animato.app.entry.EntryOverrides
import animato.app.library.UnifiedLibraryPreferences
import animato.app.sync.SyncPreferences
import animato.domain.content.ContentPreferences
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.domain.download.service.AnimeDownloadPreferences
import aniyomi.domain.library.service.AnimeLibraryPreferences
import aniyomi.domain.source.service.AnimeSourcePreferences
import aniyomi.domain.track.service.AnimeTrackPreferences
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * The preferences Mihon's own PreferenceModule does not bind.
 *
 * Mostly the anime half. `PreferenceStore` itself is Mihon's and is bound by their module, so these
 * read the same store and the same keys an Aniyomi install already wrote — splitting the classes
 * changed where the declarations live, not where the values are kept.
 *
 * [ContentPreferences] is the exception: it belongs to neither half, because it is the setting that
 * decides which half you are looking at.
 */
class AnimePreferenceModule(@Suppress("unused") val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory { AnimeLibraryPreferences(get()) }
        addSingletonFactory { AnimeDownloadPreferences(get()) }
        addSingletonFactory { AnimeSourcePreferences(get()) }
        addSingletonFactory { AnimeTrackPreferences(get()) }
        addSingletonFactory { TorrentPreferences(get()) }
        addSingletonFactory { ProxyPreferences(get()) }
        addSingletonFactory { ContentPreferences(get()) }
        addSingletonFactory { DownloadCleanupPreferences(get()) }
        addSingletonFactory { UnifiedLibraryPreferences(get()) }
        addSingletonFactory { SyncPreferences(get()) }
        // A singleton because its flow is what makes an edit show up on every screen at once, not
        // only on the one it was made from.
        addSingletonFactory { EntryOverrides(get()) }
    }
}
