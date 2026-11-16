package me.jhot.swiftcurrent.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable

/** A pin request the owner is asked about: what to show, and the one call that grants it. */
interface PendingPin {
    val label: String

    /** Blocking IPC: call off the main thread. */
    fun icon(densityDpi: Int): Drawable?

    /** Blocking IPC. False when the system no longer honours the request. */
    fun accept(): Boolean
}

/** Reads the pin request an activity was started with; null when there is nothing we handle. */
fun interface PinRequestSource {
    fun from(intent: Intent): PendingPin?
}

/** The one rule for which requests we handle: a request still valid, and for a shortcut (not a widget). */
fun isShortcutPin(type: Int, valid: Boolean): Boolean =
    valid && type == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT

class LauncherAppsPinSource(context: Context) : PinRequestSource {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    override fun from(intent: Intent): PendingPin? {
        val request = launcherApps.getPinItemRequest(intent) ?: return null
        if (!isShortcutPin(request.requestType, request.isValid)) return null
        val info = request.shortcutInfo ?: return null
        val label = listOf(info.shortLabel, info.longLabel).firstNotNullOfOrNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        return object : PendingPin {
            override val label: String = label.orEmpty()
            override fun icon(densityDpi: Int): Drawable? = launcherApps.getShortcutIconDrawable(info, densityDpi)
            override fun accept(): Boolean = request.accept()
        }
    }
}
