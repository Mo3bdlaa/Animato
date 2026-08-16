package animato.anime.player.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Pick a television.
 *
 * ## Why the list can be empty and that is fine
 *
 * mDNS answers in its own time and some receivers only advertise once they are on their home
 * screen. So the sheet opens with a spinner and a sentence rather than an error, and a device
 * appearing four seconds later is the normal case rather than a recovery.
 *
 * ## The refusal
 *
 * A video whose URL only loads with the extension's own headers cannot be cast — the television
 * fetches it and gets a 403. That is said here, before anything connects, because the alternative
 * is a receiver showing a black screen and nobody being able to tell whether the television, the
 * network or the app is at fault. [CastResult.NeedsHeaders] carries what would have to change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastSheet(
    controller: CastController,
    request: CastRequest,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val discovery = remember { FCastDiscovery(context) }
    val devices by remember { discovery.devices() }.collectAsState(initial = emptyList())
    val state by controller.state.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = SheetRadius, topEnd = SheetRadius),
    ) {
        Column(
            modifier = Modifier.padding(bottom = MaterialTheme.padding.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Text(
                text = stringResource(AYMR.strings.cast_title),
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            when {
                request.headerBound -> Notice(stringResource(AYMR.strings.cast_needs_headers))

                state is CastState.Failed ->
                    Notice((state as CastState.Failed).reason, isError = true)

                devices.isEmpty() -> Searching()
            }

            devices.forEach { device ->
                DeviceRow(
                    device = device,
                    connected = state.deviceName() == device.name,
                    enabled = !request.headerBound,
                    onClick = { controller.cast(device, request) },
                )
            }

            if (controller.isCasting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    OutlinedButton(onClick = { controller.disconnect() }) {
                        Text(stringResource(AYMR.strings.cast_leave_playing))
                    }
                    OutlinedButton(onClick = { controller.stop() }) {
                        Text(stringResource(AYMR.strings.cast_stop))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: CastDevice,
    connected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        Icon(
            imageVector = if (connected) Icons.Outlined.CastConnected else Icons.Outlined.Cast,
            contentDescription = null,
            tint = if (connected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // The address, because on a network with two identically-named receivers it is the
                // only thing that tells them apart.
                text = device.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Searching() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(SpinnerSize))
        Text(
            text = stringResource(AYMR.strings.cast_searching),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Notice(text: String, isError: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.padding(
            horizontal = MaterialTheme.padding.medium,
            vertical = MaterialTheme.padding.small,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun CastState.deviceName(): String? = when (this) {
    is CastState.Connected -> deviceName
    is CastState.Playing -> deviceName
    else -> null
}

private val SheetRadius = 28.dp
private val RowHeight = 64.dp
private val SpinnerSize = 20.dp
