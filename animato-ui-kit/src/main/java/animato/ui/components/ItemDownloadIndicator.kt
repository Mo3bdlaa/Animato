package animato.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.presentation.core.components.material.IconButtonTokens
import uy.kohesive.injekt.injectLazy

fun Modifier.commonClickable(
    enabled: Boolean,
    hapticFeedback: HapticFeedback,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) = this.combinedClickable(
    enabled = enabled,
    onLongClick = {
        onLongClick()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    },
    onClick = onClick,
    role = Role.Button,
    interactionSource = null,
    indication = ripple(
        bounded = false,
        radius = IconButtonTokens.StateLayerSize / 2,
    ),
)

val IndicatorSize = 26.dp
val IndicatorPadding = 2.dp

// To match composable parameter name when used later
val IndicatorStrokeWidth = IndicatorPadding

val IndicatorModifier = Modifier
    .size(IndicatorSize)
    .padding(IndicatorPadding)
val ArrowModifier = Modifier
    .size(IndicatorSize - 7.dp)

val preferences: DownloadPreferences by injectLazy()
