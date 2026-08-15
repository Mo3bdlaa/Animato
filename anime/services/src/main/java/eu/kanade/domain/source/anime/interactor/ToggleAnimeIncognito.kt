package eu.kanade.domain.source.anime.interactor

import aniyomi.domain.source.service.AnimeSourcePreferences
import tachiyomi.core.common.preference.getAndSet

class ToggleAnimeIncognito(
    private val preferences: AnimeSourcePreferences,
) {
    fun await(extensions: String, enable: Boolean) {
        preferences.incognitoAnimeExtensions().getAndSet {
            if (enable) it.plus(extensions) else it.minus(extensions)
        }
    }
}
