package animato.app.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Correcting what a source got wrong.
 *
 * Every field starts filled with what is currently shown, so the dialog opens on the truth as the
 * app knows it and an edit is a change to something rather than an entry into a blank. Emptying a
 * field is the way back: it removes the correction and the entry follows its source again, which is
 * why the footnote says so — an empty box otherwise reads as "this will be blank".
 *
 * Genres are one line of comma-separated words rather than chips with an add button. The list is
 * usually three to eight items and is edited about once in the lifetime of an entry; a chip editor
 * is a lot of interface to build and to learn for that.
 */
@Composable
fun EditEntryDialog(
    state: EntryState,
    onSave: (EntryOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(state.title) }
    var author by remember { mutableStateOf(state.author.orEmpty()) }
    var artist by remember { mutableStateOf(state.artist.orEmpty()) }
    var description by remember { mutableStateOf(state.description.orEmpty()) }
    var genres by remember { mutableStateOf(state.genres.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.action_edit_details)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Field(title, { title = it }, MR.strings.title)
                Field(author, { author = it }, MR.strings.author)
                Field(artist, { artist = it }, MR.strings.artist)
                Field(genres, { genres = it }, AYMR.strings.edit_details_genres, singleLine = false)
                Field(description, { description = it }, AYMR.strings.edit_details_description, singleLine = false)

                Text(
                    text = stringResource(AYMR.strings.edit_details_empty_follows_source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // What the source itself says, so a corrected title can be checked against the
                // thing it replaced without leaving the dialog.
                if (state.sourceTitle.isNotBlank() && state.sourceTitle != title) {
                    Text(
                        text = stringResource(AYMR.strings.edit_details_source_says, state.sourceTitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        EntryOverride(
                            // Compared against what the source says, not against what is on screen:
                            // typing a field back to the source's own value is a way of undoing a
                            // correction, and storing it would freeze the entry at today's value.
                            title = title.trimmedOrNull()?.takeIf { it != state.sourceTitle },
                            author = author.trimmedOrNull(),
                            artist = artist.trimmedOrNull(),
                            description = description.trimmedOrNull(),
                            genres = genres.split(',')
                                .mapNotNull { it.trimmedOrNull() }
                                .takeIf { it.isNotEmpty() },
                        ),
                    )
                    onDismiss()
                },
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: StringResource,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 5,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun String.trimmedOrNull(): String? = trim().takeIf { it.isNotEmpty() }
