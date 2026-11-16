package me.jhot.swiftcurrent.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.ui.design.SettingsPage

/**
 * Advanced: back up and restore, export of the search history, and the way back into first-run setup.
 * The recovery row exists only while there is unreadable data to recover.
 */
@Composable
fun AdvancedSettings(
    hasUnreadable: Boolean,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
    onRecover: () -> Unit,
    onSetup: () -> Unit,
    onExportSearchHistory: () -> Unit,
) {
    val section = stringResource(R.string.settings_advanced_backup_section)
    val backup = stringResource(R.string.settings_advanced_backup)
    val backupDesc = stringResource(R.string.settings_advanced_backup_desc)
    val restore = stringResource(R.string.settings_advanced_restore)
    val restoreDesc = stringResource(R.string.settings_advanced_restore_desc)
    val recover = stringResource(R.string.settings_advanced_recover)
    val recoverDesc = stringResource(R.string.settings_advanced_recover_desc)
    val exportHistory = stringResource(R.string.settings_advanced_export_history)
    val exportHistoryDesc = stringResource(R.string.settings_advanced_export_history_desc)
    val setup = stringResource(R.string.settings_advanced_setup)
    val setupDesc = stringResource(R.string.settings_advanced_setup_desc)
    Box(Modifier.fillMaxSize().testTag("settings-advanced")) {
        SettingsPage(title = stringResource(R.string.settings_advanced)) {
            row(icon = {}, title = setup, description = setupDesc, onClick = onSetup)
            section(section)
            row(icon = {}, title = backup, description = backupDesc, onClick = onBackUp)
            row(icon = {}, title = restore, description = restoreDesc, onClick = onRestore)
            if (hasUnreadable) row(icon = {}, title = recover, description = recoverDesc, onClick = onRecover)
            row(icon = {}, title = exportHistory, description = exportHistoryDesc, onClick = onExportSearchHistory)
        }
    }
}
