package me.jhot.swiftcurrent.shell

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.agenda.CalendarFilter
import me.jhot.swiftcurrent.agenda.CalendarInfo
import me.jhot.swiftcurrent.core.HomeSpacing
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.orElse
import me.jhot.swiftcurrent.lock.accessibilitySettingsIntent
import me.jhot.swiftcurrent.shortcuts.ShortcutAccess
import me.jhot.swiftcurrent.ui.home.HomeUiState
import me.jhot.swiftcurrent.ui.settings.AdvancedSettings
import me.jhot.swiftcurrent.ui.settings.AppsSettings
import me.jhot.swiftcurrent.ui.settings.BackupController
import me.jhot.swiftcurrent.ui.settings.BackupOutcome
import me.jhot.swiftcurrent.ui.settings.CalendarFilterList
import me.jhot.swiftcurrent.ui.settings.GesturesSettings
import me.jhot.swiftcurrent.ui.settings.HiddenAppsPage
import me.jhot.swiftcurrent.ui.settings.HiddenContactMethodsPage
import me.jhot.swiftcurrent.ui.settings.HomeSettings
import me.jhot.swiftcurrent.ui.settings.HomeSpacingPage
import me.jhot.swiftcurrent.ui.settings.PopupFoldersPage
import me.jhot.swiftcurrent.ui.settings.SearchHistoryExport
import me.jhot.swiftcurrent.ui.settings.SearchSettings
import me.jhot.swiftcurrent.ui.settings.SettingsEdits
import me.jhot.swiftcurrent.ui.settings.SettingsNav
import me.jhot.swiftcurrent.ui.settings.SettingsRoot
import me.jhot.swiftcurrent.ui.settings.SettingsRoute

/**
 * The one restore path (a picked document and the recovery offer both end here). After a restore the
 * in-memory usage counts are re-read from the database, so sort-by-usage follows the restored data at
 * once; the shell's data is reloaded too. Nothing is touched unless the controller accepted the text.
 */
internal suspend fun runRestore(
    deps: LauncherDependencies,
    model: ShellData,
    io: CoroutineDispatcher,
    controller: BackupController,
    read: () -> String,
    afterRestore: suspend () -> Unit = {},
): BackupOutcome {
    val outcome = controller.restore(read)
    if (outcome == BackupOutcome.Restored) {
        afterRestore()
        try {
            withContext(io) { deps.usage.hydrate() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SettingsHost", "re-reading usage after a restore failed", e)
        }
        model.reloadData()
    }
    return outcome
}

/** The notice for an outcome; [failed] is the text for a read or write failure, which differs by action. */
internal fun noticeRes(outcome: BackupOutcome, failed: Int): Int = when (outcome) {
    BackupOutcome.BackedUp -> R.string.settings_backup_done
    BackupOutcome.Restored -> R.string.settings_restore_done
    BackupOutcome.Rejected -> R.string.settings_restore_rejected
    BackupOutcome.Failed -> failed
}

/**
 * Settings wired to the launcher's data. [nav] holds the page; Back goes to the parent page and
 * from the root calls [onExit]. Every change is one [ShellData.update], which publishes the written
 * data at once, so Home, the app list and search see it without a reload. The per-app shortcuts
 * page keeps its own Back.
 */
@Composable
fun SettingsHost(
    deps: LauncherDependencies,
    model: ShellData,
    io: CoroutineDispatcher,
    nav: SettingsNav,
    onExit: () -> Unit,
    onFavorites: () -> Unit,
    onSetup: () -> Unit,
    onMessage: (ShellMessage) -> Unit,
    // The Home screen as it is now, which the spacing page draws as its preview.
    homePreview: HomeUiState = HomeUiState(clock = "", date = "", nextEvent = null, favorites = emptyList(), anyStored = false),
) {
    val route by nav.current.collectAsState()
    val data by model.data.collectAsState()
    val apps by model.namedApps.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val packageName = context.packageName
    BackHandler(enabled = route != SettingsRoute.Shortcuts) { if (!nav.back()) onExit() }

    fun edit(change: (LauncherData) -> LauncherData) {
        scope.launch { model.update(change) }
    }

    val start = rememberSettingsLauncher(deps, io, onMessage)

    // Permissions and services are changed in system settings, so they are read again whenever the
    // launcher is shown; without a lifecycle owner (tests) they are read once.
    var calendarGranted by remember { mutableStateOf(deps.calendarGranted()) }
    var shortcutsWork by remember { mutableStateOf<Boolean?>(null) }
    var lockOn by remember { mutableStateOf(deps.lockServiceOn()) }
    WhileStarted(deps) {
        // Read off the main thread, written here: Compose state is not assigned from the io dispatcher.
        // A failed read keeps what was shown rather than closing Settings.
        val read = orElse(null) {
            withContext(io) { Triple(deps.calendarGranted(), deps.lockServiceOn(), deps.shortcuts.access() == ShortcutAccess.Granted) }
        } ?: return@WhileStarted
        calendarGranted = read.first
        lockOn = read.second
        shortcutsWork = read.third
    }

    // Backup and restore. The controller does the file and store work off the main thread.
    val controller = remember(deps, io) { BackupController(deps.store, deps.usage, io) }
    var unreadableTick by remember { mutableIntStateOf(0) }
    val unreadable by produceIo<String?>(null, io, unreadableTick, route) { deps.unreadableBackup() }

    fun notice(res: Int) {
        onMessage(ShellMessage.Notice(resources.getString(res)))
    }

    fun report(outcome: BackupOutcome, failed: Int) {
        notice(noticeRes(outcome, failed))
    }

    // True when [block] ran to the end; a failure becomes a notice rather than closing Settings.
    suspend fun guardedIo(block: suspend () -> Unit): Boolean = try {
        withContext(io) { block() }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("SettingsHost", "search history work failed", e)
        false
    }

    suspend fun restoreFrom(read: () -> String, afterRestore: suspend () -> Unit = {}) {
        report(runRestore(deps, model, io, controller, read, afterRestore), R.string.settings_restore_failed)
    }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch { report(controller.export { deps.writeDocument(uri, it) }, R.string.settings_backup_failed) }
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { restoreFrom({ deps.readDocument(uri) }) }
    }
    val backupFileName = stringResource(R.string.backup_file_name)

    // Search history is kept out of backups, so it has its own file. Both the read and the write block, so both run on io.
    val exportHistory = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val saved = guardedIo { deps.writeDocument(uri, SearchHistoryExport.toJson(deps.usage.exportPicks())) }
            notice(if (saved) R.string.settings_search_history_exported else R.string.settings_search_history_export_failed)
        }
    }
    val historyFileName = stringResource(R.string.search_history_file_name)

    val newFolder = stringResource(R.string.editor_new_folder)

    Box(Modifier.fillMaxSize()) {
        when (route) {
            SettingsRoute.Root -> SettingsRoot(nav)
            SettingsRoute.Spacing -> HomeSpacingPage(
                preview = homePreview,
                gapDp = HomeSpacing.clamp(data.settings.bottomGapDp),
                onGap = { gap -> edit { it.copy(settings = it.settings.copy(bottomGapDp = HomeSpacing.clamp(gap))) } },
            )
            SettingsRoute.Home -> HomeSettings(
                gapDp = HomeSpacing.clamp(data.settings.bottomGapDp),
                onSpacing = { nav.open(SettingsRoute.Spacing) },
                calendarGranted = calendarGranted,
                onFavorites = onFavorites,
                onCalendars = { nav.open(SettingsRoute.Calendars) },
                onGrantCalendar = {
                    start(appDetailsIntent(packageName))
                },
            )
            SettingsRoute.Calendars -> {
                val calendars by produceIo(emptyList<CalendarInfo>(), io) { deps.calendar.calendars() }
                Box(Modifier.fillMaxSize().testTag("settings-calendars")) {
                    CalendarFilterList(
                        calendars = calendars,
                        enabled = data.settings.enabledCalendars,
                        onToggle = { id -> edit { CalendarFilter.apply(it, id, calendars.map { c -> c.id }) } },
                    )
                }
            }
            SettingsRoute.Apps -> AppsSettings(
                sortByUsage = data.settings.sortByUsage,
                hiddenCount = data.hiddenApps.size,
                folderCount = data.folders.size,
                onSortByUsage = { on -> edit { SettingsEdits.setSortByUsage(it, on) } },
                onHidden = { nav.open(SettingsRoute.HiddenApps) },
                onFolders = { nav.open(SettingsRoute.PopupFolders) },
            )
            SettingsRoute.HiddenApps -> HiddenAppsPage(
                apps = remember(apps) { apps.sortedBy { it.label.lowercase() } },
                hidden = data.hiddenApps,
                onChange = { component, hide -> edit { SettingsEdits.setHidden(it, component, hide) } },
            )
            SettingsRoute.PopupFolders -> PopupFoldersPage(
                folders = data.folders,
                labelOf = { it.customLabel ?: newFolder },
                onDelete = { f -> edit { SettingsEdits.deleteFolder(it, f.id) } },
            )
            SettingsRoute.Search -> SearchSettings(
                shortcutsWork = shortcutsWork,
                onShortcuts = { nav.open(SettingsRoute.Shortcuts) },
                onOpenHomeSettings = { start(Intent(Settings.ACTION_HOME_SETTINGS)) },
                appsFirst = data.settings.appsFirst,
                contactsFirst = data.settings.contactsFirst,
                learnFromPicks = data.settings.learnFromPicks,
                onAppsFirst = { on -> edit { SettingsEdits.setAppsFirst(it, on) } },
                onContactsFirst = { on -> edit { SettingsEdits.setContactsFirst(it, on) } },
                onLearnFromPicks = { on -> edit { SettingsEdits.setLearnFromPicks(it, on) } },
                onClearHistory = {
                    scope.launch {
                        val cleared = guardedIo { deps.usage.clearPicks() }
                        notice(if (cleared) R.string.settings_search_cleared else R.string.settings_search_clear_failed)
                    }
                },
                hiddenCount = data.settings.hiddenContactActions.size,
                onHiddenMethods = { nav.open(SettingsRoute.HiddenContactMethods) },
                calendarGranted = calendarGranted,
                onCalendars = { nav.open(SettingsRoute.SearchCalendars) },
                onGrantCalendar = { start(appDetailsIntent(packageName)) },
            )
            SettingsRoute.SearchCalendars -> {
                val calendars by produceIo(emptyList<CalendarInfo>(), io) { deps.calendar.calendars() }
                Box(Modifier.fillMaxSize().testTag("settings-search-calendars")) {
                    CalendarFilterList(
                        calendars = calendars,
                        enabled = data.settings.searchCalendars,
                        onToggle = { id -> edit { CalendarFilter.applyToSearch(it, id, calendars.map { c -> c.id }) } },
                    )
                }
            }
            SettingsRoute.HiddenContactMethods -> HiddenContactMethodsPage(
                items = data.settings.hiddenContactActions,
                onShow = { h -> edit { SettingsEdits.showContactAction(it, h.contact, h.action) } },
            )
            SettingsRoute.Shortcuts -> Box(Modifier.fillMaxSize().testTag("shortcuts-settings")) {
                AppShortcutsHost(deps, model, io, onBack = { nav.back() }, onMessage = onMessage)
            }
            SettingsRoute.Gestures -> GesturesSettings(
                lockServiceOn = lockOn,
                haptics = data.settings.haptics,
                onHaptics = { on -> edit { SettingsEdits.setHaptics(it, on) } },
                scrubStrength = data.settings.scrubHaptic,
                onScrubStrength = { s -> edit { SettingsEdits.setScrubHaptic(it, s) }; deps.haptics.scrub(s) },
                gestureStrength = data.settings.gestureHaptic,
                onGestureStrength = { s -> edit { SettingsEdits.setGestureHaptic(it, s) }; deps.haptics.gesture(s) },
                onOpenAccessibility = { start(accessibilitySettingsIntent()) },
            )
            SettingsRoute.Advanced -> AdvancedSettings(
                hasUnreadable = unreadable != null,
                onSetup = onSetup,
                onBackUp = { createDocument.launch(backupFileName) },
                onRestore = { openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onExportSearchHistory = { exportHistory.launch(historyFileName) },
                onRecover = {
                    val text = unreadable
                    if (text != null) {
                        scope.launch {
                            restoreFrom({ text }) {
                                withContext(io) { deps.clearUnreadableBackup() }
                                unreadableTick++
                            }
                        }
                    }
                },
            )
        }
    }
}
