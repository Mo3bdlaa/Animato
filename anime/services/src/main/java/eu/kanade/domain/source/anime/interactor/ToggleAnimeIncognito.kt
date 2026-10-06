package eu.kanade.domain.source.anime.interactor

import aniyomi.domain.source.service.AnimeSourcePreferences
import dev.zacsweers.metro.Inject
import tachiyomi.core.common.preference.getAndSet

@Inject
class ToggleAnimeIncognito(
    private val preferences: AnimeSourcePreferences,
) {
    fun await(extensions: String, enable: Boolean) {
        preferences.incognitoAnimeExtensions.getAndSet {
            if (enable) it.plus(extensions) else it.minus(extensions)
        }
    }
}
