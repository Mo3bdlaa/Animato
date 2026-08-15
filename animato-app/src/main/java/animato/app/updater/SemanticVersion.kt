package animato.app.updater

/**
 * A version, ordered the way semver orders them.
 *
 * This exists because Animato's own tags are prereleases — `v0.1.0-alpha.7` — and Mihon's
 * comparison cannot read them. Mihon strips every character that is not a digit or a dot and
 * compares the pieces positionally, which turns `0.1.0-alpha.7` into four numbers and then indexes
 * the new version by the old one's positions: comparing an alpha against the plain `0.1.0` it
 * released into reads past the end of the list and throws. It never happens upstream because Mihon
 * only ever tags `vX.Y.Z`.
 *
 * So versions are parsed rather than filtered, and compared by semver's own rules (§11):
 *
 * - the numeric parts first, left to right, a missing part counting as zero;
 * - on a tie, a version *with* a prerelease part ranks below the same version without one, which is
 *   what makes `0.1.0-alpha.7` older than `0.1.0` and stops a finished release being mistaken for a
 *   step backwards;
 * - between two prereleases, identifier by identifier — numeric ones numerically, everything else
 *   by text, numeric below non-numeric, and a version that runs out of identifiers first ranks
 *   lower.
 *
 * Build metadata after a `+` is dropped, as semver says it must be: it is not part of precedence.
 *
 * Ordering and equality are not quite the same relation here, and deliberately so: `1.2` and
 * `1.2.0` compare equal but are not the same value, because the version keeps the shape it was
 * written in so it can be shown back to the reader as itself. Compare with `<`, `>` or
 * [compareTo], not with `==`.
 */
data class SemanticVersion(
    val numbers: List<Int>,
    val prerelease: List<String>,
) : Comparable<SemanticVersion> {

    val isPrerelease: Boolean get() = prerelease.isNotEmpty()

    override fun compareTo(other: SemanticVersion): Int {
        repeat(maxOf(numbers.size, other.numbers.size)) { index ->
            val mine = numbers.getOrElse(index) { 0 }
            val theirs = other.numbers.getOrElse(index) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }

        // 1.0.0-alpha < 1.0.0, and neither is less than itself.
        if (!isPrerelease || !other.isPrerelease) {
            return other.prerelease.size.compareTo(prerelease.size)
        }

        repeat(minOf(prerelease.size, other.prerelease.size)) { index ->
            val result = compareIdentifiers(prerelease[index], other.prerelease[index])
            if (result != 0) return result
        }

        // Everything they have in common is equal, so the longer one wins: alpha.1 < alpha.1.2.
        return prerelease.size.compareTo(other.prerelease.size)
    }

    override fun toString(): String = buildString {
        append(numbers.joinToString("."))
        if (isPrerelease) {
            append('-')
            append(prerelease.joinToString("."))
        }
    }

    companion object {

        /**
         * Reads a version out of a release tag, or null when it is not one.
         *
         * Null is an answer, not a failure: a repository can hold tags that are not releases of
         * this app at all, and the caller skips those rather than guessing at them.
         */
        fun parse(raw: String): SemanticVersion? {
            val trimmed = raw.trim()
                .removePrefix("v")
                .substringBefore('+')
                .takeIf(String::isNotEmpty)
                ?: return null

            val numbersPart = trimmed.substringBefore('-')
            val prereleasePart = trimmed.substringAfter('-', missingDelimiterValue = "")

            val numbers = numbersPart.split('.').map { part ->
                part.toIntOrNull()?.takeIf { it >= 0 } ?: return null
            }

            val prerelease = if (prereleasePart.isEmpty()) {
                emptyList()
            } else {
                prereleasePart.split('.').also { identifiers ->
                    if (identifiers.any(String::isEmpty)) return null
                }
            }

            return SemanticVersion(numbers, prerelease)
        }

        private fun compareIdentifiers(mine: String, theirs: String): Int {
            val mineNumber = mine.toIntOrNull()
            val theirsNumber = theirs.toIntOrNull()
            return when {
                mineNumber != null && theirsNumber != null -> mineNumber.compareTo(theirsNumber)
                // "Numeric identifiers always have lower precedence than alphanumeric ones."
                mineNumber != null -> -1
                theirsNumber != null -> 1
                else -> mine.compareTo(theirs)
            }
        }
    }
}
