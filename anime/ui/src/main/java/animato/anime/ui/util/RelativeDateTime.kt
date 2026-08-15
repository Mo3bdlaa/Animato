package animato.anime.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

/**
 * A release time down to the minute, not just the day.
 *
 * Mihon's `relativeDateText` stops at "today" because a chapter's release date is all a reader
 * needs. An episode airs at a time, and a viewer waiting for one wants to know whether it is two
 * hours out or two minutes, so this keeps going: days, then hours, then minutes, then "now".
 *
 * On java.time rather than kotlinx.datetime because it needs [ChronoUnit] between two wall-clock
 * date-times, which the kotlinx types do not offer.
 */
@Composable
fun relativeDateTimeText(dateEpochMillis: Long): String {
    return relativeDateTimeText(
        localDateTime = LocalDateTime
            .ofInstant(Instant.ofEpochMilli(dateEpochMillis), ZoneId.systemDefault())
            .takeIf { dateEpochMillis > 0L },
    )
}

@Composable
fun relativeDateTimeText(localDateTime: LocalDateTime?): String {
    val context = LocalContext.current

    val preferences = remember { Injekt.get<UiPreferences>() }
    val relativeTime = remember { preferences.relativeTime.get() }
    val dateFormat = remember { UiPreferences.dateFormat(preferences.dateFormat.get()) }

    return localDateTime?.toRelativeString(
        context = context,
        relative = relativeTime,
        dateFormat = dateFormat,
    )
        ?: stringResource(MR.strings.not_applicable)
}

fun LocalDateTime.toRelativeString(
    context: Context,
    relative: Boolean = true,
    dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT),
): String {
    if (!relative) {
        return dateFormat.format(this)
    }
    val now = LocalDateTime.now()
    val timeDifference = ChronoUnit.DAYS.between(this, now)
    val dateDifference = ChronoUnit.DAYS.between(this.toLocalDate(), now.toLocalDate())
    return when {
        timeDifference < -7 -> dateFormat.format(this)
        timeDifference < 0 -> context.pluralStringResource(
            MR.plurals.upcoming_relative_time,
            dateDifference.toInt().absoluteValue,
            dateDifference.toInt().absoluteValue,
        )
        timeDifference < 1 -> {
            val hourDifference = ChronoUnit.HOURS.between(this, now)
            when {
                hourDifference < 0 -> context.pluralStringResource(
                    AYMR.plurals.upcoming_relative_time_hours,
                    hourDifference.toInt().absoluteValue,
                    hourDifference.toInt().absoluteValue,
                )
                hourDifference < 1 -> {
                    val minuteDifference = ChronoUnit.MINUTES.between(this, now)
                    when {
                        minuteDifference < 0 -> context.pluralStringResource(
                            AYMR.plurals.upcoming_relative_time_minutes,
                            minuteDifference.toInt().absoluteValue,
                            minuteDifference.toInt().absoluteValue,
                        )
                        minuteDifference == 0L -> context.stringResource(AYMR.strings.relative_time_now)
                        else -> context.pluralStringResource(
                            AYMR.plurals.relative_time_minutes,
                            minuteDifference.toInt(),
                            minuteDifference.toInt(),
                        )
                    }
                }
                else -> context.pluralStringResource(
                    AYMR.plurals.relative_time_hours,
                    hourDifference.toInt(),
                    hourDifference.toInt(),
                )
            }
        }
        timeDifference < 7 -> context.pluralStringResource(
            MR.plurals.relative_time,
            dateDifference.toInt(),
            dateDifference.toInt(),
        )
        else -> dateFormat.format(this)
    }
}
