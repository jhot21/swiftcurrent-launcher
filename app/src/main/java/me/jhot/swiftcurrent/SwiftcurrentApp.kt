package me.jhot.swiftcurrent

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import me.jhot.swiftcurrent.context.BLUETOOTH_CHANGE_ACTIONS
import me.jhot.swiftcurrent.context.DefaultMeldModes
import me.jhot.swiftcurrent.lock.LauncherSession
import me.jhot.swiftcurrent.lock.ScreenResetReceiver
import me.jhot.swiftcurrent.shell.DefaultDependencies
import me.jhot.swiftcurrent.shell.LauncherDependencies
import me.jhot.swiftcurrent.shell.ShellData
import me.jhot.swiftcurrent.shell.ShellState

open class SwiftcurrentApp : Application() {
    val session = LauncherSession()
    val shellState = ShellState()

    /** Created once per process; the activity may be recreated, the data layer is not. */
    val dependencies: LauncherDependencies by lazy { createDependencies() }

    /** The seam tests use to run the real activity against fakes. */
    protected open fun createDependencies(): LauncherDependencies = DefaultDependencies(this, session)

    /**
     * What the shell loaded and whether first run was offered. Held here so an activity recreation
     * neither flashes a black frame while reloading nor offers setup a second time.
     */
    val shellData: ShellData by lazy { ShellData(dependencies, Dispatchers.IO) }

    override fun onCreate() {
        super.onCreate()
        // App lifetime: never unregistered, so any screen-off resets the launcher.
        registerReceiver(ScreenResetReceiver(session), ScreenResetReceiver.filter(), Context.RECEIVER_NOT_EXPORTED)

        // Context changes are nudges to re-read, never state themselves. Registered here, for the process,
        // since Meld delivers only to runtime receivers and one tied to an activity would miss changes.
        val refresh = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = dependencies.refreshContext()
        }
        // Meld is another app, so its broadcast needs an exported receiver. Any app could send this action,
        // which is harmless: it only causes a re-read of Meld's provider.
        ContextCompat.registerReceiver(
            this,
            refresh,
            IntentFilter(DefaultMeldModes.ACTION_MODES_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        // Exported because these come from the Bluetooth process (its own uid, not system_server), which a
        // not-exported receiver may not hear. They are protected broadcasts, so no other app can send them.
        ContextCompat.registerReceiver(
            this,
            refresh,
            IntentFilter().apply { BLUETOOTH_CHANGE_ACTIONS.forEach(::addAction) },
            ContextCompat.RECEIVER_EXPORTED,
        )
        // A stopped process missed every broadcast, so read once at start.
        dependencies.refreshContext()
    }
}
