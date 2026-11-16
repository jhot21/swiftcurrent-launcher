package me.jhot.swiftcurrent.shell

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.sources.ContactIntentSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ContactActionLaunchTest {
    private val deps = FakeDependencies(LauncherData(), emptyList())
    private val meetVideo = ContactIntentSpec(
        "android.intent.action.VIEW", "content://com.android.contacts/data/7",
        "vnd.android.cursor.item/com.google.android.apps.tachyon.phone.meet", "com.google.android.apps.tachyon",
    )

    // Meet, Signal and WhatsApp guard their call rows with CALL_PHONE; without asking, the tap was refused outright.
    @Test
    fun a_guarded_action_asks_for_its_permission_before_it_launches() {
        deps.startNeeds = Manifest.permission.CALL_PHONE
        val asked = mutableListOf<String>()
        val out = launchContactAction(deps, meetVideo) { asked += it; deps.grantedPermissions += it; true }
        assertEquals(listOf(Manifest.permission.CALL_PHONE), asked)
        assertEquals(LaunchOutcome.Launched, out)
        assertEquals(1, deps.started.size)
        assertEquals("com.google.android.apps.tachyon", deps.started.single().`package`)
    }

    @Test
    fun an_action_that_needs_nothing_asks_nothing() {
        val asked = mutableListOf<String>()
        launchContactAction(deps, meetVideo) { asked += it; true }
        assertTrue(asked.isEmpty())
        assertEquals(1, deps.started.size)
    }

    @Test
    fun a_permission_already_held_is_not_asked_again() {
        deps.startNeeds = Manifest.permission.CALL_PHONE
        deps.grantedPermissions += Manifest.permission.CALL_PHONE
        val asked = mutableListOf<String>()
        launchContactAction(deps, meetVideo) { asked += it; true }
        assertTrue(asked.isEmpty())
    }

    // A refusal still launches, so the system's own denial reaches the owner as the usual "not allowed" message.
    @Test
    fun a_refused_permission_still_launches_and_reports_what_the_launch_says() {
        deps.startNeeds = Manifest.permission.CALL_PHONE
        deps.startOutcome = LaunchOutcome.Failed("Not allowed to open that")
        val out = launchContactAction(deps, meetVideo) { false }
        assertEquals(LaunchOutcome.Failed("Not allowed to open that"), out)
        assertEquals(1, deps.started.size)
    }
}
