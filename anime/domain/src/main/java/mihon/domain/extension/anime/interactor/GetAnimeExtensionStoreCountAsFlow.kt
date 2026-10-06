package mihon.domain.extension.anime.interactor

import dev.zacsweers.metro.Inject
import mihon.domain.extension.anime.repository.AnimeExtensionStoreRepository

@Inject
class GetAnimeExtensionStoreCountAsFlow(
    private val repository: AnimeExtensionStoreRepository,
) {
    operator fun invoke() = repository.getCountAsFlow()
}
