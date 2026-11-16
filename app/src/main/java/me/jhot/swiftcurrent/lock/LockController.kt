package me.jhot.swiftcurrent.lock

import android.content.Intent
import android.provider.Settings

sealed interface LockResult {
    data object Locked : LockResult
    data object ServiceDisabled : LockResult

    /** The service is connected but the system refused the lock, so enabling it would not help. */
    data object Failed : LockResult
}

interface LockController {
    fun lock(): LockResult
}

/** What the connected accessibility service can do; a seam so the controller is testable off-device. */
interface LockActions {
    fun lockScreen(): Boolean
}

/** Locks through [LockService] when the user has enabled it, else reports [LockResult.ServiceDisabled]; a connected service the system refuses is [LockResult.Failed]. */
class AccessibilityLockController(
    private val connected: () -> LockActions? = { LockService.active },
) : LockController {
    override fun lock(): LockResult {
        val actions = connected() ?: return LockResult.ServiceDisabled
        return if (actions.lockScreen()) LockResult.Locked else LockResult.Failed
    }
}

/** Where the shell's "service is off" message links to. */
fun accessibilitySettingsIntent(): Intent =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
