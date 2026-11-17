package me.jhot.swiftcurrent

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.jhot.swiftcurrent.lock.Screen
import me.jhot.swiftcurrent.shell.LauncherShell
import me.jhot.swiftcurrent.ui.settings.SettingsRoute

class HomeActivity : ComponentActivity() {
    /** True from onStop until the next onResume: the launcher was out of sight, not just paused. */
    private var wasStopped = false

    /** A HOME intent arrived for this return, so it is not a Back and must not reopen the app list. */
    private var homeIntentPending = false

    /**
     * singleTask: a Home press, or a return from another app, arrives here instead of onCreate.
     * By then Android has already moved the activity to STARTED in both cases (a resumed launcher is
     * paused first, a stopped one is restarted first), so the lifecycle state at this point cannot
     * tell Home-on-Home from a return. Whether onStop ran since the last onResume can.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openSettingsForIconTap(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeIntentPending = true
            (application as SwiftcurrentApp).session.clearListReturn()
            val app = application as SwiftcurrentApp
            val session = app.session
            if (wasStopped) {
                session.navigate(Screen.Home)
            } else {
                app.shellState.onHomePressed(session)
            }
        }
    }

    /** The app-icon entry other launchers list: it opens Settings, which is where this app's own controls live. */
    private fun openSettingsForIconTap(intent: Intent) {
        if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && !intent.hasCategory(Intent.CATEGORY_HOME)) {
            val app = application as SwiftcurrentApp
            app.shellState.openSettings(SettingsRoute.Root)
            app.session.navigate(Screen.Settings)
        }
    }

    override fun onStop() {
        super.onStop()
        wasStopped = true
    }

    override fun onResume() {
        super.onResume()
        wasStopped = false
        if (!homeIntentPending) (application as SwiftcurrentApp).session.onReturnedWithoutHome(SystemClock.elapsedRealtime())
        homeIntentPending = false
        // Meld sends nothing for a new, renamed or deleted inactive mode, and a grant made in Settings or
        // a Meld installed later arrives silently too; reading on every return heals all of them.
        (application as SwiftcurrentApp).dependencies.refreshContext()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreation replays the original intent; only a fresh launch is a tap on the icon.
        if (savedInstanceState == null) openSettingsForIconTap(intent)
        // Light icons on any system theme: every screen is dark, and the default style follows the theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val app = application as SwiftcurrentApp
            LauncherShell(app.dependencies, state = app.shellState, model = app.shellData)
        }
    }
}
