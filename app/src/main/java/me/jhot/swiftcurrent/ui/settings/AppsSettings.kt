package me.jhot.swiftcurrent.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.CheckBox
import me.jhot.swiftcurrent.ui.design.SettingsPage
import me.jhot.swiftcurrent.ui.design.SheetCard
import me.jhot.swiftcurrent.ui.design.SheetScaffold
import me.jhot.swiftcurrent.ui.design.Toggle

/** Apps: how the list sorts, and the two collections the owner manages (hidden apps, folders). */
@Composable
fun AppsSettings(
    sortByUsage: Boolean,
    hiddenCount: Int,
    folderCount: Int,
    onSortByUsage: (Boolean) -> Unit,
    onHidden: () -> Unit,
    onFolders: () -> Unit,
) {
    val listSection = stringResource(R.string.settings_apps_list_section)
    val sort = stringResource(R.string.settings_apps_sort)
    val sortDesc = stringResource(R.string.settings_apps_sort_desc)
    val sortToggle = stringResource(R.string.settings_apps_sort_toggle)
    val manage = stringResource(R.string.settings_apps_manage_section)
    val hidden = stringResource(R.string.settings_apps_hidden)
    val hiddenDesc = if (hiddenCount == 0) stringResource(R.string.settings_apps_hidden_none) else hiddenCount.toString()
    val folders = stringResource(R.string.settings_apps_folders)
    val foldersDesc = if (folderCount == 0) stringResource(R.string.settings_apps_folders_none) else folderCount.toString()
    Box(Modifier.fillMaxSize().testTag("settings-apps")) {
        SettingsPage(title = stringResource(R.string.settings_apps)) {
            section(listSection)
            row(
                icon = {}, title = sort, description = sortDesc,
                trailing = { Toggle(sortByUsage, sortToggle, "toggle-sort-by-usage", onSortByUsage) },
            )
            section(manage)
            row(icon = {}, title = hidden, description = hiddenDesc, onClick = onHidden)
            row(icon = {}, title = folders, description = foldersDesc, onClick = onFolders)
        }
    }
}

/** Every installed app with a box; ticking one hides it from the list; search still finds it, marked Hidden. */
@Composable
fun HiddenAppsPage(apps: List<AppInfo>, hidden: Set<String>, onChange: (component: String, hidden: Boolean) -> Unit) {
    val section = stringResource(R.string.settings_hidden_section)
    val none = stringResource(R.string.settings_hidden_none)
    val labels = apps.associate { it.component to stringResource(R.string.settings_hidden_toggle, it.label) }
    Box(Modifier.fillMaxSize().testTag("settings-hidden")) {
        SettingsPage(title = stringResource(R.string.settings_hidden_title)) {
            section(section)
            if (apps.isEmpty()) row(icon = {}, title = none)
            apps.forEach { app ->
                row(
                    icon = {},
                    title = app.label,
                    trailing = {
                        CheckBox(app.component in hidden, labels.getValue(app.component), "hide-${app.component}") { onChange(app.component, it) }
                    },
                )
            }
        }
    }
}

/** The folder library with a delete per folder, confirmed first because it also empties favorites and pop-ups. */
@Composable
fun PopupFoldersPage(folders: List<Entry>, labelOf: (Entry) -> String, onDelete: (Entry) -> Unit) {
    var pending by remember { mutableStateOf<String?>(null) }
    val section = stringResource(R.string.settings_folders_section)
    val none = stringResource(R.string.settings_folders_none)
    val deleteLabels = folders.associate { it.id to stringResource(R.string.settings_folders_delete, labelOf(it)) }
    Box(Modifier.fillMaxSize().testTag("settings-folders")) {
        SettingsPage(title = stringResource(R.string.settings_folders_title)) {
            section(section)
            if (folders.isEmpty()) row(icon = {}, title = none)
            folders.forEach { f ->
                row(
                    icon = { DeleteIcon(deleteLabels.getValue(f.id), Modifier.testTag("folder-delete-${f.id}")) { pending = f.id } },
                    title = labelOf(f),
                )
            }
        }
        val target = folders.firstOrNull { it.id == pending }
        if (target != null) {
            SheetScaffold(title = stringResource(R.string.settings_folders_confirm_title, labelOf(target)), onDismiss = { pending = null }) {
                AppText(stringResource(R.string.settings_folders_confirm_body), AppType.body)
                SheetCard(icon = {}, title = stringResource(R.string.settings_folders_confirm_yes), onClick = { pending = null; onDelete(target) })
                SheetCard(icon = {}, title = stringResource(R.string.settings_folders_confirm_no), onClick = { pending = null })
            }
        }
    }
}
