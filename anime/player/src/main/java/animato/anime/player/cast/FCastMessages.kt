package animato.anime.player.cast

import kotlinx.serialization.Serializable

// The bodies of the FCast messages this sender sends and reads.
//
// Field names are the protocol's and are not ours to tidy: `generationTime` and `container` are
// what goes on the wire. Numbers are `Double` and `Long` rather than the `number` the specification
// says, because JavaScript has one numeric type and Kotlin does not — seconds are fractional,
// versions and playback states are not.

/**
 * Announces which protocol version this sender speaks. Sent first, before anything else.
 *
 * A version 3 receiver expects a handshake; a version 2 one ignores this. Sending it either way is
 * what makes a downgrade deterministic rather than something the receiver has to infer from
 * silence.
 */
@Serializable
internal data class VersionMessage(
    val version: Long = FCastProtocol.VERSION,
)

/**
 * Start playing something.
 *
 * [url] and [content] are alternatives: a URL for the receiver to fetch, or the content inline —
 * an HLS playlist, say. We always send a URL, because the point of the local video server is that
 * there is one.
 *
 * [container] is the MIME type. Receivers use it to choose a player, and getting it wrong is the
 * difference between playing and a blank screen, so it is derived from the video rather than
 * guessed at a single default.
 *
 * [headers] exists in the protocol and we deliberately do not use it. The local server already
 * re-injects whatever the extension required, so what the receiver fetches is a plain URL with
 * nothing to replay — which is the entire reason casting is possible here at all.
 */
@Serializable
internal data class PlayMessage(
    val container: String,
    val url: String,
    val time: Double = 0.0,
    val speed: Double = 1.0,
)

@Serializable
internal data class SeekMessage(
    val time: Double,
)

@Serializable
internal data class SetSpeedMessage(
    val speed: Double,
)

@Serializable
internal data class SetVolumeMessage(
    /** 0.0 to 1.0. */
    val volume: Double,
)

/**
 * Where the receiver has got to.
 *
 * This is the only message that matters coming back: it is what lets the phone show a real position
 * instead of a guess, and what tells us playback ended.
 *
 * [state] is the receiver's, and the protocol numbers it rather than naming it: 0 idle, 1 playing,
 * 2 paused. Modelled as a number and interpreted at the one place that cares, so a receiver adding
 * a fourth state does not fail to decode here.
 */
@Serializable
internal data class PlaybackUpdateMessage(
    val generationTime: Long = 0,
    val time: Double = 0.0,
    val duration: Double = 0.0,
    val state: Int = 0,
    val speed: Double = 1.0,
)

@Serializable
internal data class PlaybackErrorMessage(
    val message: String,
)
