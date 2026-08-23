package eu.kanade.tachiyomi.ui.player

import `is`.xyz.mpv.MPVLib

/**
 * mpv's properties, read with an answer for the case where it has none.
 *
 * ## Why every one of these needs a default
 *
 * `MPVLib.getProperty*` is Java, so Kotlin sees a platform type and lets it be assigned straight to
 * a non-null `Int`, `Double` or `Boolean` — at which point the compiler quietly inserts an unboxing
 * null check. That reads as ordinary code and is a crash waiting for the one state mpv is in more
 * often than any other: not having loaded anything yet.
 *
 * Which is exactly when the settings panels are opened. Somebody starts a torrent, it buffers, they
 * open the subtitle panel to line the timing up while they wait — and every property that panel
 * reads is one mpv cannot answer. The failure surfaced as a crash out of a Compose composition,
 * with the panel named nowhere in it.
 *
 * The defaults here are mpv's own, so a panel opened early shows what playback will use anyway.
 *
 * ## Why a file rather than twenty-five `?:`
 *
 * There were twenty-five of these across four panels, all with the same shape and all equally easy
 * to miss on the next one somebody writes. A named function is the difference between a rule and a
 * habit — `mpvDouble("sub-delay", 0.0)` cannot be written without stating what happens when there
 * is no answer.
 */
internal fun mpvInt(property: String, default: Int): Int =
    MPVLib.getPropertyInt(property) ?: default

internal fun mpvDouble(property: String, default: Double): Double =
    MPVLib.getPropertyDouble(property) ?: default

internal fun mpvBoolean(property: String, default: Boolean): Boolean =
    MPVLib.getPropertyBoolean(property) ?: default

internal fun mpvString(property: String, default: String): String =
    MPVLib.getPropertyString(property) ?: default
