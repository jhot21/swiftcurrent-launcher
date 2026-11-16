package me.jhot.swiftcurrent.shell

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.jhot.swiftcurrent.agenda.CalendarRepository
import me.jhot.swiftcurrent.catalog.AppCatalog
import me.jhot.swiftcurrent.catalog.RoomUsageStore
import me.jhot.swiftcurrent.context.BluetoothDevices
import me.jhot.swiftcurrent.context.MeldModes
import me.jhot.swiftcurrent.context.NoBluetoothDevices
import me.jhot.swiftcurrent.context.NoContextState
import me.jhot.swiftcurrent.context.NoMeldModes
import me.jhot.swiftcurrent.core.ContextState
import me.jhot.swiftcurrent.core.LauncherStore
import me.jhot.swiftcurrent.lock.LauncherSession
import me.jhot.swiftcurrent.lock.LockController
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.sources.AppLauncher
import me.jhot.swiftcurrent.search.sources.ContactReader
import me.jhot.swiftcurrent.shortcuts.CreatorDiscovery
import me.jhot.swiftcurrent.shortcuts.ShortcutProvider
import me.jhot.swiftcurrent.shortcuts.UserShortcuts
import me.jhot.swiftcurrent.ui.settings.SetupEnv
import java.time.ZoneId

/**
 * Everything the shell needs from the outside world, so the real Android objects live only in
 * [DefaultDependencies] and tests can supply fakes. [shortcuts], [creators], [userShortcuts],
 * [contacts], [calendar], [catalog], [appLauncher] and [startActivity] are blocking IPC: the shell calls them off the main thread.
 */
interface LauncherDependencies {
    val store: LauncherStore
    val usage: RoomUsageStore
    val catalog: AppCatalog
    val shortcuts: ShortcutProvider
    val creators: CreatorDiscovery
    val userShortcuts: UserShortcuts
    val contacts: ContactReader
    val calendar: CalendarRepository
    val appLauncher: AppLauncher
    val lock: LockController
    val session: LauncherSession

    /** What the setup page reads about the device; blocking, so read off the main thread. */
    val setupEnv: SetupEnv

    fun nowMs(): Long

    /** The time now, then at the top of every minute and whenever the owner sets the clock or the zone. */
    fun minuteTicks(): Flow<Long>
    fun clockText(): Flow<String>
    fun dateText(): Flow<String>
    fun startActivity(intent: Intent): LaunchOutcome

    /** The launcher's own haptic ticks; nothing by default so fakes need not provide one. */
    val haptics: HapticPlayer get() = HapticPlayer.None

    /** Pulls the notification shade down; true when the call went through. Best effort: the system offers no public call. */
    fun expandNotifications(): Boolean = false
    fun contactsGranted(): Boolean
    fun calendarGranted(): Boolean

    /** The zone the agenda groups days in; read per refresh so a travel zone change applies. */
    fun zone(): ZoneId = ZoneId.systemDefault()

    /** True while the lock accessibility service is connected, i.e. quick lock can work. */
    fun lockServiceOn(): Boolean

    /** The raw text of stored data that could not be read, for the recovery offer. Blocking. */
    suspend fun unreadableBackup(): String? = null

    /** Forgets the text [unreadableBackup] returns once it has been restored. */
    suspend fun clearUnreadableBackup() {}

    /** Reads the document the owner picked for a restore. Blocking: call off the main thread. */
    fun readDocument(uri: Uri): String

    /** Writes a backup to the document the owner picked. Blocking: call off the main thread. */
    fun writeDocument(uri: Uri, text: String)

    /** The installed app's name for [packageName], or null when it is gone. Blocking binder call. */
    fun packageLabel(packageName: String): String?

    // The defaults are shared objects, not `get() = <new object>`, so every read sees the same flow.

    /** Last-known Bluetooth and Meld state that favorites' conditions are checked against. */
    val contextState: StateFlow<ContextState> get() = NoContextState

    /** Meld's modes, for the mode picker. Blocking IPC. */
    val meldModes: MeldModes get() = NoMeldModes

    /** Paired and connected devices, for the device picker. Blocking, may wait on a bind. */
    val bluetooth: BluetoothDevices get() = NoBluetoothDevices

    fun permissionGranted(permission: String): Boolean = false

    /**
     * The permission the activity that would open [intent] demands and this app lacks, or null when it needs none
     * or it is held. Blocking (package manager).
     */
    fun permissionNeededToStart(intent: Intent): String? = null

    /** Re-reads every context source; for broadcasts and for right after a permission is granted. */
    fun refreshContext() {}
}
