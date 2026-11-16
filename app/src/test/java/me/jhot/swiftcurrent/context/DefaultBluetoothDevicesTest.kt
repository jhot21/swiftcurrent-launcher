package me.jhot.swiftcurrent.context

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowBluetoothAdapter

/** Counts proxy requests and holds each listener, so the test decides when (or whether) a bind finishes. */
@Implements(BluetoothAdapter::class)
class RecordingBluetoothAdapter : ShadowBluetoothAdapter() {
    @Implementation
    override fun getProfileProxy(context: Context, listener: BluetoothProfile.ServiceListener, profile: Int): Boolean {
        requests += profile
        listeners[profile] = listener
        return true
    }

    companion object {
        val requests = mutableListOf<Int>()
        val listeners = HashMap<Int, BluetoothProfile.ServiceListener>()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], shadows = [RecordingBluetoothAdapter::class])
class DefaultBluetoothDevicesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        RecordingBluetoothAdapter.requests.clear()
        RecordingBluetoothAdapter.listeners.clear()
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        // Off reads as an empty set, not unknown, so the adapter has to be on for a pending bind to show.
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(true)
    }

    // A bind that missed the timeout used to be dropped, so every later read asked for another proxy,
    // waited the full timeout again and registered one more proxy object.
    @Test
    fun a_bind_that_has_not_finished_is_waited_on_not_requested_again() {
        val devices = DefaultBluetoothDevices(context, bindTimeoutMs = 20)
        val asked = RecordingBluetoothAdapter.requests.toList()
        assertEquals(setOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET), asked.toSet())

        assertNull(devices.connected())
        assertNull(devices.connected())

        assertEquals("no further proxy requests while the first binds are pending", asked, RecordingBluetoothAdapter.requests)
    }

    @Test
    fun a_late_bind_is_picked_up_by_the_next_read() {
        val devices = DefaultBluetoothDevices(context, bindTimeoutMs = 20)
        assertNull(devices.connected())

        RecordingBluetoothAdapter.listeners.forEach { (profile, listener) ->
            listener.onServiceConnected(profile, FakeProfile(setOf("AA:BB:CC:DD:EE:FF")))
        }

        assertEquals(setOf("AA:BB:CC:DD:EE:FF"), devices.connected())
        assertEquals(2, RecordingBluetoothAdapter.requests.size)
    }

    private class FakeProfile(private val addresses: Set<String>) : BluetoothProfile {
        override fun getConnectedDevices(): List<BluetoothDevice> = addresses.map {
            android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice(it)
        }
        override fun getDevicesMatchingConnectionStates(states: IntArray): List<BluetoothDevice> = emptyList()
        override fun getConnectionState(device: BluetoothDevice): Int = BluetoothProfile.STATE_CONNECTED
    }
}
