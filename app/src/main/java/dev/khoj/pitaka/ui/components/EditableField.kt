package dev.khoj.pitaka.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.khoj.pitaka.R
import dev.khoj.pitaka.ui.contribute.LocalizedText

/**
 * A single inline persisted field that toggles between a read view and an
 * edit view:
 *
 *  - Collapsed (a value is set): renders the label + the saved value as text,
 *    with an Edit (pencil) button. Tapping Edit opens the input.
 *  - Editing: renders an [OutlinedTextField] with a Save button (enabled only
 *    when the trimmed draft differs from the saved value) and, when a prior
 *    value exists, a Cancel button that reverts without saving.
 *
 * Empty-state behaviour (decision A): when [value] is blank, the field starts
 * in edit mode so first entry needs no extra tap. Saving an empty value
 * collapses back to the box (this is how an optional field is "removed").
 *
 * Single source of truth for this behaviour so every inline persisted field
 * across the app (Settings library name/local, Publish contact fields) behaves
 * identically.
 *
 * @param value the currently-saved value (from DataStore/prefs).
 * @param onSave called with the trimmed draft when the user taps Save.
 */
@Composable
fun EditableField(
    labelRes: Int,
    value: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    helpRes: Int? = null,
    placeholder: String? = null,
    valuePlaceholder: String = "—",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    // editing is keyed on `value`: an external load or a save that changes the
    // saved value re-evaluates the initial mode. A blank value => start editing.
    var editing by remember(value) { mutableStateOf(value.isBlank()) }
    var draft by remember(value) { mutableStateOf(value) }

    Column(modifier = modifier.fillMaxWidth()) {
        LocalizedText(
            labelRes,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (helpRes != null) {
            LocalizedText(
                helpRes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))

        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = singleLine,
                placeholder = placeholder?.let {
                    {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                // Cancel is only meaningful when there's a saved value to revert
                // to; for a never-set field there's nothing to cancel back to.
                if (value.isNotBlank()) {
                    TextButton(onClick = {
                        draft = value
                        editing = false
                    }) { Text(stringResource(R.string.common_cancel)) }
                }
                TextButton(
                    onClick = {
                        val trimmed = draft.trim()
                        onSave(trimmed)
                        draft = trimmed
                        // Collapse to read view only when something was saved;
                        // an empty save leaves the box open for entry.
                        editing = trimmed.isNotBlank()
                    },
                    enabled = draft.trim() != value,
                ) { Text(stringResource(R.string.common_save)) }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value.ifBlank { valuePlaceholder },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                IconButton(onClick = {
                    draft = value
                    editing = true
                }) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.common_edit_cd, stringResource(labelRes)),
                    )
                }
            }
        }
    }
}
