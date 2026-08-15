package eu.kanade.domain.source.anime.interactor

import aniyomi.domain.source.service.AnimeSourcePreferences
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.source.anime.model.AnimeSource

class ToggleAnimeSourcePin(
    private val preferences: AnimeSourcePreferences,
) {

    fun await(source: AnimeSource) {
        val isPinned = source.id.toString() in preferences.pinnedAnimeSources.get()
        preferences.pinnedAnimeSources.getAndSet { pinned ->
            if (isPinned) pinned.minus("${source.id}") else pinned.plus("${source.id}")
        }
    }
}
