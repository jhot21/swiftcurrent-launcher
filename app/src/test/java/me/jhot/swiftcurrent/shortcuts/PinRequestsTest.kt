package me.jhot.swiftcurrent.shortcuts

import android.content.pm.LauncherApps.PinItemRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRequestsTest {
    @Test
    fun only_a_valid_shortcut_request_is_handled() {
        assertTrue(isShortcutPin(PinItemRequest.REQUEST_TYPE_SHORTCUT, valid = true))
        assertFalse("a request the system has withdrawn", isShortcutPin(PinItemRequest.REQUEST_TYPE_SHORTCUT, valid = false))
        assertFalse("widgets are not supported", isShortcutPin(PinItemRequest.REQUEST_TYPE_APPWIDGET, valid = true))
    }
}
