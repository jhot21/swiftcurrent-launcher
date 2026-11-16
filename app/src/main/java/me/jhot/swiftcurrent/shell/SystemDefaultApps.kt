package me.jhot.swiftcurrent.shell

import android.content.Context
import android.provider.Telephony
import android.telecom.TelecomManager
import me.jhot.swiftcurrent.search.DefaultApps

/** The phone's default SMS app and dialer, read when a search starts so a change in Settings shows at once. */
fun systemDefaultApps(context: Context): DefaultApps = try {
    DefaultApps(
        sms = Telephony.Sms.getDefaultSmsPackage(context),
        dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
    )
} catch (e: RuntimeException) {
    DefaultApps()
}
