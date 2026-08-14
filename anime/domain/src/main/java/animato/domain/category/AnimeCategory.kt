package animato.domain.category

import java.io.Serializable

/**
 * A category in the anime library.
 *
 * Deliberately not Mihon's `AnimeCategory`. Anime categories live in their own table with their own id
 * space, and they carry a field Mihon's model does not have. Sharing the model would have meant
 * adding that field inside Mihon's file — the exact move that made the old codebase unmergeable.
 */
data class AnimeCategory(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
    val hidden: Boolean,
) : Serializable {

    val isSystemCategory: Boolean = id == UNCATEGORIZED_ID

    companion object {
        const val UNCATEGORIZED_ID = 0L
    }
}

data class AnimeCategoryUpdate(
    val id: Long,
    val name: String? = null,
    val order: Long? = null,
    val flags: Long? = null,
    val hidden: Boolean? = null,
)
