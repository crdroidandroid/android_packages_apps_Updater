/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settingslib.spa.widget.preference.ListPreference
import com.android.settingslib.spa.widget.preference.ListPreferenceModel
import com.android.settingslib.spa.widget.dialog.AlertDialogButton
import com.android.settingslib.spa.widget.dialog.SettingsAlertDialogWithIcon
import com.android.settingslib.spa.widget.preference.ListPreferenceOption
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.scaffold.RegularScaffold
import com.android.settingslib.spa.widget.ui.Category
import kotlinx.coroutines.launch
import org.lineageos.updater.R
import org.lineageos.updater.UpdaterApplication
import org.lineageos.updater.data.CheckInterval
import org.lineageos.updater.data.UserPreferencesRepository
import org.lineageos.updater.deviceinfo.DeviceInfoUtils
import org.lineageos.updater.download.ParallelDownloadClient
import org.lineageos.updater.download.SourceForgeMirrors.MIRROR_ASK
import org.lineageos.updater.download.SourceForgeMirrors.MIRROR_AUTO
import org.lineageos.updater.download.SourceForgeMirrors.MIRROR_FASTEST
import org.lineageos.updater.util.BatteryMonitor
import java.io.File

private val CONNECTION_OPTIONS = listOf(1, 2, 4, 6, 8, 12, 16)
    .filter { it <= ParallelDownloadClient.MAX_CONNECTIONS }

@Composable
fun PreferencesScreen() {
    val context = LocalContext.current
    val application = remember(context) { context.applicationContext as UpdaterApplication }
    val repository = application.userPreferencesRepository
    val batteryMonitor = application.batteryMonitor
    val isABDevice = remember { DeviceInfoUtils.isABDevice }
    val showRecoveryUpdate = remember { installRecoveryScriptExists() }
    RegularScaffold(title = stringResource(R.string.display_name)) {
        PreferencesContent(repository, batteryMonitor, isABDevice, showRecoveryUpdate)
    }
}

@Composable
private fun PreferencesContent(
    repository: UserPreferencesRepository,
    batteryMonitor: BatteryMonitor,
    isABDevice: Boolean,
    showRecoveryUpdate: Boolean,
) {
    val coroutineScope = rememberCoroutineScope()
    val abPerfMode by repository.abPerfModeFlow.collectAsStateWithLifecycle(false)
    val batteryState by batteryMonitor.batteryState.collectAsStateWithLifecycle(
        batteryMonitor.currentBatteryState
    )
    val autoDelete by repository.autoDeleteFlow.collectAsStateWithLifecycle(true)
    val streamUpdates by repository.streamUpdatesFlow.collectAsStateWithLifecycle(true)
    val checkInterval by repository.checkIntervalFlow.collectAsStateWithLifecycle(CheckInterval.default)
    val meteredNetworkWarning by repository.meteredNetworkWarningFlow.collectAsStateWithLifecycle(
        true
    )
    val periodicCheckEnabled by repository.periodicCheckEnabledFlow.collectAsStateWithLifecycle(true)
    val downloadConnections by repository.downloadConnectionsFlow.collectAsStateWithLifecycle(
        ParallelDownloadClient.DEFAULT_CONNECTIONS
    )
    var showConnectionsDialog by rememberSaveable { mutableStateOf(false) }
    val downloadMirror by repository.downloadMirrorFlow.collectAsStateWithLifecycle(MIRROR_AUTO)
    var showMirrorDialog by rememberSaveable { mutableStateOf(false) }
    val mirrorOptions = listOf(
        MIRROR_AUTO to stringResource(R.string.updater_download_mirror_auto),
        MIRROR_FASTEST to stringResource(R.string.updater_download_mirror_fastest),
        MIRROR_ASK to stringResource(R.string.updater_download_mirror_choose),
    )
    val downloadMirrorSummary = mirrorOptions.first { it.first == downloadMirror }.second
    var recoveryUpdateEnabled by remember { mutableStateOf(repository.getRecoveryUpdateEnabled()) }

    val autoUpdatesCheckSummary = stringResource(R.string.menu_auto_updates_check_summary)
    val autoDeleteUpdatesSummary = stringResource(R.string.menu_auto_delete_updates_summary)
    val streamUpdatesSummary = stringResource(R.string.menu_stream_updates_summary)
    val meteredNetworkWarningSummary = stringResource(R.string.menu_metered_network_warning_summary)
    val abPerfModeSummary = stringResource(R.string.menu_ab_perf_mode_summary)
    val abPerfModeChargingSummary = stringResource(R.string.menu_ab_perf_mode_summary_charging)
    val updateRecoverySummary = stringResource(R.string.menu_update_recovery_summary)
    val downloadConnectionsSummary = stringResource(
        R.string.updater_menu_download_connections_summary,
        pluralStringResource(
            R.plurals.updater_download_connections_count,
            downloadConnections,
            downloadConnections,
        ),
    )
    val selectedCheckInterval = remember(checkInterval) {
        object : IntState {
            override val intValue = checkInterval.ordinal
        }
    }

    Category(title = stringResource(R.string.pref_category_background_sync)) {
        SwitchPreference(object : SwitchPreferenceModel {
            override val title = stringResource(R.string.menu_auto_updates_check)
            override val summary = { autoUpdatesCheckSummary }
            override val checked = { periodicCheckEnabled }
            override val onCheckedChange: (Boolean) -> Unit = { value ->
                coroutineScope.launch { repository.setPeriodicCheckEnabled(value) }
            }
        })

        ListPreference(object : ListPreferenceModel {
            override val title = stringResource(R.string.menu_auto_updates_check_interval)
            override val enabled = { periodicCheckEnabled }
            override val options = listOf(
                ListPreferenceOption(
                    id = CheckInterval.DAILY.ordinal,
                    text = stringResource(R.string.time_unit_day),
                ),
                ListPreferenceOption(
                    id = CheckInterval.WEEKLY.ordinal,
                    text = stringResource(R.string.time_unit_week),
                ),
                ListPreferenceOption(
                    id = CheckInterval.MONTHLY.ordinal,
                    text = stringResource(R.string.time_unit_month),
                ),
            )
            override val selectedId = selectedCheckInterval
            override val onIdSelected: (Int) -> Unit = { id ->
                val interval = CheckInterval.entries.getOrElse(id) { CheckInterval.default }
                coroutineScope.launch { repository.setCheckInterval(interval) }
            }
        })
    }

    Category(title = stringResource(R.string.pref_category_download_install)) {
        if (isABDevice) {
            SwitchPreference(object : SwitchPreferenceModel {
                override val title = stringResource(R.string.menu_stream_updates)
                override val summary = { streamUpdatesSummary }
                override val checked = { streamUpdates }
                override val onCheckedChange: (Boolean) -> Unit = { value ->
                    coroutineScope.launch { repository.setStreamUpdates(value) }
                }
            })
        } else {
            SwitchPreference(object : SwitchPreferenceModel {
                override val title = stringResource(R.string.menu_auto_delete_updates)
                override val summary = { autoDeleteUpdatesSummary }
                override val checked = { autoDelete }
                override val onCheckedChange: (Boolean) -> Unit = { value ->
                    coroutineScope.launch { repository.setAutoDelete(value) }
                }
            })
        }

        SwitchPreference(object : SwitchPreferenceModel {
            override val title = stringResource(R.string.menu_metered_network_warning)
            override val summary = { meteredNetworkWarningSummary }
            override val checked = { meteredNetworkWarning }
            override val onCheckedChange: (Boolean) -> Unit = { value ->
                coroutineScope.launch { repository.setMeteredNetworkWarning(value) }
            }
        })

        Preference(object : PreferenceModel {
            override val title = stringResource(R.string.updater_menu_download_connections)
            override val summary = { downloadConnectionsSummary }
            override val onClick: () -> Unit = { showConnectionsDialog = true }
        })

        Preference(object : PreferenceModel {
            override val title = stringResource(R.string.updater_menu_download_mirror)
            override val summary = { downloadMirrorSummary }
            override val onClick: () -> Unit = { showMirrorDialog = true }
        })

        if (isABDevice) {
            SwitchPreference(object : SwitchPreferenceModel {
                override val title = stringResource(R.string.menu_ab_perf_mode)
                override val summary = {
                    if (batteryState.isAcCharging) {
                        abPerfModeChargingSummary
                    } else {
                        abPerfModeSummary
                    }
                }
                override val changeable = { !batteryState.isAcCharging }
                override val checked = { batteryState.isAcCharging || abPerfMode }
                override val onCheckedChange: (Boolean) -> Unit = { value ->
                    coroutineScope.launch { repository.setAbPerfMode(value) }
                }
            })
        }

        if (showRecoveryUpdate) {
            SwitchPreference(object : SwitchPreferenceModel {
                override val title = stringResource(R.string.menu_update_recovery)
                override val summary = { updateRecoverySummary }
                override val checked = { recoveryUpdateEnabled }
                override val onCheckedChange: (Boolean) -> Unit = { value ->
                    recoveryUpdateEnabled = value
                    repository.setRecoveryUpdateEnabled(value)
                }
            })
        }
    }

    if (showConnectionsDialog) {
        RadioListDialog(
            icon = Icons.Outlined.Speed,
            title = stringResource(R.string.updater_menu_download_connections),
            message = stringResource(R.string.updater_download_connections_dialog_message),
            options = CONNECTION_OPTIONS.map { option ->
                option.toString() to pluralStringResource(
                    R.plurals.updater_download_connections_count, option, option,
                )
            },
            initial = downloadConnections.toString(),
            onConfirm = { value ->
                coroutineScope.launch { repository.setDownloadConnections(value.toInt()) }
            },
            onDismiss = { showConnectionsDialog = false },
        )
    }

    if (showMirrorDialog) {
        RadioListDialog(
            icon = Icons.Outlined.Public,
            title = stringResource(R.string.updater_menu_download_mirror),
            message = stringResource(R.string.updater_download_mirror_dialog_message),
            options = mirrorOptions,
            initial = downloadMirror,
            onConfirm = { value ->
                coroutineScope.launch { repository.setDownloadMirror(value) }
            },
            onDismiss = { showMirrorDialog = false },
        )
    }
}

/** Single-choice dialog; [options] are (key, label) pairs. Long lists scroll. */
@Composable
private fun RadioListDialog(
    icon: ImageVector,
    title: String,
    message: String?,
    options: List<Pair<String, String>>,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(initial) }

    SettingsAlertDialogWithIcon(
        onDismissRequest = onDismiss,
        icon = icon,
        confirmButton = AlertDialogButton(text = stringResource(android.R.string.ok)) {
            onDismiss()
            onConfirm(selected)
        },
        dismissButton = AlertDialogButton(text = stringResource(android.R.string.cancel)) {
            onDismiss()
        },
        title = title,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (message != null) {
                    Text(text = message, style = MaterialTheme.typography.bodyMedium)
                }
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                ) {
                    options.forEach { (key, label) ->
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
        },
    )
}

private fun installRecoveryScriptExists() = File("/vendor/bin/install-recovery.sh").exists()
