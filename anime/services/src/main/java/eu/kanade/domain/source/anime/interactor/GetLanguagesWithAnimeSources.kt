package eu.kanade.domain.source.anime.interactor

import aniyomi.domain.source.service.AnimeSourcePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.repository.AnimeSourceRepository
import java.util.SortedMap

class GetLanguagesWithAnimeSources(
    private val repository: AnimeSourceRepository,
    private val preferences: AnimeSourcePreferences,
    // The language filter is not per content type: hiding French hides it for both.
    private val sharedPreferences: SourcePreferences,
) {

    fun subscribe(): Flow<SortedMap<String, List<AnimeSource>>> {
        return combine(
            sharedPreferences.enabledLanguages.changes(),
            preferences.disabledAnimeSources().changes(),
            repository.getOnlineAnimeSources(),
        ) { enabledLanguage, disabledSource, onlineSources ->
            val sortedSources = onlineSources.sortedWith(
                compareBy<AnimeSource> { it.id.toString() in disabledSource }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )

            sortedSources
                .groupBy { it.lang }
                .toSortedMap(
                    compareBy<String> { it !in enabledLanguage }.then(LocaleHelper.comparator),
                )
        }
    }
}
