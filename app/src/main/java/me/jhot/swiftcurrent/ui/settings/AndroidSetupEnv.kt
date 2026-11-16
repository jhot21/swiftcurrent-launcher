package me.jhot.swiftcurrent.ui.settings

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import me.jhot.swiftcurrent.lock.LockService

/**
 * The device's answers for the setup page. The lock step reads the enabled-services list rather than
 * [LockService.active]: that is the owner's switch in system settings, and it is right at once after a
 * process start, before the service has reconnected.
 */
class AndroidSetupEnv(context: Context) : SetupEnv {
    private val app = context.applicationContext

    override fun isDefaultHome(): Boolean =
        app.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

    override fun hasContacts() = granted(Manifest.permission.READ_CONTACTS)

    override fun hasCalendar() = granted(Manifest.permission.READ_CALENDAR)

    override fun lockServiceOn(): Boolean {
        val manager = app.getSystemService(AccessibilityManager::class.java) ?: return false
        val ours = LockService::class.java.name
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.resolveInfo.serviceInfo.let { s -> s.packageName == app.packageName && s.name == ours }
        }
    }

    private fun granted(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
