package me.jhot.swiftcurrent.ui.editor

import me.jhot.swiftcurrent.core.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConditionTextTest {
    private fun summary(vararg c: Condition) = conditionSummary(c.toList(), EnglishEditorStrings)

    @Test
    fun none_is_null() = assertNull(summary())

    @Test
    fun meld_mode_active_and_inactive() {
        assertEquals("Shown when Meld mode \"church\" is active", summary(Condition.MeldMode("church")))
        assertEquals("Shown when Meld mode \"church\" is inactive", summary(Condition.MeldMode("church", whenTrue = false)))
    }

    @Test
    fun named_bluetooth_device_connected_and_not() {
        assertEquals("Shown when Anker Soundcore is connected", summary(Condition.Bluetooth("AA:BB:CC:DD:EE:01", "Anker Soundcore")))
        assertEquals(
            "Shown when Anker Soundcore is not connected",
            summary(Condition.Bluetooth("AA:BB:CC:DD:EE:01", "Anker Soundcore", whenTrue = false)),
        )
    }

    @Test
    fun any_bluetooth_device() {
        assertEquals("Shown when any Bluetooth device is connected", summary(Condition.Bluetooth(null, "")))
        assertEquals("Shown when no Bluetooth device is connected", summary(Condition.Bluetooth(null, "", whenTrue = false)))
    }

    @Test
    fun several_conditions_join_with_and() {
        assertEquals(
            "Shown when Meld mode \"church\" is active and Anker Soundcore is connected",
            summary(Condition.MeldMode("church"), Condition.Bluetooth("AA:BB:CC:DD:EE:01", "Anker Soundcore")),
        )
    }
}
