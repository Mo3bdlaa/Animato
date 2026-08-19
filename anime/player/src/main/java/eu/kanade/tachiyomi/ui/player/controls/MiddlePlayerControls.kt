package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import animato.anime.player.R
import aniyomi.core.common.torrent.TorrentProgress
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import `is`.xyz.mpv.Utils
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs

@Composable
fun MiddlePlayerControls(
    // previous
    hasPrevious: Boolean,
    onSkipPrevious: () -> Unit,

    // middle
    isLoading: Boolean,
    isLoadingEpisode: Boolean,
    /**
     * What the swarm is doing, when the thing being waited for is a torrent.
     *
     * Null for every other kind of video, and the spinner alone is right for those: an HTTP video
     * either arrives or fails, and there is no middle state worth a number.
     */
    torrentProgress: TorrentProgress?,
    controlsShown: Boolean,
    areControlsLocked: Boolean,
    showLoadingCircle: Boolean,
    paused: Boolean,
    gestureSeekAmount: Pair<Int, Int>?,
    onPlayPauseClick: () -> Unit,

    // next
    hasNext: Boolean,
    onSkipNext: () -> Unit,

    enter: EnterTransition,
    exit: ExitTransition,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.large),
    ) {
        AnimatedVisibility(
            visible = controlsShown && !areControlsLocked,
            enter = enter,
            exit = exit,
        ) {
            if (gestureSeekAmount == null) {
                ControlsButton(
                    Icons.Filled.SkipPrevious,
                    onClick = onSkipPrevious,
                    iconSize = 48.dp,
                    enabled = hasPrevious,
                )
            }
        }

        val icon = AnimatedImageVector.animatedVectorResource(R.drawable.anim_play_to_pause)
        val interaction = remember { MutableInteractionSource() }
        when {
            gestureSeekAmount != null -> {
                Text(
                    stringResource(
                        AYMR.strings.player_gesture_seek_indicator,
                        if (gestureSeekAmount.second >= 0) '+' else '-',
                        Utils.prettyTime(abs(gestureSeekAmount.second)),
                        Utils.prettyTime(gestureSeekAmount.first + gestureSeekAmount.second),
                    ),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        shadow = Shadow(Color.Black, blurRadius = 5f),
                    ),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            (isLoading || isLoadingEpisode) && showLoadingCircle ->
                if (torrentProgress != null) {
                    TorrentProgressIndicator(torrentProgress)
                } else {
                    CircularProgressIndicator(Modifier.size(96.dp))
                }
            else -> {
                AnimatedVisibility(
                    visible = controlsShown && !areControlsLocked,
                    enter = enter,
                    exit = exit,
                ) {
                    Image(
                        painter = rememberAnimatedVectorPainter(icon, !paused),
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .clickable(
                                interaction,
                                ripple(),
                                onClick = onPlayPauseClick,
                            )
                            .padding(MaterialTheme.padding.medium),
                        contentDescription = null,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsShown && !areControlsLocked,
            enter = enter,
            exit = exit,
        ) {
            if (gestureSeekAmount == null) {
                ControlsButton(
                    Icons.Filled.SkipNext,
                    onClick = onSkipNext,
                    iconSize = 48.dp,
                    enabled = hasNext,
                )
            }
        }
    }
}

/**
 * The spinner, with the swarm's answer under it.
 *
 * Deliberately a full replacement rather than a caption beside the spinner: this appears in the
 * dead centre of a black screen and is, for the length of the wait, the only thing on it. Three
 * lines — what stage, how full, who is on the other end — is all there is to say, and all of it
 * has to be readable at a glance from across a room.
 *
 * The bar is determinate once the buffer's size is known and indeterminate before, because during
 * the peer search there is genuinely no denominator; a determinate bar at zero would be claiming a
 * measurement that does not exist yet.
 */
@Composable
private fun TorrentProgressIndicator(progress: TorrentProgress) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        modifier = Modifier.padding(horizontal = MaterialTheme.padding.large),
    ) {
        val fraction = progress.fraction
        if (fraction == null) {
            CircularProgressIndicator(Modifier.size(64.dp))
        } else {
            CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(64.dp))
        }

        Text(
            text = stringResource(
                when (progress.stage) {
                    TorrentProgress.Stage.FindingPeers -> AYMR.strings.torrent_stage_finding_peers
                    TorrentProgress.Stage.Buffering -> AYMR.strings.torrent_stage_buffering
                    TorrentProgress.Stage.Ready -> AYMR.strings.torrent_stage_ready
                },
            ),
            style = MaterialTheme.typography.titleMedium.copy(shadow = Shadow(Color.Black, blurRadius = 5f)),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        if (progress.targetBytes > 0) {
            Text(
                text = "${megabytes(progress.loadedBytes)} / ${megabytes(progress.targetBytes)} MB" +
                    "  ·  ${megabytes(progress.bytesPerSecond)} MB/s",
                style = MaterialTheme.typography.bodyMedium.copy(shadow = Shadow(Color.Black, blurRadius = 5f)),
                textAlign = TextAlign.Center,
            )
        }

        // The line that decides what to do. Zero peers is a dead torrent and a reason to back out
        // and pick another stream; a healthy count with a slow bar is a reason to wait.
        Text(
            text = stringResource(
                AYMR.strings.torrent_peers_and_seeders,
                progress.peers,
                progress.seeders,
            ),
            style = MaterialTheme.typography.bodySmall.copy(shadow = Shadow(Color.Black, blurRadius = 5f)),
            color = if (progress.peers == 0) MaterialTheme.colorScheme.error else Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

/** Bytes as a one-decimal megabyte figure, which is the only precision worth reading here. */
private fun megabytes(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return ((mb * 10).toLong() / 10.0).toString()
}
