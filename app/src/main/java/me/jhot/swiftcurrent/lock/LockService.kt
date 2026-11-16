package me.jhot.swiftcurrent.lock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** Exists only to lock the screen; it listens to no events and reads no window content. */
class LockService : AccessibilityService(), LockActions {
    override fun onServiceConnected() {
        active = this
    }

    override fun lockScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        clear()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clear()
        super.onDestroy()
    }

    private fun clear() {
        if (active === this) active = null
    }

    companion object {
        @Volatile
        var active: LockService? = null
            private set
    }
}
