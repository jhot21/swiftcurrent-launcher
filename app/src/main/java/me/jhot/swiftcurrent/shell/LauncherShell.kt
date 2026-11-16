package me.jhot.swiftcurrent.shell

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.agenda.AgendaEvent
import me.jhot.swiftcurrent.agenda.AgendaFormatter
import me.jhot.swiftcurrent.agenda.AgendaIntents
import me.jhot.swiftcurrent.agenda.CalendarAccess
import me.jhot.swiftcurrent.agenda.LocationIntentSpec
import me.jhot.swiftcurrent.agenda.rememberAgendaFormatter
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.catalog.AppSections
import me.jhot.swiftcurrent.context.DefaultMeldModes
import me.jhot.swiftcurrent.core.ConditionEval
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Favorites
import me.jhot.swiftcurrent.core.HiddenContactAction
import me.jhot.swiftcurrent.core.HomeSpacing
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Renames
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.core.orElse
import me.jhot.swiftcurrent.lock.DoubleTapDetector
import me.jhot.swiftcurrent.lock.LockResult
import me.jhot.swiftcurrent.lock.Screen
import me.jhot.swiftcurrent.lock.ScrubTapHandler
import me.jhot.swiftcurrent.lock.SetupOrigin
import me.jhot.swiftcurrent.lock.accessibilitySettingsIntent
import me.jhot.swiftcurrent.search.ContactHiding
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.RankingOptions
import me.jhot.swiftcurrent.search.SearchEngine
import me.jhot.swiftcurrent.search.SearchSource
import me.jhot.swiftcurrent.search.sources.AppSource
import me.jhot.swiftcurrent.search.sources.ContactActionFactory
import me.jhot.swiftcurrent.search.sources.ContactIntentSpec
import me.jhot.swiftcurrent.search.sources.ContactSource
import me.jhot.swiftcurrent.search.sources.EventActions
import me.jhot.swiftcurrent.search.sources.EventSource
import me.jhot.swiftcurrent.search.sources.ResourceSearchStrings
import me.jhot.swiftcurrent.search.sources.SearchStrings
import me.jhot.swiftcurrent.search.sources.ShortcutSource
import me.jhot.swiftcurrent.search.sources.UserShortcutSource
import me.jhot.swiftcurrent.shortcuts.Creator
import me.jhot.swiftcurrent.shortcuts.ShortcutAccess
import me.jhot.swiftcurrent.shortcuts.ShortcutEntry
import me.jhot.swiftcurrent.shortcuts.ShortcutFilter
import me.jhot.swiftcurrent.shortcuts.userShortcutId
import me.jhot.swiftcurrent.shortcuts.userShortcutKey
import me.jhot.swiftcurrent.shortcuts.withoutShortcut
import me.jhot.swiftcurrent.ui.agenda.AgendaSheet
import me.jhot.swiftcurrent.ui.applist.AppListScreen
import me.jhot.swiftcurrent.ui.applist.EdgeScrub
import me.jhot.swiftcurrent.ui.design.Tokens
import me.jhot.swiftcurrent.ui.editor.Candidates
import me.jhot.swiftcurrent.ui.editor.CreatorPicker
import me.jhot.swiftcurrent.ui.editor.ItemEditor
import me.jhot.swiftcurrent.ui.editor.ItemEditorState
import me.jhot.swiftcurrent.ui.editor.Mode
import me.jhot.swiftcurrent.ui.editor.ShortcutCandidate
import me.jhot.swiftcurrent.ui.editor.rememberEditorStrings
import me.jhot.swiftcurrent.ui.home.ActionSheet
import me.jhot.swiftcurrent.ui.home.ActionSheetCallbacks
import me.jhot.swiftcurrent.ui.home.FavoriteAction
import me.jhot.swiftcurrent.ui.home.HomeGestureHost
import me.jhot.swiftcurrent.ui.home.HomeGestureTargets
import me.jhot.swiftcurrent.ui.home.HomeRows
import me.jhot.swiftcurrent.ui.home.HomeScreen
import me.jhot.swiftcurrent.ui.home.HomeUiState
import me.jhot.swiftcurrent.ui.home.PopupHost
import me.jhot.swiftcurrent.ui.home.RenameSheet
import me.jhot.swiftcurrent.ui.home.ShortcutRowModel
import me.jhot.swiftcurrent.ui.home.TapAction
import me.jhot.swiftcurrent.ui.home.homeMetrics
import me.jhot.swiftcurrent.ui.home.tapAction
import me.jhot.swiftcurrent.ui.search.SearchScreen
import me.jhot.swiftcurrent.ui.settings.SettingsEdits
import me.jhot.swiftcurrent.ui.settings.SettingsNav
import me.jhot.swiftcurrent.ui.settings.SettingsRoute
import me.jhot.swiftcurrent.ui.settings.SetupChecklist
import java.time.Instant

/** How far ahead the agenda looks. */
private const val AGENDA_DAYS = 14

/**
 * The launcher: composes Home, the app list, search and settings over the session's current
 * screen. Blocking work (catalog, shortcuts, contacts, store, launches) runs on [ioDispatcher].
 * A screen lock bumps the session's reset token, which re-creates everything below the data
 * layer, so list scroll, sheets, pop-ups and typed queries are discarded, not just hidden.
 */
@Composable
fun LauncherShell(
    deps: LauncherDependencies,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    permissions: PermissionRequesterFactory = { onResult -> rememberActivityPermissionRequester(onResult) },
    state: ShellState = remember { ShellState() },
    model: ShellData = remember(deps, ioDispatcher) { ShellData(deps, ioDispatcher) },
) {
    LaunchedEffect(model) { model.run() }
    val ready by model.ready.collectAsState()
    val token by deps.session.resetToken.collectAsState()
    state.resetTokenChanged(token)
    if (!ready) {
        Box(Modifier.fillMaxSize().background(Tokens.black))
    } else {
        key(token) { ShellContent(deps, model, state, ioDispatcher, permissions) }
    }
}

/**
 * The sources search ships with; a new integration adds one here. [data] is read on every query, so
 * hidden apps, the owner's own shortcuts and the switched-off shortcuts are always current.
 */
internal fun searchSources(
    deps: LauncherDependencies,
    data: () -> LauncherData,
    apps: () -> List<AppInfo>,
    strings: SearchStrings,
    io: CoroutineDispatcher,
    agenda: AgendaFormatter,
    askPermission: (String) -> Boolean = { false },
): List<SearchSource> {
    // One predicate for both shortcut sources, so a switch hides a shortcut wherever it would show.
    val filter = ShortcutFilter { it !in data().settings.disabledShortcuts }
    return listOf(
        AppSource(apps, { data().hiddenApps }, deps.appLauncher, strings),
        ShortcutSource(deps.shortcuts, io, filter),
        UserShortcutSource(
            shortcuts = { data().userShortcuts },
            appLabel = { pkg -> apps().firstOrNull { it.packageName == pkg }?.label ?: deps.packageLabel(pkg) },
            launch = deps.userShortcuts::launch,
            filter = filter,
            // The shell publishes a new list when the installed apps or their names change.
            labelsVersion = apps,
        ),
        ContactSource(deps.contacts, deps::contactsGranted, strings, io, ContactActionFactory { launchContactAction(deps, it, askPermission) }),
        EventSource(
            deps.calendar, deps::calendarGranted, { data().settings.searchCalendars },
            { Instant.ofEpochMilli(deps.nowMs()) }, deps::zone, agenda, calendarEventActions(deps), io,
        ),
    )
}

/** Opening an event is the calendar app's job, and so is any location that is a link or a place. */
private fun calendarEventActions(deps: LauncherDependencies) = object : EventActions {
    override fun open(event: AgendaEvent): LaunchOutcome = deps.startActivity(AgendaIntents.view(event, deps.zone()))

    override fun launch(spec: LocationIntentSpec): LaunchOutcome = deps.startActivity(Intent(spec.action, Uri.parse(spec.uri)))
}

/**
 * Opens a contact's action. A connected app can guard the activity behind a permission this app lacks (Meet, Signal
 * and WhatsApp want CALL_PHONE for their call rows); it is asked for here, at the tap, and a refusal still launches so the
 * system's own denial becomes the usual "not allowed" message. Runs off the main thread: [askPermission] blocks.
 */
internal fun launchContactAction(deps: LauncherDependencies, spec: ContactIntentSpec, askPermission: (String) -> Boolean): LaunchOutcome {
    val intent = spec.toIntent()
    deps.permissionNeededToStart(intent)?.let { askPermission(it) }
    return deps.startActivity(intent)
}

private fun ContactIntentSpec.toIntent(): Intent {
    val uri = Uri.parse(uri)
    return Intent(action).apply {
        if (mimetype != null) setDataAndType(uri, mimetype) else data = uri
        packageName?.let { setPackage(it) }
    }
}

@Composable
private fun ShellContent(deps: LauncherDependencies, model: ShellData, state: ShellState, io: CoroutineDispatcher, permissions: PermissionRequesterFactory) {
    val session = deps.session
    val screen by session.screen.collectAsState()
    val overlay by state.overlay.collectAsState()
    val message by state.message.collectAsState()
    val serial by state.messageSerial.collectAsState()
    val data by model.data.collectAsState()
    // With the owner's names applied: every screen below shows, sorts and searches by them.
    val apps by model.namedApps.collectAsState()
    val scope = rememberCoroutineScope()
    val failedText = stringResource(R.string.search_launch_failed)
    val packageName = LocalContext.current.packageName

    val loadFailed by model.loadFailed.collectAsState()
    val loadFailedText = stringResource(R.string.load_failed)
    LaunchedEffect(loadFailed) { if (loadFailed) state.showMessage(ShellMessage.LaunchFailed(loadFailedText)) }

    // Overlays render above every screen, so none may survive a change of screen.
    state.screenChanged(screen)

    // First run: once per process, on Home, when setup was never seen and a step is still open.
    LaunchedEffect(model) {
        if (model.setupPrompted) return@LaunchedEffect
        model.setupPrompted = true
        if (model.data.value.settings.setupSeen) return@LaunchedEffect
        val open = orElse(false) {
            withContext(io) { SetupChecklist(deps.setupEnv).steps().any { !it.done } }
        }
        if (open && session.screen.value == Screen.Home) {
            session.openSetup(SetupOrigin.FirstRun)
        }
    }

    val appsByComponent = remember(apps) { apps.associateBy { it.component } }
    // The system's shortcut labels, read off the main thread and again whenever Home is shown, so a
    // favorite saved without a label (older data) still gets a real name instead of its key.
    val systemLabels by produceState(emptyMap<String, String>(), screen) {
        if (screen != Screen.Home) return@produceState
        value = orElse(value) {
            withContext(io) { deps.shortcuts.entries().associate { it.key to it.label } }
        }
    }
    val unknownShortcut = stringResource(R.string.label_unknown_shortcut)
    val unknownApp = stringResource(R.string.label_unknown_app)
    val newFolder = stringResource(R.string.editor_new_folder)

    // Never a raw id: a missing label falls back to a neutral word.
    // What an entry is called with no custom label: also what a rename's Reset goes back to.
    fun defaultLabelFor(entry: Entry): String = when (val t = entry.target) {
        // The system's name for the app, which a rename's Reset goes back to.
        is Target.App -> appsByComponent[t.component]?.systemLabel ?: unknownApp
        is Target.Shortcut ->
            (userShortcutId(t.key)?.let { id -> data.userShortcuts.firstOrNull { it.id == id }?.label } ?: systemLabels[t.key])
                ?: unknownShortcut
        Target.Folder -> newFolder
    }
    fun labelFor(entry: Entry): String = when (val t = entry.target) {
        // An app has one name wherever it appears (the apps here already carry it). An app that is gone has
        // only the name an entry kept for it.
        is Target.App -> appsByComponent[t.component]?.label ?: Renames.appNameOf(data, t.component) ?: entry.customLabel?.ifBlank { null } ?: unknownApp
        else -> entry.customLabel?.ifBlank { null } ?: defaultLabelFor(entry)
    }

    // Permissions are granted or revoked in system settings, so they are read again whenever the
    // launcher is shown, and the agenda is refreshed with them and then every minute.
    var contactsOk by remember { mutableStateOf(true) }
    var calendarOk by remember { mutableStateOf(false) }
    var agendaEvents by remember { mutableStateOf(emptyList<AgendaEvent>()) }
    var agendaNow by remember { mutableStateOf(Instant.ofEpochMilli(deps.nowMs())) }
    val enabledCalendars = data.settings.enabledCalendars
    WhileStarted(deps, enabledCalendars) {
        suspend fun refresh() {
            val read = try {
                withContext(io) {
                    val now = Instant.ofEpochMilli(deps.nowMs())
                    val calendar = if (deps.calendarGranted()) deps.calendar.load(now, AGENDA_DAYS, enabledCalendars) else CalendarAccess.Denied
                    Triple(deps.contactsGranted(), calendar, now)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return
            }
            contactsOk = read.first
            agendaNow = read.third
            when (val c = read.second) {
                is CalendarAccess.Granted -> { calendarOk = true; agendaEvents = c.events }
                CalendarAccess.Denied -> { calendarOk = false; agendaEvents = emptyList() }
            }
        }
        deps.minuteTicks().collect { refresh() }
    }
    val agendaFormatter = rememberAgendaFormatter()
    val agendaZone = remember(agendaNow) { deps.zone() }
    // One pass gives the whole line and the part after the title, so Home can cut only the title.
    val nextLine = remember(agendaFormatter, agendaEvents, agendaNow, agendaZone, calendarOk) {
        if (calendarOk) agendaFormatter.nextLineParts(agendaEvents, agendaNow, agendaZone) else null
    }
    val nextEvent = nextLine?.text
    val nextEventTail = nextLine?.tail.orEmpty()

    // One launch at a time: a second tap while the first is in flight would launch (and count) twice.
    var launching by remember { mutableStateOf(false) }
    fun run(onLaunched: () -> Unit = {}, block: () -> LaunchOutcome) {
        if (launching) return
        launching = true
        scope.launch {
            try {
                when (val out = withContext(io) { block() }) {
                    LaunchOutcome.Launched -> onLaunched()
                    is LaunchOutcome.Failed -> state.showMessage(ShellMessage.LaunchFailed(out.message))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Binder calls can throw (SecurityException, DeadObjectException); show it, never crash.
                state.showMessage(ShellMessage.LaunchFailed(failedText))
            } finally {
                launching = false
            }
        }
    }

    fun launchComponent(component: String, onLaunched: () -> Unit = {}) = run(onLaunched) {
        deps.usage.record(component, learn = model.data.value.settings.learnFromPicks)
        deps.appLauncher.launch(component)
    }

    fun start(intent: Intent) = run { deps.startActivity(intent) }

    // A key is "user/<id>" for a shortcut the owner made, otherwise "package/id" from the system.
    fun launchShortcut(key: String): LaunchOutcome {
        val id = userShortcutId(key) ?: return deps.shortcuts.launch(key)
        val shortcut = model.data.value.userShortcuts.firstOrNull { it.id == id }
            ?: return LaunchOutcome.Failed(failedText)
        return deps.userShortcuts.launch(shortcut)
    }

    val gestureTargets = remember { HomeGestureTargets() }

    // The card opens beside the row it came from; an entry that is not a Home row (a folder inside a pop-up) has no anchor.
    // One short tick when a gesture opens something, at the owner's chosen strength.
    fun gestureTick() {
        if (data.settings.haptics) deps.haptics.gesture(data.settings.gestureHaptic)
    }

    fun openPopup(entry: Entry) = state.openPopup(entry, gestureTargets.boundsOf(entry))

    // The menu of an app seen anywhere (app list, search, inside another sheet): its own pop-up if it is a favorite, else its shortcuts.
    fun openAppMenu(entry: Entry, bounds: Rect?) {
        val component = (entry.target as? Target.App)?.component ?: return
        state.openPopup(Favorites.menuEntry(data, component), bounds)
    }

    // Launching from a menu opened over Search ends the search; anywhere else this does nothing.
    fun leaveSearch() {
        if (session.screen.value == Screen.Search) session.navigate(Screen.Home)
    }

    fun activate(entry: Entry) {
        when (val action = tapAction(entry)) {
            // A menu opened over Search ends the search, but only once the launch worked: a failed one keeps the query.
            is TapAction.Launch -> { state.dismissOverlay(); launchComponent(action.component, ::leaveSearch) }
            is TapAction.LaunchShortcut -> { state.dismissOverlay(); run(::leaveSearch) { launchShortcut(action.key) } }
            TapAction.OpenPopup -> openPopup(entry)
        }
    }

    // The editor reloads the store once when it opens, so its first edit already writes the current
    // list; opening only has to remember what is being edited.
    fun openEditor(target: EditorTarget) {
        state.openEditor(target)
        session.navigate(Screen.Editor)
    }

    fun appDetails(pkg: String = packageName) = appDetailsIntent(pkg)

    // The list keeps its scroll across a trip to Settings; a new visit from Home starts at the top.
    val listState = remember(state.listVisit) { LazyListState() }

    val scrubTaps = remember {
        ScrubTapHandler(
            detector = DoubleTapDetector(ViewConfiguration.getDoubleTapTimeout().toLong()),
            lock = deps.lock,
            session = session,
            nowMs = deps::nowMs,
            onLockResult = {
                when (it) {
                    LockResult.ServiceDisabled -> state.showMessage(ShellMessage.LockNeedsService)
                    LockResult.Failed -> state.showMessage(ShellMessage.LockFailed)
                    LockResult.Locked -> Unit
                }
            },
        )
    }

    // Keyed under the reset token with the rest of ShellContent, so a lock drops a scrub in progress.
    val edgeScrub = remember { EdgeScrub() }
    val edgeSwipe = remember { EdgeSwipe(edgeScrub, session) }

    val clockFlow = remember(deps) { deps.clockText() }
    val dateFlow = remember(deps) { deps.dateText() }
    val clock = lifecycleText(clockFlow)
    val date = lifecycleText(dateFlow)

    val ctx by deps.contextState.collectAsState()
    val rows = remember(data.favorites, data.userShortcuts, appsByComponent, systemLabels, ctx) {
        HomeRows.build(
            data.favorites.filter { ConditionEval.shown(it, ctx) }, installed = { it in appsByComponent }, labelFor = ::labelFor,
            archived = { appsByComponent[it]?.archived == true },
        )
    }

    Box(Modifier.fillMaxSize().background(Tokens.black)) {
        // Home stays composed under the other screens so a closing list slides off it, but it is
        // hidden from accessibility and its gestures are off while anything covers it.
        Box(if (screen == Screen.Home) Modifier else Modifier.clearAndSetSemantics { }) {
            HomeGestureHost(
                // The tick marks the drag becoming one that opens search or the shade; they open on the lift.
                onArmed = { gestureTick() },
                onSearch = { session.navigate(Screen.Search) },
                onAppList = { state.nextListVisit(); session.navigate(Screen.AppList) },
                onItemPopup = { entry -> gestureTick(); openPopup(entry) },
                targets = gestureTargets,
                // The tick is the moment the long press registers, before the sheet has drawn.
                onItemLongPress = { entry -> gestureTick(); state.openActionSheet(entry) },
                enabled = ShellState.gesturesEnabled(screen, overlay),
                edge = edgeSwipe,
                // Best effort: the call can do nothing, and the tick has already played when the pull armed.
                onShade = { deps.expandNotifications() },
            ) {
                HomeScreen(
                    state = HomeUiState(clock = clock, date = date, nextEvent = nextEvent, favorites = rows, anyStored = data.favorites.isNotEmpty(), nextEventTail = nextEventTail),
                    onClock = { start(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
                    // Without calendar access there is no agenda to show, so the tap offers to grant it.
                    onAgenda = { if (calendarOk) state.openAgenda() else state.showMessage(ShellMessage.CalendarNeedsAccess) },
                    onItem = ::activate,
                    onAddFavorites = { openEditor(EditorTarget.Favorites) },
                    bottomGapDp = HomeSpacing.clamp(data.settings.bottomGapDp).toFloat(),
                )
            }
        }
        // Home swallows Back: a launcher has nowhere to go back to.
        BackHandler(enabled = screen == Screen.Home && overlay == Overlay.None) {}

        when (screen) {
            Screen.Home -> Unit
            Screen.AppList -> Layer {
                val sections = remember(apps, data.hiddenApps, data.settings.sortByUsage) {
                    AppSections.build(apps, data.hiddenApps, deps.usage.counts(), data.settings.sortByUsage)
                }
                AppListScreen(
                    sections = sections,
                    onLaunch = { launchComponent(it.component); session.navigate(Screen.Home) },
                    onSearch = { session.navigate(Screen.Search) },
                    onSettings = { state.openSettings(SettingsRoute.Root); session.navigate(Screen.Settings) },
                    onScrubTap = scrubTaps::onScrubTap,
                    // Navigating away removes the screen from composition, so a list slid off to the right does not linger over Home.
                    onClose = { session.navigate(Screen.Home) },
                    // The sheet for this app: the same one a favorite has, with add/remove favorite in place of editing them all.
                    onLongPress = { app ->
                        gestureTick()
                        state.openActionSheet(Entry(app.component, Target.App(app.component)), fromList = true)
                    },
                    // A swipe right shows the app's menu, as in search, lined up with the row.
                    onSwipeRight = { app, bounds ->
                        gestureTick()
                        openAppMenu(Entry(app.component, Target.App(app.component)), bounds)
                    },
                    state = listState,
                    haptics = data.settings.haptics,
                    onTick = { deps.haptics.scrub(data.settings.scrubHaptic) },
                    edgeScrub = edgeScrub,
                )
            }
            Screen.Search -> Layer(enter = true) {
                SearchHost(
                    deps = deps, model = model, io = io, permissions = permissions,
                    // The app's menu over the search: its own pop-up if it is a favorite, else its shortcuts.
                    onAppMenu = { component, bounds ->
                        gestureTick()
                        openAppMenu(Entry(component, Target.App(component)), bounds)
                    },
                    onClose = { session.navigate(Screen.Home) },
                    onOpenSettings = { start(appDetails()) },
                    contactsDenied = !contactsOk,
                )
            }
            Screen.Editor -> {
                val target by state.editorTarget.collectAsState()
                val t = target
                if (t == null) {
                    // Nothing to edit (the state was lost): leave rather than show a layer that eats every touch.
                    LaunchedEffect(Unit) { session.navigate(Screen.Home) }
                } else Layer {
                    EditorHost(
                        deps = deps, model = model, io = io, target = t, apps = apps, permissions = permissions,
                        labelFor = ::labelFor,
                        onMessage = state::showMessage,
                        onSelectHiddenApps = {
                            state.closeEditor()
                            state.openSettings(SettingsRoute.HiddenApps)
                            session.navigate(Screen.Settings)
                        },
                        onClose = { state.closeEditor(); session.navigate(Screen.Home) },
                    )
                }
            }
            Screen.Setup -> Layer {
                SetupHost(
                    deps = deps, io = io, permissions = permissions, onMessage = state::showMessage,
                    onFinish = {
                        if (session.setupOrigin == SetupOrigin.FirstRun) {
                            // Done and Skip both mean the owner has seen it, so it is not offered again.
                            scope.launch { model.update { it.copy(settings = it.settings.copy(setupSeen = true)) } }
                            session.navigate(Screen.Home)
                        } else {
                            state.openSettings(SettingsRoute.Advanced)
                            session.navigate(Screen.Settings)
                        }
                    },
                )
            }
            Screen.Settings -> Layer {
                val nav = remember { state.settingsNav() }
                SettingsHost(
                    deps = deps, model = model, io = io, nav = nav,
                    onExit = { session.navigate(Screen.AppList) },
                    onFavorites = { openEditor(EditorTarget.Favorites) },
                    onSetup = { state.openSettings(SettingsRoute.Advanced); session.openSetup(SetupOrigin.Settings) },
                    onMessage = state::showMessage,
                    homePreview = HomeUiState(clock = clock, date = date, nextEvent = nextEvent, favorites = rows, anyStored = data.favorites.isNotEmpty(), nextEventTail = nextEventTail),
                )
            }
        }

        when (val o = overlay) {
            Overlay.None -> Unit
            is Overlay.Popup -> {
                // A stack shows every sheet, the one opened last on top; Back and the scrim close only that one.
                generateSequence(o) { it.below }.toList().asReversed().forEach { frame ->
                    key(frame) {
                        val shortcutEntries by produceIo(emptyList<ShortcutEntry>(), io, frame) { deps.shortcuts.entries() }
                        PopupHost(
                            entry = frame.entry,
                            anchor = frame.anchor,
                            onDismiss = state::dismissTop,
                            ownShortcuts = { pkg ->
                                shortcutEntries.filter { it.packageName == pkg }.map { ShortcutRowModel(it.key, it.label) }
                            },
                            labelOf = ::labelFor,
                            // A folder opens over this sheet, lined up with its line; anything else launches.
                            onEntry = { e, bounds -> if (e.target is Target.Folder) state.openPopup(e, bounds) else activate(e) },
                            // An app's own menu over this one: its pop-up if it is a favorite, else its shortcuts.
                            onEntrySwipe = { e, bounds -> gestureTick(); openAppMenu(e, bounds) },
                            onShortcut = { s -> state.dismissOverlay(); run(::leaveSearch) { launchShortcut(s.id) } },
                            // A favorite and any app have a pop-up to edit (an app that is no favorite keeps it in appPopups);
                            // a folder opened inside another pop-up does not.
                            onEdit = if (frame.entry.target is Target.App || data.favorites.any { it.id == frame.entry.id }) {
                                { openEditor(EditorTarget.Popup(frame.entry.id)) }
                            } else null,
                        )
                    }
                }
            }
            Overlay.Agenda -> {
                val days = remember(agendaFormatter, agendaEvents, agendaNow, agendaZone) {
                    agendaFormatter.days(agendaEvents, agendaNow, agendaZone)
                }
                AgendaSheet(
                    days = days,
                    onNewEvent = { date -> state.dismissOverlay(); start(AgendaIntents.newEvent(date, agendaZone)) },
                    onDismiss = state::dismissOverlay,
                    now = agendaNow,
                    zone = agendaZone,
                    onEvent = { e -> state.dismissOverlay(); start(AgendaIntents.view(e, agendaZone)) },
                )
            }
            is Overlay.Rename -> {
                val default = defaultLabelFor(o.entry)
                // The stored label, not the snapshot the overlay was opened with.
                val appComponent = (o.entry.target as? Target.App)?.component
                val current = (appComponent?.let { Renames.appNameOf(data, it) } ?: Renames.customLabelOf(data, o.entry.id)) ?: default
                fun rename(text: String) {
                    state.dismissOverlay()
                    scope.launch {
                        model.update {
                            // An app is named once, whether or not it is a favorite; shortcuts and folders per entry.
                            if (appComponent != null) Renames.setAppName(it, appComponent, text)
                            else Renames.apply(it, o.entry.id, text, isFolder = o.entry.target is Target.Folder)
                        }
                    }
                }
                // Only a top-level favorite can carry conditions; an entry inside a pop-up, or an app that is no favorite, cannot.
                val favoriteId = o.entry.id.takeIf { id -> data.favorites.any { it.id == id } }
                val askPermission = rememberConditionPermission(deps, permissions, state::showMessage)
                RenameSheet(
                    currentLabel = current,
                    defaultLabel = default,
                    // Naming it what it is already called is a reset, so a language change still follows.
                    onSave = { rename(if (Renames.sanitize(it) == default) "" else it) },
                    onReset = { rename("") },
                    onDismiss = state::dismissOverlay,
                    conditions = favoriteId?.let { id -> data.favorites.first { it.id == id }.conditions },
                    context = ctx,
                    onConditionsChange = { list ->
                        state.dismissOverlay()
                        scope.launch {
                            // The favorite only: a folder's library entry never carries conditions.
                            model.update { d -> d.copy(favorites = d.favorites.map { if (it.id == favoriteId) it.copy(conditions = list) else it }) }
                        }
                    },
                    loadPairedDevices = { deps.bluetooth.paired() },
                    loadModes = { deps.meldModes.modes() },
                    requestPermission = askPermission,
                    io = io,
                )
            }
            is Overlay.ActionSheet -> {
                val component = (o.entry.target as? Target.App)?.component
                // From the list the entry is a stand-in for the app; whether it is a favorite only matters for add/remove.
                val favorite = if (o.fromList && component != null) Favorites.entryFor(data, component) else null
                val shown = o.entry
                val shortcutKey = (o.entry.target as? Target.Shortcut)?.key
                val userId = shortcutKey?.let(::userShortcutId)
                // Only a shortcut this launcher pinned is the owner's to delete; the index says which ones are.
                val pinned by produceIo(false, io, o.entry) {
                    shortcutKey != null && userId == null && deps.shortcuts.entries().any { it.key == shortcutKey && it.pinned }
                }
                ActionSheet(
                    entry = o.entry,
                    onDismiss = state::dismissOverlay,
                    label = labelFor(shown),
                    callbacks = ActionSheetCallbacks(
                        showEditFavorites = !o.fromList,
                        canRename = true,
                        favorite = if (o.fromList && component != null) {
                            FavoriteAction(isFavorite = favorite != null) {
                                state.dismissOverlay()
                                scope.launch {
                                    model.update { if (favorite == null) Favorites.add(it, component) else Favorites.remove(it, component) }
                                }
                            }
                        } else null,
                        onEditFavorites = { openEditor(EditorTarget.Favorites) },
                        onRename = { state.openRename(shown) },
                        onDelete = when {
                            shortcutKey == null -> null
                            userId != null -> (
                                {
                                    state.dismissOverlay()
                                    scope.launch {
                                        deps.userShortcuts.delete(userId, model::update)
                                    }
                                }
                                )
                            pinned -> (
                                {
                                    state.dismissOverlay()
                                    scope.launch {
                                        when (val r = withContext(io) { deps.shortcuts.unpin(shortcutKey) }) {
                                            LaunchOutcome.Launched -> {
                                                model.update { it.withoutShortcut(shortcutKey) }
                                            }
                                            is LaunchOutcome.Failed -> state.showMessage(ShellMessage.Notice(r.message))
                                        }
                                    }
                                }
                                )
                            else -> null
                        },
                        onAppInfo = {
                            state.dismissOverlay()
                            if (component != null) start(appDetails(component.substringBefore('/')))
                        },
                        onHide = {
                            state.dismissOverlay()
                            if (component != null) {
                                scope.launch {
                                    model.update { it.copy(hiddenApps = it.hiddenApps + component) }
                                }
                            }
                        },
                    ),
                )
            }
        }

        message?.let { m ->
            ShellMessageBar(
                message = m,
                serial = serial,
                onAction = {
                    if (m == ShellMessage.LockNeedsService) start(accessibilitySettingsIntent())
                    if (m == ShellMessage.CalendarNeedsAccess) start(appDetails())
                    state.dismissMessage()
                },
                onDismiss = state::dismissMessage,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(Tokens.pageGutter),
            )
        }
    }
}

/**
 * A full-screen layer that takes every touch, so nothing under it (Home's rows) can be tapped through it.
 * With [enter] it eases in (fade and a short rise) instead of appearing in one frame.
 */
@Composable
private fun Layer(enter: Boolean = false, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(!enter) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(LAYER_ENTER_MS), label = "layer-enter")
    val rise = with(LocalDensity.current) { LAYER_RISE.toPx() }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = progress; translationY = (1f - progress) * rise }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        content()
    }
}

private const val LAYER_ENTER_MS = 180
private val LAYER_RISE = 32.dp

@Composable
private fun SearchHost(
    deps: LauncherDependencies,
    model: ShellData,
    io: CoroutineDispatcher,
    permissions: PermissionRequesterFactory,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    contactsDenied: Boolean,
    onAppMenu: (component: String, bounds: Rect) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val askPermission = rememberBlockingPermission(permissions)
    val agenda = rememberAgendaFormatter()
    val sources = remember(deps, io, resources, askPermission, agenda) {
        searchSources(deps, { model.data.value }, { model.namedApps.value }, ResourceSearchStrings(resources), io, agenda, askPermission)
    }
    // Taken once each time search opens, so no keystroke waits on a read and a pick made now
    // reorders the next search, not this one. Reading the in-memory picks is no I/O.
    val settings = remember(deps) { model.data.value.settings }
    val options = remember(deps) {
        RankingOptions(
            settings.appsFirst,
            settings.contactsFirst,
            if (settings.learnFromPicks) deps.usage.picks().at(deps.nowMs()) else null,
        )
    }
    // Taken with the options and for the same reason: a pick made now shows in the next open's list, not this one's.
    val recentIds = remember(deps) { if (settings.learnFromPicks) deps.usage.recents() else emptyList() }
    val recentOwnerIds = remember(deps) { if (settings.learnFromPicks) deps.usage.recentOwners() else emptyMap() }
    val context = LocalContext.current
    val engine = remember(sources, options, recentIds) {
        SearchEngine(
            sources, options = { options }, recents = { recentIds }, recentOwners = { recentOwnerIds }, defaultApps = { systemDefaultApps(context) },
            hidden = { model.data.value.settings.hiddenContactActions.mapTo(HashSet()) { ContactHiding.key(it.contact, it.action) } },
            compute = Dispatchers.Default,
        )
    }
    val shortcutSource = remember(sources) { sources.filterIsInstance<ShortcutSource>().single() }
    val eventSource = remember(sources) { sources.filterIsInstance<EventSource>().single() }
    // Read off the main thread. Until the first answer the banner is not shown, which is the common case.
    var access by remember { mutableStateOf(ShortcutAccess.Granted as ShortcutAccess) }

    // Search opening re-reads both the banner state and the shortcut index it then searches from.
    fun reload() {
        scope.launch {
            access = withContext(io) { deps.shortcuts.access() }
            try {
                shortcutSource.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("SearchHost", "shortcut index refresh failed", e)
            }
            try {
                eventSource.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("SearchHost", "event refresh failed", e)
            }
        }
    }
    LaunchedEffect(deps) {
        try {
            deps.shortcuts.changes.collect { access = withContext(io) { deps.shortcuts.access() } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SearchHost", "shortcut change updates stopped", e)
        }
    }
    LaunchedEffect(shortcutSource) { shortcutSource.followChanges() }
    LaunchedEffect(eventSource) { eventSource.followChanges() }
    SearchScreen(
        engine = engine,
        access = access,
        onClose = onClose,
        contactsDenied = contactsDenied,
        refresh = ::reload,
        dispatcher = io,
        onOpenSettings = onOpenSettings,
        onResultMenu = { r, bounds -> if (r.id.startsWith("app:")) onAppMenu(r.id.removePrefix("app:"), bounds) },
        onHideAction = { owner, action ->
            // The label and name are kept as they read now: a hidden way is in no result to read them from later.
            val hide = HiddenContactAction(owner.id, action.recentId ?: return@SearchScreen, owner.label, action.label)
            model.update { SettingsEdits.hideContactAction(it, hide) }
        },
        onPicked = { r, action, q ->
            when {
                // Ranking learns the row's owner; the action is kept beside it so recents can offer it again.
                // A row that has an owner is itself the action's identity when the action names none (a shortcut
                // tapped in recents), or recents would swap it for its owner.
                settings.learnFromPicks ->
                    deps.usage.recordPick(r.ownerId ?: r.id, r.kind, q, action.recentId ?: r.id.takeIf { r.ownerId != null })
                // Still counted for the app list's order, without being kept as a pick.
                r.kind == Kind.App -> deps.usage.record(r.id.removePrefix("app:"), learn = false)
            }
        },
    )
}

/**
 * Hosts the shared editor over favorites or one favorite's pop-up. Edits are written as they are
 * made (through [ShellData.update], in order); closing returns Home, which reloads the store.
 */
@Composable
private fun EditorHost(
    deps: LauncherDependencies,
    model: ShellData,
    io: CoroutineDispatcher,
    target: EditorTarget,
    apps: List<AppInfo>,
    permissions: PermissionRequesterFactory,
    labelFor: (Entry) -> String,
    onMessage: (ShellMessage) -> Unit,
    onSelectHiddenApps: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val shortcuts by produceState<List<ShortcutEntry>?>(null) {
        value = orElse(emptyList()) {
            withContext(io) { deps.shortcuts.entries() }
        }
    }
    val loaded = shortcuts
    val strings = rememberEditorStrings()
    // What the shell handed down can predate edits made before an activity recreation. Reload the
    // store once on entry and build from the result, so the first edit cannot write the old list back.
    var restored by remember(target) { mutableStateOf<LauncherData?>(null) }
    val unreadable = stringResource(R.string.editor_load_failed)
    LaunchedEffect(target) {
        // One retry; if the store still cannot be read the editor stays shut, since building it on the
        // data Home already had would let its first edit overwrite anything saved since.
        if (model.reloadDataStrictly() || model.reloadDataStrictly()) {
            restored = model.data.value
        } else {
            onMessage(ShellMessage.Notice(unreadable))
            onClose()
        }
    }
    // Read at call time: the editor state is built once, but dimming must follow the live context.
    val ctx by deps.contextState.collectAsState()
    val data = restored
    if (loaded == null || data == null) {
        // The shortcuts and the store are read off the main thread; nothing is editable until both are in.
        Box(Modifier.fillMaxSize().background(Tokens.surface))
        return
    }
    // A user shortcut whose app is gone can only be named by the package manager.
    val goneShortcutLabels by rememberPackageLabels(
        deps, io,
        data.userShortcuts.map { it.appPackage }.filter { p -> apps.none { it.packageName == p } },
    )
    // The shortcuts the owner made; one made while the editor is open is added to the live state instead.
    val userCandidates = remember(data.userShortcuts, apps, goneShortcutLabels) {
        data.userShortcuts.filter { userShortcutKey(it.id) !in data.settings.disabledShortcuts }
            .mapNotNull { u ->
                val app = apps.firstOrNull { it.packageName == u.appPackage }?.label ?: goneShortcutLabels[u.appPackage]
                    ?: return@mapNotNull null
                ShortcutCandidate(userShortcutKey(u.id), u.label, app)
            }
    }
    // A favorite by its id, else an installed app by its component (its pop-up is kept in appPopups).
    val popupEntry = (target as? EditorTarget.Popup)?.let { t ->
        data.favorites.firstOrNull { it.id == t.entryId }
            ?: apps.firstOrNull { it.component == t.entryId }?.let { Favorites.menuEntry(data, it.component) }
    }
    val popupIsFavorite = popupEntry != null && data.favorites.any { it.id == popupEntry.id }
    if (target is EditorTarget.Popup && popupEntry == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val density = LocalDensity.current
    val insets = WindowInsets.systemBars
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val capacity = homeMetrics(
            heightDp = maxHeight.value,
            topInsetDp = with(density) { insets.getTop(density).toDp().value },
            bottomInsetDp = with(density) { insets.getBottom(density).toDp().value },
            fontScale = density.fontScale,
            bottomGapDp = HomeSpacing.clamp(data.settings.bottomGapDp).toFloat(),
        ).let { me.jhot.swiftcurrent.ui.home.FavoritesCapacity.maxRows(it) }

        // Built once from the data reloaded on entry and kept: a handled configuration change must
        // not rebuild it from older data.
        val state = remember(target, loaded, data) {
            val installed = apps.map { it.component }.toSet()
            val counts = deps.usage.counts()
            val visible = apps.filter { it.component !in data.hiddenApps }
            // The library entry is the source of truth for a folder's pop-up; a favorite is its mirror.
            val library = data.folders.associateBy { it.id }

            // Conditions belong to the favorite, never the library entry, so the mirror keeps the favorite's own.
            fun resolved(e: Entry) = if (e.target == Target.Folder) library[e.id]?.copy(conditions = e.conditions) ?: e else e
            val favorites = data.favorites.map(::resolved)
            // A candidate never carries conditions: picked into a pop-up they would ride along as inert data.
            val folders = (
                data.folders + favorites.filter { it.target == Target.Folder && it.id !in library }
                    .map { it.copy(conditions = emptyList()) }
                )
                .filter { it.id != popupEntry?.id }
            val candidates = Candidates(
                suggestions = visible.filter { (counts[it.component] ?: 0) > 0 }
                    .sortedByDescending { counts[it.component] ?: 0 }.take(4),
                apps = AppSections.build(apps, data.hiddenApps, emptyMap(), sortByUsage = false),
                shortcuts = loaded.filter { it.key !in data.settings.disabledShortcuts }
                    .map { ShortcutCandidate(it.key, it.label, it.appLabel) } + userCandidates,
                folders = folders,
            )
            val missing = { e: Entry -> (e.target as? Target.App)?.component?.let { it !in installed } ?: false }

            // Undispatched so writes enter the store lock in the order the edits were made.
            fun save(transform: (LauncherData) -> LauncherData) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) { model.update(transform) }
            }

            // Replaces by id, appends when new; the library only ever grows here, a folder is never deleted.
            fun upsert(into: List<Entry>, e0: Entry): List<Entry> {
                val e = e0.copy(conditions = emptyList())
                return if (into.any { it.id == e.id }) into.map { if (it.id == e.id) e else it } else into + e
            }
            var editor: ItemEditorState? = null
            if (popupEntry == null) {
                editor = ItemEditorState(
                    selected = favorites, candidates = candidates, capacity = capacity, strings = strings,
                    isMissing = missing, appNames = data.appNames,
                    isInactive = { !ConditionEval.shown(it, ctx) },
                    onChange = { rows ->
                        val selected = rows.filterIsInstance<PopupRow.Item>().map { it.entry }
                        // The whole library goes with every write, so a folder made or unticked here is kept.
                        val all = editor?.folders.orEmpty()
                        save { d -> d.copy(favorites = selected, folders = all.fold(d.folders) { acc, f -> upsert(acc, f) }) }
                    },
                )
            } else {
                val pkg = (popupEntry.target as? Target.App)?.component?.substringBefore('/')
                val self = resolved(popupEntry)
                // No stored pop-up yet: the default holds the app's own shortcuts.
                val stored = self.popup?.rows ?: listOfNotNull(pkg?.let { PopupRow.OwnShortcuts(it) })
                editor = ItemEditorState(
                    selected = stored.filterIsInstance<PopupRow.Item>().map { it.entry },
                    candidates = candidates, capacity = null, strings = strings,
                    ownShortcutsApp = pkg,
                    ownShortcutsIndex = stored.indexOfFirst { it is PopupRow.OwnShortcuts }.takeIf { it >= 0 },
                    isMissing = missing, appNames = data.appNames,
                    onChange = { rows ->
                        val edited = self.copy(popup = Popup(rows))
                        val libs = editor?.folders.orEmpty()
                        save { d ->
                            if (!popupIsFavorite) {
                                // No favorite entry to hold it: the app's pop-up lives in its own map.
                                return@save d.copy(
                                    appPopups = d.appPopups + (edited.id to Popup(rows)),
                                    folders = libs.fold(d.folders) { acc, f -> upsert(acc, f) },
                                )
                            }
                            d.copy(
                                favorites = d.favorites.map { if (it.id == edited.id) edited else it },
                                // Only folders live in the library; an app's pop-up stays on its favorite.
                                folders = (libs.fold(d.folders) { acc, f -> upsert(acc, f) }).let { l ->
                                    if (edited.target == Target.Folder) upsert(l, edited) else l
                                },
                            )
                        }
                    },
                )
            }
            editor
        }
        var picking by remember { mutableStateOf(false) }
        var pickable by remember { mutableStateOf<List<Creator>?>(null) }
        val create = rememberShortcutCreator(deps, model, io, onMessage) { made ->
            val app = apps.firstOrNull { it.packageName == made.appPackage }?.label
                ?: withContext(io) { deps.packageLabel(made.appPackage) } ?: made.appPackage
            state.addShortcutCandidate(ShortcutCandidate(userShortcutKey(made.id), made.label, app))
        }
        LaunchedEffect(picking) {
            if (picking && pickable == null) {
                pickable = orElse(emptyList()) {
                    withContext(io) { deps.creators.creators() }
                }
            }
        }
        // Held by id (savable): the row is looked up in the live state, which is rebuilt after a recreation.
        var renameId by rememberSaveable { mutableStateOf<String?>(null) }
        val askPermission = rememberConditionPermission(deps, permissions, onMessage)
        ItemEditor(
            state = state,
            mode = if (popupEntry == null) Mode.Favorites else Mode.Popup,
            onDone = onClose,
            title = if (popupEntry == null) strings.titleFavorites else strings.titlePopupFor(labelFor(popupEntry)),
            ownShortcutsSubtitle = { pkg ->
                loaded.filter { it.packageName == pkg }.joinToString(", ") { it.label }.ifEmpty { null }
            },
            onAddShortcut = { picking = true },
            // A new folder asks for its name at once; dismissing keeps the default.
            onCreateFolder = { renameId = it.id },
            onRename = { renameId = it.id },
            onSelectHiddenApps = onSelectHiddenApps,
        )
        renameId?.let { id ->
            val entry = state.entries.firstOrNull { it.id == id }
            if (entry == null) {
                // The reloaded store no longer has the row: close rather than ask to name nothing.
                LaunchedEffect(id) { renameId = null }
            } else {
                val default = state.defaultLabelOf(entry)
                fun rename(text: String) {
                    renameId = null
                    state.applyRename(id, text, entry.target is Target.Folder)
                    // Undispatched so it enters the store lock in order with the edits around it.
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        model.update { Renames.apply(it, id, text, within = popupEntry?.id, isFolder = entry.target is Target.Folder) }
                    }
                }
                RenameSheet(
                    currentLabel = state.labelOf(entry),
                    defaultLabel = default,
                    onSave = { rename(if (Renames.sanitize(it) == default) "" else it) },
                    onReset = { rename("") },
                    onDismiss = { renameId = null },
                    // Only the favorites list holds top-level favorites; a pop-up's rows cannot carry conditions.
                    conditions = entry.conditions.takeIf { popupEntry == null },
                    context = ctx,
                    onConditionsChange = { list ->
                        renameId = null
                        // The editor writes whole rows, so it must hold the new conditions before its next write.
                        state.applyConditions(id, list)
                        // Undispatched so it enters the store lock in order with the edits around it.
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            // The favorite only: a folder's library entry never carries conditions.
                            model.update { d -> d.copy(favorites = d.favorites.map { if (it.id == id) it.copy(conditions = list) else it }) }
                        }
                    },
                    loadPairedDevices = { deps.bluetooth.paired() },
                    loadModes = { deps.meldModes.modes() },
                    requestPermission = askPermission,
                    io = io,
                )
            }
        }
        if (picking) {
            CreatorPicker(
                creators = pickable,
                onPick = { picking = false; create(it) },
                onDismiss = { picking = false },
            )
        }
    }
}

/**
 * What the edit sheet calls before offering a condition kind: answers true at once when the permission
 * is already held, else asks the system. A grant re-reads the context, so the new kind's state is known
 * before the favorite is saved; a denial says why nothing was added.
 */
@Composable
private fun rememberConditionPermission(
    deps: LauncherDependencies,
    permissions: PermissionRequesterFactory,
    onMessage: (ShellMessage) -> Unit,
): (String, (Boolean) -> Unit) -> Unit {
    val pending = remember { mutableMapOf<String, (Boolean) -> Unit>() }
    val bluetoothDenied = stringResource(R.string.conditions_bluetooth_denied)
    val meldDenied = stringResource(R.string.conditions_meld_denied)
    val requester = permissions { permission, granted ->
        val answer = pending.remove(permission)
        if (answer != null) {
            if (granted) {
                deps.refreshContext()
            } else {
                onMessage(ShellMessage.Notice(if (permission == DefaultMeldModes.PERMISSION) meldDenied else bluetoothDenied))
            }
            answer(granted)
        }
    }
    return remember(requester) {
        { permission, answer ->
            if (deps.permissionGranted(permission)) {
                // Meld announces no new, renamed or deleted inactive mode, so the known set may be stale.
                deps.refreshContext()
                answer(true)
            } else {
                pending[permission] = answer
                requester.request(permission)
            }
        }
    }
}

/**
 * Asks for a permission and waits for the answer, for a caller that is already off the main thread and has to know
 * before it goes on (a launch that fails without it). The dialog is shown from the main thread; with no answer in
 * [ANSWER_WAIT_MS] (the screen went away) the wait ends as a refusal.
 */
@Composable
private fun rememberBlockingPermission(permissions: PermissionRequesterFactory): (String) -> Boolean {
    val waiting = remember { java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<Boolean>>() }
    val requester = permissions { permission, granted -> waiting.remove(permission)?.complete(granted) }
    return remember(requester) {
        { permission ->
            val answer = java.util.concurrent.CompletableFuture<Boolean>()
            waiting[permission] = answer
            android.os.Handler(android.os.Looper.getMainLooper()).post { requester.request(permission) }
            try {
                answer.get(ANSWER_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                waiting.remove(permission)
                false
            }
        }
    }
}

private const val ANSWER_WAIT_MS = 120_000L

/** Latest value of a text flow, collected only while the activity is at least started. */
@Composable
private fun lifecycleText(flow: Flow<String>): String {
    var text by remember { mutableStateOf("") }
    WhileStarted(flow) { flow.collect { text = it } }
    return text
}
