package animato.di

import android.app.Application
import animato.anime.player.RememberedQuality
import animato.anime.player.SubtitleDelayMemory
import animato.anime.stremio.StremioSubtitleFinder
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.ui.player.utils.TrackSelect
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * What the player needs bound: its preferences, its track selector, and the tracking interactors
 * that mark an episode seen once it has been watched.
 *
 * Separate from [AnimeAppModule] because it is separate in the build — nothing below `:anime:player`
 * resolves any of it, and `EpisodeVideoResolver`, the one binding the layers below do depend on,
 * deliberately stays in `AnimeAppModule` beside the downloader that asks for it.
 */
class AnimePlayerModule(val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory { PlayerPreferences(get()) }
        // Not a settings screen's preference — the quality someone chose by hand for one
        // anime, so the next episode of it does not start over. See RememberedQuality.
        addSingletonFactory { RememberedQuality(get()) }
        addSingletonFactory { SubtitleDelayMemory(get()) }
        addSingletonFactory { StremioSubtitleFinder() }
        addSingletonFactory { AdvancedPlayerPreferences(get()) }
        addSingletonFactory { AudioPreferences(get()) }
        addSingletonFactory { DecoderPreferences(get()) }
        addSingletonFactory { GesturePreferences(get()) }
        addSingletonFactory { SubtitlePreferences(get()) }

        addSingletonFactory { ExternalIntents() }
        addSingletonFactory { DelayedAnimeTrackingStore(app) }

        addFactory { TrackSelect(get(), get()) }
        addFactory { TrackEpisode(get(), get(), get(), get()) }
        addFactory { AddAnimeTracks(get(), get(), get(), get()) }
        addFactory { SetAnimeViewerFlags(get()) }
        addFactory { GetAnimeIncognitoState(get(), get(), get(), get()) }
    }
}
