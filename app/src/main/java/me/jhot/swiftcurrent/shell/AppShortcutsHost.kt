package me.jhot.swiftcurrent.shell

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.core.orElse
import me.jhot.swiftcurrent.shortcuts.Creator
import me.jhot.swiftcurrent.shortcuts.ShortcutAccess
import me.jhot.swiftcurrent.shortcuts.ShortcutEntry
import me.jhot.swiftcurrent.shortcuts.ShortcutPrefs
import me.jhot.swiftcurrent.ui.settings.AppShortcutGroups
import me.jhot.swiftcurrent.ui.settings.AppShortcutsActions
import me.jhot.swiftcurrent.ui.settings.AppShortcutsPage

/**
 * The per-app shortcuts page wired to the launcher's data, independent of which screen shows it:
 * the settings host places it wherever it likes and only supplies [onBack] and [onMessage].
 * Toggles go through [ShellData.update] into `Settings.disabledShortcuts`, and the written data is
 * published at once, so search sees the change on its next query.
 */
@Composable
fun AppShortcutsHost(
    deps: LauncherDependencies,
    model: ShellData,
    io: CoroutineDispatcher,
    onBack: () -> Unit,
    onMessage: (ShellMessage) -> Unit,
) {
    val data by model.data.collectAsState()
    val apps by model.namedApps.collectAsState()
    val scope = rememberCoroutineScope()
    val start = rememberSettingsLauncher(deps, io, onMessage)
    var openApp by rememberSaveable { mutableStateOf<String?>(null) }
    var contactsGranted by remember { mutableStateOf(deps.contactsGranted()) }
    // Shortcuts need the default home app; until the first answer the badge is not shown.
    var shortcutsGranted by remember { mutableStateOf(true) }
    // The grant happens in system settings; read it again when the launcher is shown again.
    WhileStarted(deps) {
        contactsGranted = orElse(contactsGranted) { withContext(io) { deps.contactsGranted() } }
        shortcutsGranted = orElse(true) { withContext(io) { deps.shortcuts.access() == ShortcutAccess.Granted } }
    }

    val system by produceIo(emptyList<ShortcutEntry>(), io) { deps.shortcuts.entries() }
    val creators by produceIo(emptyList<me.jhot.swiftcurrent.shortcuts.Creator>(), io) { deps.creators.creators() }

    val appLabels = remember(apps) { apps.associate { it.packageName to it.label } }
    val shortcutLabels = remember(system) { system.associate { it.packageName to it.appLabel } }
    val creatorLabels = remember(creators) { creators.associate { it.packageName to it.appLabel } }
    // Only a shortcut's app that nothing above names needs the package manager.
    val extraLabels by rememberPackageLabels(
        deps, io,
        data.userShortcuts.map { it.appPackage }.filter { it !in appLabels && it !in shortcutLabels && it !in creatorLabels },
    )
    fun labelOf(pkg: String): String = appLabels[pkg] ?: shortcutLabels[pkg] ?: creatorLabels[pkg] ?: extraLabels[pkg] ?: pkg

    val groups = remember(system, data.userShortcuts, creators, data.settings.disabledShortcuts, extraLabels, apps) {
        AppShortcutGroups.build(system, data.userShortcuts, creators, data.settings.disabledShortcuts, ::labelOf)
    }
    val prefs = remember(model, groups) {
        ShortcutPrefs(
            disabled = { model.data.value.settings.disabledShortcuts },
            edit = { f ->
                model.update { it.copy(settings = f(it.settings)) }
            },
            keysOf = { pkg -> groups.firstOrNull { it.packageName == pkg }?.keys.orEmpty() },
        )
    }
    val create = rememberShortcutCreator(deps, model, io, onMessage) { }
    var webLinkFor by rememberSaveable(stateSaver = WebLinkCreatorSaver) { mutableStateOf<Creator?>(null) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        AppShortcutsPage(
            groups = groups,
            sources = AppShortcutGroups.sources(contactsGranted, shortcutsGranted),
            openApp = openApp,
            onOpenApp = { openApp = it },
            actions = AppShortcutsActions(
                onToggle = { key, on -> scope.launch { prefs.setEnabled(key, on) } },
                onToggleAll = { pkg, on -> scope.launch { prefs.setAll(pkg, on) } },
                onDelete = { row ->
                    scope.launch { deps.userShortcuts.delete(row.id, model::update) }
                },
                onCreate = { if (it.isWebLink) webLinkFor = it else create(it) },
                onGrantShortcuts = { start(Intent(Settings.ACTION_HOME_SETTINGS)) },
                onGrantContacts = { start(appDetailsIntent(context.packageName)) },
            ),
            onBack = onBack,
        )
        WebLinkHost(webLinkFor, deps, model, io, onMessage, onAdded = { }, onDismiss = { webLinkFor = null })
    }
}
