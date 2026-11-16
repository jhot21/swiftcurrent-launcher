package me.jhot.swiftcurrent.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/** Resets the session on every screen-off, whatever turned the screen off (power key, timeout, our lock). */
class ScreenResetReceiver(private val session: LauncherSession) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) session.onScreenOff()
    }

    companion object {
        // SCREEN_OFF cannot be received by a manifest receiver, so the app registers this at startup.
        fun filter() = IntentFilter(Intent.ACTION_SCREEN_OFF)
    }
}
