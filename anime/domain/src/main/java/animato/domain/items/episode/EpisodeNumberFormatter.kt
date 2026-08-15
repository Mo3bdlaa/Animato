package animato.domain.items.episode

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/*
 * Mihon has an identical formatter for chapter numbers in eu.kanade.presentation.util, but it sits
 * in the presentation layer of its app module. Notifications need to format an episode number too,
 * and the services layer must not depend on a Compose module to do it — so the episode one lives
 * here, in the lowest module both the services and the UI already depend on.
 *
 * The pattern is deliberately the same as Mihon's: episode 12.0 reads "12", and 12.5 reads "12.5",
 * with a full stop regardless of locale so it matches how sources number their episodes.
 */
private val formatter = DecimalFormat(
    "#.###",
    DecimalFormatSymbols().apply { decimalSeparator = '.' },
)

fun formatEpisodeNumber(episodeNumber: Double): String = formatter.format(episodeNumber)
