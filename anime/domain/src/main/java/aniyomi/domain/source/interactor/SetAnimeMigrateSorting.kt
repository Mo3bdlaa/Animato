package aniyomi.domain.source.interactor

import aniyomi.domain.source.service.AnimeSourcePreferences

/**
 * How the anime migration source list is ordered.
 *
 * Aniyomi reused Mihon's `SetMigrateSorting` and its preference keys for both content types, so
 * ordering the manga source list silently reordered the anime one. This is the anime half, with its
 * own keys, because the two lists are sorted independently — a user who orders manga sources by
 * favourite count has said nothing about how they want anime sources ordered.
 *
 * The enums mirror Mihon's rather than referencing them: they are a stored preference value, and
 * sharing an enum with upstream would mean an upstream rename silently invalidating saved settings.
 */
class SetAnimeMigrateSorting(
    private val preferences: AnimeSourcePreferences,
) {

    fun await(mode: Mode, direction: Direction) {
        preferences.migrationSortingMode().set(mode)
        preferences.migrationSortingDirection().set(direction)
    }

    enum class Mode {
        ALPHABETICAL,
        TOTAL,
    }

    enum class Direction {
        ASCENDING,
        DESCENDING,
    }
}
