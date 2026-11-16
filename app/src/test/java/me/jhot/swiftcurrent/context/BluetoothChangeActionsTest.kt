package me.jhot.swiftcurrent.context

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothChangeActionsTest {
    // ACL comes up seconds before A2DP/headset report the device connected, so the profile actions must
    // nudge too or the read after ACL_CONNECTED misses the earbuds and nothing reads again.
    @Test
    fun nudges_on_acl_profile_and_adapter_changes() {
        assertEquals(
            setOf(
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothAdapter.ACTION_STATE_CHANGED,
            ),
            BLUETOOTH_CHANGE_ACTIONS.toSet(),
        )
    }
}
