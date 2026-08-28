/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.util.AttributeSet
import android.view.KeyCharacterMap
import android.view.KeyEvent
import animato.anime.net.ProxyPreferences
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.player.controls.components.panels.toColorHexString
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.KeyMapping
import `is`.xyz.mpv.MPVLib
import logcat.LogPriority
import logcat.logcat
import uy.kohesive.injekt.injectLazy
import kotlin.reflect.KProperty

class AniyomiMPVView(context: Context, attributes: AttributeSet) : BaseMPVView(context, attributes) {

    private val playerPreferences: PlayerPreferences by injectLazy()
    private val decoderPreferences: DecoderPreferences by injectLazy()
    private val subtitlePreferences: SubtitlePreferences by injectLazy()
    private val audioPreferences: AudioPreferences by injectLazy()
    private val advancedPreferences: AdvancedPlayerPreferences by injectLazy()
    private val networkPreferences: NetworkPreferences by injectLazy()
    private val proxyPreferences: ProxyPreferences by injectLazy()

    var isExiting = false

    private fun getPropertyInt(property: String): Int? {
        return MPVLib.getPropertyInt(property) as Int?
    }

    private fun getPropertyBoolean(property: String): Boolean? {
        return MPVLib.getPropertyBoolean(property) as Boolean?
    }

    private fun getPropertyDouble(property: String): Double? {
        return MPVLib.getPropertyDouble(property) as Double?
    }

    private fun getPropertyString(property: String): String? {
        return MPVLib.getPropertyString(property) as String?
    }

    val duration: Int?
        get() = getPropertyInt("duration")

    var timePos: Int?
        get() = getPropertyInt("time-pos")
        set(position) = MPVLib.setPropertyInt("time-pos", position!!)

    var paused: Boolean?
        get() = getPropertyBoolean("pause")
        set(paused) = MPVLib.setPropertyBoolean("pause", paused!!)

    val hwdecActive: String
        get() = getPropertyString("hwdec-current") ?: "no"

    val videoH: Int?
        get() = getPropertyInt("video-params/h")

    /**
     * Returns the video aspect ratio. Rotation is taken into account.
     */
    fun getVideoOutAspect(): Double? {
        return getPropertyDouble("video-params/aspect")?.let {
            if (it < 0.001) return 0.0
            if ((getPropertyInt("video-params/rotate") ?: 0) % 180 == 90) 1.0 / it else it
        }
    }

    inner class TrackDelegate(private val name: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = getPropertyString(name)
            // we can get null here for "no" or other invalid value
            return v?.toIntOrNull() ?: -1
        }
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1) {
                MPVLib.setPropertyString(name, "no")
            } else {
                MPVLib.setPropertyInt(name, value)
            }
        }
    }

    var sid: Int by TrackDelegate("sid")
    var secondarySid: Int by TrackDelegate("secondary-sid")
    var aid: Int by TrackDelegate("aid")

    override fun initOptions(vo: String) {
        setVo(if (decoderPreferences.gpuNext().get()) "gpu-next" else "gpu")
        MPVLib.setPropertyBoolean("pause", true)
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("hwdec", if (decoderPreferences.tryHWDecoding().get()) "auto" else "no")
        when (decoderPreferences.videoDebanding().get()) {
            Debanding.None -> {}
            Debanding.CPU -> MPVLib.setOptionString("vf", "gradfun=radius=12")
            Debanding.GPU -> MPVLib.setOptionString("deband", "yes")
        }

        if (decoderPreferences.useYUV420P().get()) {
            MPVLib.setOptionString("vf", "format=yuv420p")
        }
        MPVLib.setOptionString("msg-level", "all=" + if (networkPreferences.verboseLogging.get()) "v" else "warn")

        MPVLib.setPropertyBoolean("keep-open", true)
        MPVLib.setPropertyBoolean("input-default-bindings", true)

        MPVLib.setOptionString("ytdl", "no")
        /*
         * The app's proxy, said again for mpv.
         *
         * Everything else in the app goes through a default `ProxySelector`, which mpv is not
         * reached by: it does its networking in ffmpeg, below Java entirely, so a proxy the rest of
         * the app is using would be bypassed by the one connection that carries the video — and the
         * whole reason for setting a proxy here is usually a stream that is blocked.
         *
         * HTTP proxies only, and that is ffmpeg's limit rather than a shortcut: `http-proxy` is an
         * HTTP-level option and there is no SOCKS equivalent for it. Somebody on SOCKS gets a proxy
         * for browsing and catalogues and a direct connection for playback, which the settings
         * screen says out loud rather than leaving to be discovered mid-episode.
         */
        MPVLib.setOptionString("http-proxy", proxyPreferences.httpProxyUrl().orEmpty())
        MPVLib.setOptionString("tls-verify", "yes")
        MPVLib.setOptionString("tls-ca-file", "${context.filesDir.path}/${PlayerActivity.MPV_DIR}/cacert.pem")

        setupNetworkCache()

        val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        screenshotDir.mkdirs()
        MPVLib.setOptionString("screenshot-directory", screenshotDir.path)

        VideoFilters.entries.forEach {
            MPVLib.setOptionString(it.mpvProperty, it.preference(decoderPreferences).get().toString())
        }

        MPVLib.setOptionString("speed", playerPreferences.playerSpeed().get().toString())
        // workaround for <https://github.com/mpv-player/mpv/issues/14651>
        MPVLib.setOptionString("vd-lavc-film-grain", "cpu")

        setupSubtitlesOptions()
        setupAudioOptions()
    }

    /**
     * How much of a stream is held ahead of the play head, and what happens when it runs out.
     *
     * This is the setting a bad connection is spent against. mpv reads as fast as the network allows
     * and plays out of what it has read, so a drop-out is invisible until the buffer empties — the
     * buffer is, quite literally, how many seconds of trouble can pass without the picture stopping.
     *
     * Three things were wrong with what was here.
     *
     * **The budget was split evenly, forwards and backwards.** The back cache only saves
     * re-downloading when somebody seeks *backwards* — the rarer direction of the rarer action —
     * and it was given as many bytes as the half that keeps playback alive. Forward now takes
     * most of it.
     *
     * **The byte ceiling was never the binding limit.** Readahead is bounded in seconds as well,
     * and mpv's default stops far short of filling a cache of any size — so raising the megabytes
     * alone, which is the obvious fix and the one that had been made, changed nothing. Both bounds
     * have to move, which is why [CACHE_SECONDS] is here.
     *
     * **The size was chosen off the API level.** What a cache costs is native memory, and the thing
     * that differs between a cheap phone and an expensive one is how much of that there is, not
     * which Android they run — every device that reaches this code is years past the 8.1 the check
     * was testing for. It reads the device's actual memory now, and a low-RAM device still gets the
     * small cache the old check was trying to give it.
     *
     * The last two lines are the other half of the same problem: a buffer buys time for a connection
     * that recovers, and a connection that has dropped has to be picked up again. ffmpeg will
     * reconnect by itself, but only when asked to, and the timeout is what tells it the connection
     * is gone rather than slow. Unknown options here are ignored rather than fatal.
     */
    private fun setupNetworkCache() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
        val totalGigs = memory.totalMem / BYTES_PER_GIB

        // A null ActivityManager leaves totalMem at zero and lands in the smallest bucket, which is
        // the right way round to be wrong: too small is a stall, too large is a kill.
        val (forwardMegs, backMegs) = when {
            activityManager?.isLowRamDevice == true -> LOW_RAM_CACHE
            totalGigs < MODEST_DEVICE_GIB -> MODEST_CACHE
            else -> ROOMY_CACHE
        }

        MPVLib.setOptionString("demuxer-max-bytes", "${forwardMegs * BYTES_PER_MIB}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${backMegs * BYTES_PER_MIB}")
        MPVLib.setOptionString("cache-secs", CACHE_SECONDS)
        MPVLib.setOptionString("network-timeout", NETWORK_TIMEOUT_SECONDS)
        MPVLib.setOptionString("stream-lavf-o", RECONNECT_OPTIONS)
    }

    override fun observeProperties() {
        for ((name, format) in observedProps) MPVLib.observeProperty(name, format)
    }

    override fun postInitOptions() {
        advancedPreferences.playerStatisticsPage().get().let {
            if (it != 0) {
                MPVLib.command(arrayOf("script-binding", "stats/display-stats-toggle"))
                MPVLib.command(arrayOf("script-binding", "stats/display-page-$it"))
            }
        }
    }

    fun onKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_MULTIPLE || KeyEvent.isModifierKey(event.keyCode)) {
            return false
        }

        var mapped = KeyMapping.map.get(event.keyCode)
        if (mapped == null) {
            // Fallback to produced glyph
            if (!event.isPrintingKey) {
                if (event.repeatCount == 0) {
                    logcat(LogPriority.DEBUG) { "Unmapped non-printable key ${event.keyCode}" }
                }
                return false
            }

            val ch = event.unicodeChar
            if (ch.and(KeyCharacterMap.COMBINING_ACCENT) != 0) {
                return false // dead key
            }
            mapped = ch.toChar().toString()
        }

        if (event.repeatCount > 0) {
            return true // eat event but ignore it, mpv has its own key repeat
        }

        val mod: MutableList<String> = mutableListOf()
        event.isShiftPressed && mod.add("shift")
        event.isCtrlPressed && mod.add("ctrl")
        event.isAltPressed && mod.add("alt")
        event.isMetaPressed && mod.add("meta")

        val action = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
        mod.add(mapped)
        MPVLib.command(arrayOf(action, mod.joinToString("+")))

        return true
    }

    private val observedProps = mapOf(
        "chapter" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "chapter-list" to MPVLib.mpvFormat.MPV_FORMAT_NONE,
        "track-list" to MPVLib.mpvFormat.MPV_FORMAT_NONE,

        "time-pos" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "demuxer-cache-time" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "duration" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "volume" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "volume-max" to MPVLib.mpvFormat.MPV_FORMAT_INT64,

        "sid" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "secondary-sid" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "aid" to MPVLib.mpvFormat.MPV_FORMAT_STRING,

        "speed" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
        "video-params/aspect" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,

        "hwdec-current" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "hwdec" to MPVLib.mpvFormat.MPV_FORMAT_STRING,

        "pause" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "paused-for-cache" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "seeking" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "eof-reached" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,

        "user-data/aniyomi/show_text" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/toggle_ui" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/show_panel" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/software_keyboard" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/set_button_title" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/reset_button_title" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/toggle_button" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/switch_episode" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/pause" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/seek_by" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/seek_to" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/seek_by_with_text" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/seek_to_with_text" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/aniyomi/launch_int_picker" to MPVLib.mpvFormat.MPV_FORMAT_STRING,

        "user-data/current-anime/intro-length" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
    )

    private fun setupAudioOptions() {
        MPVLib.setOptionString("alang", audioPreferences.preferredAudioLanguages().get())
        MPVLib.setOptionString("audio-delay", (audioPreferences.audioDelay().get() / 1000.0).toString())
        MPVLib.setOptionString("audio-pitch-correction", audioPreferences.enablePitchCorrection().get().toString())
        MPVLib.setOptionString("volume-max", (audioPreferences.volumeBoostCap().get() + 100).toString())
    }

    private fun setupSubtitlesOptions() {
        MPVLib.setOptionString("sub-delay", (subtitlePreferences.subtitlesDelay().get() / 1000.0).toString())
        MPVLib.setOptionString("sub-speed", subtitlePreferences.subtitlesSpeed().get().toString())
        MPVLib.setOptionString(
            "secondary-sub-delay",
            (subtitlePreferences.subtitlesSecondaryDelay().get() / 1000.0).toString(),
        )

        MPVLib.setOptionString("sub-font", subtitlePreferences.subtitleFont().get())
        if (subtitlePreferences.overrideSubsASS().get()) {
            MPVLib.setOptionString("sub-ass-override", "force")
            MPVLib.setOptionString("sub-ass-justify", "yes")
        }
        MPVLib.setOptionString("sub-font-size", subtitlePreferences.subtitleFontSize().get().toString())
        MPVLib.setOptionString("sub-bold", if (subtitlePreferences.boldSubtitles().get()) "yes" else "no")
        MPVLib.setOptionString("sub-italic", if (subtitlePreferences.italicSubtitles().get()) "yes" else "no")
        MPVLib.setOptionString("sub-justify", subtitlePreferences.subtitleJustification().get().value)
        MPVLib.setOptionString("sub-color", subtitlePreferences.textColorSubtitles().get().toColorHexString())
        MPVLib.setOptionString(
            "sub-back-color",
            subtitlePreferences.backgroundColorSubtitles().get().toColorHexString(),
        )
        MPVLib.setOptionString("sub-border-color", subtitlePreferences.borderColorSubtitles().get().toColorHexString())
        MPVLib.setOptionString("sub-border-size", subtitlePreferences.subtitleBorderSize().get().toString())
        MPVLib.setOptionString("sub-border-style", subtitlePreferences.borderStyleSubtitles().get().value)
        MPVLib.setOptionString("sub-shadow-offset", subtitlePreferences.shadowOffsetSubtitles().get().toString())
        MPVLib.setOptionString("sub-pos", subtitlePreferences.subtitlePos().get().toString())
        MPVLib.setOptionString("sub-scale", subtitlePreferences.subtitleFontScale().get().toString())
    }

    companion object {

        /** Forward and back cache in MiB, for a device that says it is short of memory. */
        private val LOW_RAM_CACHE = 32 to 8

        /** The same, for a phone with enough memory to play video and not much more. */
        private val MODEST_CACHE = 64 to 16

        /**
         * The same, for anything current.
         *
         * 192 MiB is roughly two minutes of a good 1080p stream — long enough to sit through a lift,
         * a tunnel or a handover between cells without the picture stopping. It is native memory and
         * it is only ever as large as what has actually been read, so an episode that plays without
         * trouble never reaches this number.
         */
        private val ROOMY_CACHE = 192 to 32

        /** Under this much RAM in total, take the middle cache. */
        private const val MODEST_DEVICE_GIB = 4

        /**
         * The other half of the cache size, and the half that was missing.
         *
         * mpv bounds readahead in seconds as well as in bytes and stops at whichever comes first,
         * with a default low enough that the byte limit never mattered. Two minutes, to match what
         * [ROOMY_CACHE] can hold — the bytes remain the real ceiling on a smaller device.
         */
        private const val CACHE_SECONDS = "120"

        /** How long a silent connection is given before it counts as gone rather than slow. */
        private const val NETWORK_TIMEOUT_SECONDS = "60"

        /**
         * Pick the connection back up instead of ending the episode.
         *
         * ffmpeg can resume an interrupted HTTP read from where it stopped, which for a stream that
         * dropped for a few seconds is the difference between a pause and a failure — but it does
         * not do it unless asked. `reconnect_streamed` covers the non-seekable case, which is most
         * live sources; the delay is capped so a network that is properly down fails in a bounded
         * time rather than retrying into the evening.
         */
        private const val RECONNECT_OPTIONS =
            "reconnect=1,reconnect_streamed=1,reconnect_on_network_error=1,reconnect_delay_max=10"

        private const val BYTES_PER_MIB = 1024 * 1024
        private const val BYTES_PER_GIB = 1024L * 1024 * 1024
    }
}
