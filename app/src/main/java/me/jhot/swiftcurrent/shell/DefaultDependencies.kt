package me.jhot.swiftcurrent.shell

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.agenda.CalendarRepository
import me.jhot.swiftcurrent.agenda.ContentResolverCalendarRepository
import me.jhot.swiftcurrent.catalog.AppCatalog
import me.jhot.swiftcurrent.catalog.LauncherAppsCatalog
import me.jhot.swiftcurrent.catalog.RoomUsageStore
import me.jhot.swiftcurrent.context.BluetoothDevices
import me.jhot.swiftcurrent.context.ContextMonitor
import me.jhot.swiftcurrent.context.DefaultBluetoothDevices
import me.jhot.swiftcurrent.context.DefaultMeldModes
import me.jhot.swiftcurrent.context.MeldModes
import me.jhot.swiftcurrent.core.ContextState
import me.jhot.swiftcurrent.core.DataStoreLauncherStore
import me.jhot.swiftcurrent.core.LauncherStore
import me.jhot.swiftcurrent.core.db.SwiftcurrentDatabase
import me.jhot.swiftcurrent.core.minuteTicks
import me.jhot.swiftcurrent.lock.AccessibilityLockController
import me.jhot.swiftcurrent.lock.LauncherSession
import me.jhot.swiftcurrent.lock.LockController
import me.jhot.swiftcurrent.lock.LockService
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.sources.AndroidConnectedAppResolver
import me.jhot.swiftcurrent.search.sources.AppLauncher
import me.jhot.swiftcurrent.search.sources.ContactReader
import me.jhot.swiftcurrent.search.sources.ContentResolverContactReader
import me.jhot.swiftcurrent.shortcuts.CreatorDiscovery
import me.jhot.swiftcurrent.shortcuts.LauncherAppsShortcutProvider
import me.jhot.swiftcurrent.shortcuts.PackageManagerCreatorDiscovery
import me.jhot.swiftcurrent.shortcuts.ShortcutMessages
import me.jhot.swiftcurrent.shortcuts.ShortcutProvider
import me.jhot.swiftcurrent.shortcuts.UserShortcuts
import me.jhot.swiftcurrent.ui.settings.AndroidSetupEnv
import me.jhot.swiftcurrent.ui.settings.SetupEnv
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clock and date wording, kept apart from the flow so the formats can be tested. */
internal object ClockText {
    fun clock(time: ZonedDateTime, locale: Locale, is24Hour: Boolean): String =
        DateTimeFormatter.ofPattern(if (is24Hour) "H:mm" else "h:mm", locale).format(time)

    fun date(time: ZonedDateTime, locale: Locale): String =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale).format(time)
}

/** The real Android objects; the only place they are created. */
class DefaultDependencies(context: Context, override val session: LauncherSession) : LauncherDependencies {
    private val app = context.applicationContext

    private val dataStore = DataStoreLauncherStore(app)
    override val store: LauncherStore = dataStore

    // Room allows one open instance per file; this object is made once per process (by the application), so
    // holding the database here is what keeps it single.
    private val database = SwiftcurrentDatabase.open(app)
    override val usage = RoomUsageStore(database.usage(), dataStore)
    override val catalog: AppCatalog = LauncherAppsCatalog(app)
    override val shortcuts: ShortcutProvider = LauncherAppsShortcutProvider(
        app,
        ShortcutMessages(
            gone = app.getString(R.string.shortcut_launch_gone),
            unavailable = app.getString(R.string.shortcut_launch_unavailable),
            needsDefaultHome = app.getString(R.string.shortcut_launch_needs_default_home),
        ),
    )
    override val creators: CreatorDiscovery = PackageManagerCreatorDiscovery(app.packageManager, app.packageName, app.getString(R.string.shortcut_web_link), app.getString(R.string.shortcut_chat_link))
    override val userShortcuts: UserShortcuts = UserShortcuts.create(app)
    override val contacts: ContactReader = ContentResolverContactReader(app, AndroidConnectedAppResolver(app))
    override val calendar: CalendarRepository = ContentResolverCalendarRepository(app)
    override val lock: LockController = AccessibilityLockController()
    override val setupEnv: SetupEnv = AndroidSetupEnv(app)
    override val haptics: HapticPlayer = VibratorHaptics(app)

    override val meldModes: MeldModes = DefaultMeldModes(app.contentResolver)
    override val bluetooth: BluetoothDevices = DefaultBluetoothDevices(app)

    // Process lifetime like this object; supervised so a failed read cannot cancel later ones.
    private val contextScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val contextMonitor = ContextMonitor(meldModes, bluetooth, Dispatchers.IO, contextScope)
    override val contextState: StateFlow<ContextState> = contextMonitor.state

    override fun refreshContext() = contextMonitor.refresh()

    override fun permissionNeededToStart(intent: Intent): String? =
        try {
            app.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
                ?.activityInfo?.permission
                ?.takeIf { it.isNotEmpty() && !permissionGranted(it) }
        } catch (e: RuntimeException) {
            null
        }

    override fun permissionGranted(permission: String) =
        app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override val appLauncher = AppLauncher { component ->
        val name = android.content.ComponentName.unflattenFromString(component)
            ?: return@AppLauncher LaunchOutcome.Failed(app.getString(R.string.launch_unavailable))
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    override fun nowMs() = System.currentTimeMillis()

    // One timer behind the clock, the date and the agenda, running only while one of them is collected.
    private val ticks: Flow<Long> by lazy {
        minuteTicks(::nowMs, timeChanges())
            .shareIn(contextScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)
    }

    override fun minuteTicks(): Flow<Long> = ticks

    // The zone and locale are read per tick, so a change applies on the next one.
    private fun zoned(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone())

    override fun clockText(): Flow<String> = ticks.map {
        ClockText.clock(zoned(it), Locale.getDefault(), DateFormat.is24HourFormat(app))
    }.distinctUntilChanged()

    override fun dateText(): Flow<String> = ticks.map { ClockText.date(zoned(it), Locale.getDefault()) }.distinctUntilChanged()

    // Setting the clock or moving zones does not wait for the next tick.
    private fun timeChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { app.unregisterReceiver(receiver) }
    }

    override fun startActivity(intent: Intent): LaunchOutcome = try {
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        LaunchOutcome.Launched
    } catch (e: ActivityNotFoundException) {
        LaunchOutcome.Failed(app.getString(R.string.launch_unavailable))
    } catch (e: SecurityException) {
        LaunchOutcome.Failed(app.getString(R.string.launch_not_allowed))
    }

    // StatusBarManager.expandNotificationsPanel is hidden but stable and is what launchers use; the
    // EXPAND_STATUS_BAR permission is a normal one. A failure leaves the shade closed, never crashes.
    @SuppressLint("WrongConstant", "PrivateApi")
    override fun expandNotifications(): Boolean = try {
        val bar = app.getSystemService("statusbar")
        if (bar == null) {
            false
        } else {
            bar.javaClass.getMethod("expandNotificationsPanel").invoke(bar)
            true
        }
    } catch (e: Exception) {
        android.util.Log.w("DefaultDependencies", "could not expand the notification shade", e)
        false
    }

    override fun contactsGranted() =
        app.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override fun calendarGranted() =
        app.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override fun lockServiceOn() = LockService.active != null

    override suspend fun unreadableBackup(): String? = dataStore.unreadableBackup()

    override suspend fun clearUnreadableBackup() {
        dataStore.clearUnreadableBackup()
    }

    override fun readDocument(uri: Uri): String =
        app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw java.io.IOException("cannot open the document")

    override fun writeDocument(uri: Uri, text: String) {
        // "wt" truncates: overwriting a longer old file must not leave its tail behind.
        (app.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("cannot open the document"))
            .use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    override fun packageLabel(packageName: String): String? = try {
        app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
