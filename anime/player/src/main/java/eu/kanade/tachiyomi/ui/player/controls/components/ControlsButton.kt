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

package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import animato.ui.tv.tvFocusRing
import eu.kanade.tachiyomi.ui.player.controls.LocalPlayerButtonsClickEvent
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.padding

/**
 * Every button on the player, and the one place a remote can be made to reach them.
 *
 * All three overloads share two pieces of television behaviour, and they are here rather than at
 * each call site because this component *is* every button on the screen — the settings, the
 * subtitles, the episode list, the quality picker, the custom buttons. Adding it in one place is
 * what makes the whole overlay reachable at once.
 *
 * **A visible ring.** These were already focusable: `combinedClickable` makes a node focusable, so
 * a D-pad could always move onto them. What it could not do is *show* that it had, because these
 * buttons pass `indication = null` and layer their own ripple — and a ripple is a touch affordance
 * nobody is watching for from across a room. A button you can focus but cannot see focused is, in
 * practice, a button that is not there.
 *
 * **A hide timer that waits.** The controls fade out after a few seconds unless something resets
 * the clock, and the only thing that reset it was a click. A remote spends several presses moving
 * before it ever clicks, so the overlay would vanish mid-journey. Focus now resets it too, through
 * the same event a click uses — see `LocalPlayerButtonsClickEvent`.
 */
@Composable
fun ControlsButton(
    icon: ImageVector,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    title: String? = null,
    color: Color = Color.White,
    horizontalSpacing: Dp = MaterialTheme.padding.medium,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickEvent = LocalPlayerButtonsClickEvent.current
    val iconColor = if (enabled) color else color.copy(alpha = DISABLED_ALPHA)

    KeepControlsAliveWhileFocused(interactionSource)

    Box(
        modifier = modifier
            .tvFocusRing(interactionSource, CircleShape)
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    clickEvent()
                    onClick()
                },
                onLongClick = onLongClick,
                interactionSource = interactionSource,
                indication = null,
            )
            .clip(CircleShape)
            .indication(
                interactionSource,
                ripple(),
            )
            .padding(
                vertical = MaterialTheme.padding.medium,
                horizontal = horizontalSpacing,
            ),
    ) {
        Icon(
            icon,
            title,
            tint = iconColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun ControlsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    color: Color = Color.White,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val clickEvent = LocalPlayerButtonsClickEvent.current

    KeepControlsAliveWhileFocused(interactionSource)

    Box(
        modifier = modifier
            .tvFocusRing(interactionSource, CircleShape)
            .combinedClickable(
                onClick = {
                    clickEvent()
                    onClick()
                },
                onLongClick = onLongClick,
                interactionSource = interactionSource,
                indication = null,

            )
            .clip(CircleShape)
            .indication(
                interactionSource,
                ripple(),
            )
            .padding(MaterialTheme.padding.medium),
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun FilledControlsButton(
    text: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickEvent = LocalPlayerButtonsClickEvent.current

    KeepControlsAliveWhileFocused(interactionSource)

    Box(
        modifier = modifier.padding(end = MaterialTheme.padding.small),
    ) {
        Button(onClick = {}) {
            Text(text = text)
        }
        // The ring goes on the transparent overlay that owns the click, not on the Button beneath
        // it: the overlay is the focusable node, so it is the one whose focus state there is
        // anything to draw.
        Box(
            modifier = Modifier
                .matchParentSize()
                .tvFocusRing(interactionSource)
                .combinedClickable(
                    onClick = {
                        clickEvent()
                        onClick()
                    },
                    onLongClick = onLongClick,
                    interactionSource = interactionSource,
                    indication = null,
                ),
        )
    }
}

/**
 * While this button has focus, the controls are not idle.
 *
 * The overlay's hide timer is reset by `LocalPlayerButtonsClickEvent`, which until now only a click
 * fired. That is the right signal for a finger, which arrives at a button and presses it in one
 * motion, and the wrong one for a remote, which may press *left* six times on the way somewhere —
 * each press a deliberate act, none of them a click. The controls would go out underneath it.
 *
 * Firing the existing event rather than adding a second path, so there is one definition of "the
 * user is still here" and the two cannot drift apart.
 */
@Composable
private fun KeepControlsAliveWhileFocused(interactionSource: InteractionSource) {
    val clickEvent = LocalPlayerButtonsClickEvent.current
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) clickEvent()
    }
}

@Preview
@Composable
private fun PreviewControlsButton() {
    ControlsButton(
        Icons.Default.CatchingPokemon,
        onClick = {},
    )
}
