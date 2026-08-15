package animato.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.core.common.preference.Preference as PreferenceData

// Settings rows that Mihon's framework does not have an item type for.
//
// Aniyomi got these by adding three variants to Mihon's `PreferenceItem`. That is not available
// from another module — the hierarchy is `sealed` — and it turns out not to be necessary either:
// `CustomPreference` takes a composable and renders exactly it, which is the extension point. These
// are that composable, wrapped so a settings screen declares them the same way it declares any
// other row.
//
// The consequence worth having is that upstream keeps one preference hierarchy and we keep none.

/**
 * A text preference whose value is more than one line — a subtitle track name, a header, an
 * argument list. The dialog is a text area rather than a single-line field, and blank is allowed
 * when [canBeBlank] says so, because "no value" is a real setting for most of these.
 */
fun multiLineEditTextPreference(
    preference: PreferenceData<String>,
    title: String,
    subtitle: String? = "%s",
    icon: ImageVector? = null,
    canBeBlank: Boolean = false,
    enabled: Boolean = true,
    onValueChanged: suspend (String) -> Boolean = { true },
) = Preference.PreferenceItem.CustomPreference(title) {
    val value by preference.collectAsState()
    WhenEnabled(enabled) {
        EditTextDialogRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            value = value,
            canBeBlank = canBeBlank,
            singleLine = false,
            onConfirm = { newValue ->
                val accepted = onValueChanged(newValue)
                if (accepted) preference.set(newValue)
                accepted
            },
        )
    }
}

/**
 * A text preference with an explanation inside the dialog and a rule about what is valid.
 *
 * Used where the value is a syntax rather than a word — a filename pattern, a socket address — and
 * a title alone cannot say what a correct one looks like.
 */
fun editTextInfoPreference(
    preference: PreferenceData<String>,
    title: String,
    dialogSubtitle: String?,
    subtitle: String? = "%s",
    icon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    validate: (String) -> Boolean = { true },
    errorMessage: @Composable (String) -> String = { "" },
    enabled: Boolean = true,
    onValueChanged: suspend (String) -> Boolean = { true },
) = Preference.PreferenceItem.CustomPreference(title) {
    val value by preference.collectAsState()
    WhenEnabled(enabled) {
        EditTextDialogRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            value = value,
            dialogSubtitle = dialogSubtitle,
            canBeBlank = true,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            validate = validate,
            errorMessage = errorMessage,
            onConfirm = { newValue ->
                val accepted = onValueChanged(newValue)
                if (accepted) preference.set(newValue)
                accepted
            },
        )
    }
}

/**
 * Mihon hides a disabled preference rather than greying it out, and does it for the whole framework
 * in one place — which a `CustomPreference` never reaches, because its own `enabled` is always true.
 * So these do it themselves, with the same animation, and a disabled row reads the same either way.
 */
@Composable
private fun WhenEnabled(enabled: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = enabled,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        content = { content() },
    )
}

/**
 * The row and dialog all three share.
 *
 * Deliberately one composable with parameters rather than three near-copies: the differences are a
 * line count, a caption and a validation rule, and three files that drift apart would be worse than
 * one that says which knobs exist.
 */
@Composable
fun EditTextDialogRow(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    value: String,
    onConfirm: suspend (String) -> Boolean,
    dialogSubtitle: String? = null,
    canBeBlank: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    validate: (String) -> Boolean = { true },
    errorMessage: @Composable (String) -> String = { "" },
) {
    var isDialogShown by remember { mutableStateOf(false) }

    TextPreferenceWidget(
        title = title,
        subtitle = subtitle?.format(value),
        icon = icon,
        onPreferenceClick = { isDialogShown = true },
    )

    if (!isDialogShown) return

    val scope = rememberCoroutineScope()
    val onDismissRequest = { isDialogShown = false }
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value))
    }
    val text = textFieldValue.text
    val isBlankAndShouldNotBe = text.isBlank() && !canBeBlank
    val isInvalid = isBlankAndShouldNotBe || !validate(text)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier else Modifier.heightIn(min = MultiLineFieldHeight)),
                supportingText = {
                    val message = when {
                        isInvalid && text.isNotBlank() -> errorMessage(text)
                        else -> dialogSubtitle.orEmpty()
                    }
                    if (message.isNotEmpty()) Text(text = message)
                },
                trailingIcon = {
                    when {
                        isBlankAndShouldNotBe -> Icon(imageVector = Icons.Filled.Error, contentDescription = null)
                        text.isNotBlank() -> IconButton(onClick = { textFieldValue = TextFieldValue("") }) {
                            Icon(imageVector = Icons.Filled.Cancel, contentDescription = null)
                        }
                    }
                },
                isError = (isInvalid && text.isNotBlank()) || isBlankAndShouldNotBe,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
            )
        },
        properties = DialogProperties(usePlatformDefaultWidth = true),
        confirmButton = {
            TextButton(
                enabled = text != value && !isInvalid,
                onClick = {
                    scope.launch {
                        if (onConfirm(text)) onDismissRequest()
                    }
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

private val MultiLineFieldHeight = 160.dp
