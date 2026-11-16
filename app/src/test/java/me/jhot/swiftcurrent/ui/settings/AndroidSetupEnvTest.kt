package me.jhot.swiftcurrent.ui.settings

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.app.role.RoleManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.lock.LockService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AndroidSetupEnvTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val env = AndroidSetupEnv(app)

    private fun service(pkg: String, name: String) = AccessibilityServiceInfo().also {
        val resolve = ResolveInfo().apply { serviceInfo = ServiceInfo().apply { packageName = pkg; this.name = name } }
        // The platform class exposes no setter for it.
        AccessibilityServiceInfo::class.java.getDeclaredField("mResolveInfo").apply { isAccessible = true }.set(it, resolve)
    }

    @Test
    fun permissions_follow_the_granted_state() {
        assertFalse(env.hasContacts())
        assertFalse(env.hasCalendar())
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        assertTrue(env.hasContacts())
        assertFalse(env.hasCalendar())
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        assertTrue(env.hasCalendar())
    }

    @Test
    fun default_home_follows_the_home_role() {
        assertFalse(env.isDefaultHome())
        shadowOf(app.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_HOME)
        assertTrue(env.isDefaultHome())
    }

    @Test
    fun the_lock_step_matches_only_this_apps_service() {
        val manager = app.getSystemService(AccessibilityManager::class.java)
        assertFalse(env.lockServiceOn())
        shadowOf(manager).setEnabledAccessibilityServiceList(listOf(service("other.app", LockService::class.java.name)))
        assertFalse(env.lockServiceOn())
        shadowOf(manager).setEnabledAccessibilityServiceList(listOf(service(app.packageName, LockService::class.java.name)))
        assertTrue(env.lockServiceOn())
    }
}
