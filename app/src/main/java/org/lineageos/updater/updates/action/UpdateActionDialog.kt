/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updates.action

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.widget.dialog.AlertDialogButton
import com.android.settingslib.spa.widget.dialog.SettingsAlertDialogWithIcon
import org.lineageos.updater.R

data class AlertDialogState(
    val onConfirm: () -> Unit = {},
    val showDismiss: Boolean = false,
    val title: String,
    val text: AnnotatedString,
    /** When set, the dialog shows these (key, label) choices; OK passes the selected key. */
    val options: List<Pair<String, String>> = emptyList(),
    val onOptionConfirmed: (String) -> Unit = {},
)

@Composable
fun UpdateActionDialog(
    dialog: AlertDialogState,
    onDismiss: () -> Unit,
) {
    var selected by remember(dialog) { mutableStateOf(dialog.options.firstOrNull()?.first) }

    SettingsAlertDialogWithIcon(
        onDismissRequest = onDismiss,
        icon = ImageVector.vectorResource(R.drawable.ic_notification),
        confirmButton = AlertDialogButton(text = stringResource(android.R.string.ok)) {
            onDismiss()
            val choice = selected
            if (dialog.options.isNotEmpty() && choice != null) {
                dialog.onOptionConfirmed(choice)
            } else {
                dialog.onConfirm()
            }
        },
        dismissButton = if (dialog.showDismiss) {
            AlertDialogButton(text = stringResource(android.R.string.cancel)) {
                onDismiss()
            }
        } else {
            null
        },
        title = dialog.title,
        text = {
            Column {
                if (dialog.text.isNotEmpty()) {
                    Text(text = dialog.text)
                }
                if (dialog.options.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .selectableGroup(),
                    ) {
                        dialog.options.forEach { (key, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = key == selected,
                                        role = Role.RadioButton,
                                        onClick = { selected = key },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = key == selected, onClick = null)
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 16.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
