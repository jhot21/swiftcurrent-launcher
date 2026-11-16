package me.jhot.swiftcurrent.shell

import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import me.jhot.swiftcurrent.agenda.AgendaEvent
import me.jhot.swiftcurrent.agenda.AgendaMapper
import me.jhot.swiftcurrent.agenda.CalendarAccess
import me.jhot.swiftcurrent.agenda.CalendarInfo
import me.jhot.swiftcurrent.agenda.CalendarRepository
import me.jhot.swiftcurrent.catalog.AppCatalog
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.catalog.RoomUsageStore
import me.jhot.swiftcurrent.context.MeldModes
import me.jhot.swiftcurrent.core.ContextState
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.LauncherStore
import me.jhot.swiftcurrent.core.LegacyCounts
import me.jhot.swiftcurrent.core.db.SwiftcurrentDatabase
import me.jhot.swiftcurrent.lock.LauncherSession
import me.jhot.swiftcurrent.lock.LockController
import me.jhot.swiftcurrent.lock.LockResult
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.sources.AppLauncher
import me.jhot.swiftcurrent.search.sources.ContactReader
import me.jhot.swiftcurrent.search.sources.ContactRow
import me.jhot.swiftcurrent.shortcuts.Creator
import me.jhot.swiftcurrent.shortcuts.CreatorDiscovery
import me.jhot.swiftcurrent.shortcuts.LaunchMessages
import me.jhot.swiftcurrent.shortcuts.ShortcutAccess
import me.jhot.swiftcurrent.shortcuts.ShortcutEntry
import me.jhot.swiftcurrent.shortcuts.ShortcutProvider
import me.jhot.swiftcurrent.shortcuts.UserShortcuts
import me.jhot.swiftcurrent.ui.settings.SetupEnv
import java.util.concurrent.CopyOnWriteArrayList

/** Fakes shared by the shell tests, kept out of LauncherShellTest so that file is about behaviour. */
internal const val ALPHA = "pkg.alpha/.Main"
internal const val TASKER = "net.example.tasker"

internal class FakeStore(initial: LauncherData = LauncherData()) : LauncherStore {
    @Volatile var data = initial

    @Volatile var loadFails = false

    @Volatile var loads = 0

    /** Holds update() until completed, so a test can see the in-memory answer arrive first. */
    @Volatile var gate: CompletableDeferred<Unit>? = null

    @Volatile var failUpdates = false
    override suspend fun load(): LauncherData {
        loads++
        if (loadFails) throw java.io.IOException("disk")
        return data
    }
    private val lock = Any()
    override suspend fun update(transform: (LauncherData) -> LauncherData): LauncherData {
        gate?.await()
        if (failUpdates) error("disk full")
        return synchronized(lock) { transform(data).also { data = it } }
    }
}

internal class FakeCatalog(val list: List<AppInfo>) : AppCatalog {
    @Volatile var appsFails = false

    @Volatile var changesFlow: Flow<Unit> = emptyFlow()
    val appsCalls = java.util.concurrent.atomic.AtomicInteger()
    override fun apps(): List<AppInfo> {
        appsCalls.incrementAndGet()
        if (appsFails) throw IllegalStateException("binder died")
        return list
    }
    override val changes: Flow<Unit> get() = changesFlow
}

internal class FakeShortcuts : ShortcutProvider {
    val launched = CopyOnWriteArrayList<String>()

    @Volatile var shortcutAccess: ShortcutAccess = ShortcutAccess.Granted
    val accessCalls = java.util.concurrent.atomic.AtomicInteger()

    /** Set to make the access read fail, as a dead system service does. */
    @Volatile var accessFails: RuntimeException? = null
    override fun access(): ShortcutAccess {
        accessCalls.incrementAndGet()
        accessFails?.let { throw it }
        return shortcutAccess
    }
    val entriesCalls = java.util.concurrent.atomic.AtomicInteger()
    override fun entries(): List<ShortcutEntry> {
        entriesCalls.incrementAndGet()
        return listOf("Bedtime", "Work", "Home").map {
            val key = "$TASKER/${it.lowercase()}"
            ShortcutEntry(key, "Tasker", TASKER, it.lowercase(), it, pinned = key in pinnedKeys)
        } + extraEntries
    }

    /** More shortcuts beyond Tasker's, for a test that needs one named after a contact. */
    @Volatile var extraEntries: List<ShortcutEntry> = emptyList()

    @Volatile var pinnedKeys: Set<String> = emptySet()

    @Volatile var unpinOutcome: LaunchOutcome = LaunchOutcome.Launched
    override fun launch(key: String): LaunchOutcome { launched += key; return LaunchOutcome.Launched }
    val unpinned = CopyOnWriteArrayList<String>()
    override fun unpin(key: String): LaunchOutcome { unpinned += key; return unpinOutcome }
    override val changes: Flow<Unit> = emptyFlow()
}

internal class FakeCalendar(var events: List<AgendaEvent> = emptyList(), var infos: List<CalendarInfo> = emptyList()) : CalendarRepository {
    /** How many agenda reads have finished; a test waits on it because the answer lands from the IO thread. */
    val loads = java.util.concurrent.atomic.AtomicInteger()

    @Volatile var loadDelayMs = 0L
    override fun load(now: java.time.Instant, days: Int, enabled: Set<Long>?): CalendarAccess.Granted {
        if (loadDelayMs > 0) Thread.sleep(loadDelayMs)
        return CalendarAccess.Granted(AgendaMapper.filter(events, enabled).filter { it.end > now }).also { loads.incrementAndGet() }
    }
    override fun calendars() = infos

    /** What search sees; set per test. */
    @Volatile var occurrences: List<me.jhot.swiftcurrent.agenda.EventOccurrence> = emptyList()
    override fun occurrences(now: java.time.Instant, days: Int, enabled: Set<Long>?) =
        occurrences.filter { enabled == null || it.event.calendarId in enabled }
}

internal class FakeLock(var result: LockResult = LockResult.Locked) : LockController {
    var calls = 0
    override fun lock(): LockResult { calls++; return result }
}

internal class FakeSetupEnv(
    @Volatile var home: Boolean = false,
    @Volatile var contacts: Boolean = false,
    @Volatile var calendar: Boolean = false,
    @Volatile var lock: Boolean = false,
) : SetupEnv {
    override fun isDefaultHome() = home
    override fun hasContacts() = contacts
    override fun hasCalendar() = calendar
    override fun lockServiceOn() = lock
}

/** Records what the setup page asks the system for; [deliver] plays the permission result back. */
internal class FakePermissions : PermissionRequester {
    val requests = CopyOnWriteArrayList<String>()

    /** Permissions the system would still explain; absent means it will not (the owner chose "don't ask again"). */
    val rationale = mutableSetOf<String>()

    @Volatile var onResult: (String, Boolean) -> Unit = { _, _ -> }
    override fun request(permission: String) { requests += permission }
    override fun shouldShowRationale(permission: String) = permission in rationale
    fun deliver(permission: String, granted: Boolean) = onResult(permission, granted)
}

// Setup is already seen unless a test is about first run, so the page does not cover every other test's Home.
internal class FakeDependencies(data: LauncherData, apps: List<AppInfo>, setupSeen: Boolean = true) : LauncherDependencies {
    val fakeSetup = FakeSetupEnv()
    override val setupEnv: SetupEnv = fakeSetup
    val fakeStore = FakeStore(if (setupSeen) data.copy(settings = data.settings.copy(setupSeen = true)) else data)
    override val store: LauncherStore = fakeStore

    /** Counts as an older version left them in DataStore; the given data's counts start here, as on an upgrade. */
    @Volatile var legacyCounts: Map<String, Int>? = data.launchCounts.ifEmpty { null }
    val usageDao = androidx.room.Room.inMemoryDatabaseBuilder(
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(),
        SwiftcurrentDatabase::class.java,
    ).build().usage()
    override val usage = RoomUsageStore(
        usageDao,
        object : LegacyCounts {
            override suspend fun read() = legacyCounts
            override suspend fun drop() { legacyCounts = null }
        },
        { nowMs() },
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )
    override val catalog = FakeCatalog(apps)
    override val shortcuts = FakeShortcuts()
    override val creators = FakeCreators()
    val userStarts = CopyOnWriteArrayList<Intent>()

    @Volatile var userStartFails: Exception? = null
    override val userShortcuts = UserShortcuts(
        { userStarts += it; userStartFails?.let { e -> throw e } },
        LaunchMessages("shortcut gone", "shortcut not allowed", "shortcut damaged"),
    )
    val fakeCalendar = FakeCalendar()
    override val calendar: CalendarRepository = fakeCalendar

    /** The phone's contacts; a search reads the rows whose name contains the query. */
    @Volatile var contactRows: List<ContactRow> = emptyList()

    /** How many times recents asked for contacts by id; a test waits on it to know a resolve really ran. */
    val contactResolves = java.util.concurrent.atomic.AtomicInteger()
    override val contacts = object : ContactReader {
        override fun rows(query: String) = contactRows.filter { it.name.contains(query, ignoreCase = true) }
        override fun rowsForContacts(ids: Set<Long>) = contactRows.filter { it.contactId in ids }.also { contactResolves.incrementAndGet() }
    }
    val launches = CopyOnWriteArrayList<String>()

    /** The usage count the launcher saw at the moment of each launch. */
    val countsAtLaunch = CopyOnWriteArrayList<Int>()

    @Volatile var launchOutcome: LaunchOutcome = LaunchOutcome.Launched
    override val appLauncher = AppLauncher { c ->
        countsAtLaunch += usage.counts()[c] ?: 0
        launches += c
        launchOutcome
    }
    val fakeLock = FakeLock()
    override val lock: LockController = fakeLock
    override val session = LauncherSession()
    val started = CopyOnWriteArrayList<Intent>()

    @Volatile var startOutcome: LaunchOutcome = LaunchOutcome.Launched

    @Volatile var contactsGranted = true

    @Volatile var calendarGranted = true

    @Volatile var nowMillis = 1_000L

    override fun nowMs() = nowMillis
    override fun zone(): java.time.ZoneId = java.time.ZoneId.of("UTC")
    val calendarChecks = java.util.concurrent.atomic.AtomicInteger()
    override fun calendarGranted() = calendarGranted.also { calendarChecks.incrementAndGet() }
    override fun minuteTicks() = flowOf(nowMillis)
    override fun clockText() = flowOf("8:40")
    override fun dateText() = flowOf("Sun, Nov 8")
    override fun startActivity(intent: Intent): LaunchOutcome { started += intent; return startOutcome }
    val shadePulls = java.util.concurrent.atomic.AtomicInteger()

    @Volatile var shadeWorks = true
    override fun expandNotifications(): Boolean { shadePulls.incrementAndGet(); return shadeWorks }
    val recordedHaptics = RecordingHaptics()
    override val haptics: HapticPlayer = recordedHaptics
    override fun contactsGranted() = contactsGranted

    @Volatile var mainThreadLabels = 0
    override fun packageLabel(packageName: String): String? {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) mainThreadLabels++
        return null
    }

    @Volatile var lockOn = false
    override fun lockServiceOn() = lockOn

    @Volatile var unreadable: String? = null
    override suspend fun unreadableBackup() = unreadable
    override suspend fun clearUnreadableBackup() { unreadable = null }
    val documents = java.util.concurrent.ConcurrentHashMap<String, String>()
    override fun readDocument(uri: android.net.Uri) = documents[uri.toString()] ?: throw java.io.IOException("no such document")
    override fun writeDocument(uri: android.net.Uri, text: String) { documents[uri.toString()] = text }

    /** The live context a test drives directly, as the monitor would after a read. */
    val context = MutableStateFlow(ContextState())
    override val contextState: StateFlow<ContextState> = context
    val grantedPermissions: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    override fun permissionGranted(permission: String) = permission in grantedPermissions

    /** The permission every started activity demands in this test, until it is granted. */
    @Volatile var startNeeds: String? = null
    override fun permissionNeededToStart(intent: Intent): String? = startNeeds?.takeIf { it !in grantedPermissions }
    val contextRefreshes = java.util.concurrent.atomic.AtomicInteger()
    override fun refreshContext() { contextRefreshes.incrementAndGet() }

    /** What the Meld mode picker reads; null is Meld unreadable. */
    @Volatile var meldModeNames: List<String>? = null
    override val meldModes: MeldModes = object : MeldModes {
        override fun modes() = meldModeNames
        override fun active(): List<String>? = null
    }
}

/** What the launcher asked the vibrator for, so a test can tell which tick fired and how strong. */
internal class RecordingHaptics : HapticPlayer {
    val ticks = CopyOnWriteArrayList<String>()
    override fun scrub(strength: Int) { ticks += "scrub:$strength" }
    override fun gesture(strength: Int) { ticks += "gesture:$strength" }
}

internal class FakeCreators(var list: List<Creator> = emptyList()) : CreatorDiscovery {
    override fun creators() = list
}
