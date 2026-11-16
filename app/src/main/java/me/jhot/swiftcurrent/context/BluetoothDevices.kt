package me.jhot.swiftcurrent.context

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class PairedDevice(val address: String, val name: String)

/**
 * Broadcasts after which the Bluetooth state may have changed and should be read again. The profile
 * actions matter: A2DP and headset report a device connected seconds after its ACL link comes up, so a
 * read on ACL_CONNECTED alone usually misses it.
 */
val BLUETOOTH_CHANGE_ACTIONS: List<String> = listOf(
    BluetoothDevice.ACTION_ACL_CONNECTED,
    BluetoothDevice.ACTION_ACL_DISCONNECTED,
    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
    BluetoothAdapter.ACTION_STATE_CHANGED,
)

/**
 * The phone's Bluetooth devices. Null means unknown (permission revoked, adapter unreadable). Blocking,
 * and may wait for a profile service to bind: never call on the main thread.
 */
interface BluetoothDevices {
    /** Paired devices; unknown while the adapter is off, since it then lists none. */
    fun paired(): List<PairedDevice>?

    /** Addresses of connected audio devices; empty while the adapter is off. */
    fun connected(): Set<String>?
}

object NoBluetoothDevices : BluetoothDevices {
    override fun paired(): List<PairedDevice>? = null
    override fun connected(): Set<String>? = null
}

/**
 * Connected devices come from the A2DP and headset profile proxies: classic audio devices such as earbuds
 * do not show in the GATT connected list. The proxies are bound once and kept, since binding is
 * asynchronous and slow; a bind that has not finished within [bindTimeoutMs] reads as unknown, and the
 * next read waits on the same bind again.
 */
class DefaultBluetoothDevices(context: Context, private val bindTimeoutMs: Long = 2_000L) : BluetoothDevices {
    private val app = context.applicationContext
    private val adapter: BluetoothAdapter? get() = app.getSystemService(BluetoothManager::class.java)?.adapter

    private val proxies = ConcurrentHashMap<Int, BluetoothProfile>()
    private val lock = Any()
    private val binding = HashMap<Int, CountDownLatch>() // guarded by lock

    // Called on the main thread, so a read waiting for a bind must not run there.
    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            val kept = proxies.putIfAbsent(profile, proxy)
            // A second answer for a profile already held (a rebind racing a late one): keep one, release the other.
            if (kept != null && kept !== proxy) closeQuietly(profile, proxy)
            synchronized(lock) { binding.remove(profile) }?.countDown()
        }

        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)
        }
    }

    init {
        PROFILES.forEach(::bind)
    }

    @SuppressLint("MissingPermission") // granted() is checked first; a revoke in between lands in the catch
    override fun paired(): List<PairedDevice>? {
        if (!granted()) return null
        return try {
            val adapter = adapter ?: return null
            if (!adapter.isEnabled) return null
            adapter.bondedDevices.map { PairedDevice(it.address, it.name ?: it.address) }
        } catch (e: RuntimeException) {
            null
        }
    }

    @SuppressLint("MissingPermission") // as in paired()
    override fun connected(): Set<String>? {
        if (!granted()) return null
        return try {
            val adapter = adapter ?: return null
            // Off is a readable state: nothing can be connected.
            if (!adapter.isEnabled) return emptySet()
            val connected = HashSet<String>()
            for (profile in PROFILES) {
                val proxy = proxy(profile) ?: return null
                proxy.connectedDevices.mapTo(connected) { it.address }
            }
            connected
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun granted() =
        app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun proxy(profile: Int): BluetoothProfile? {
        proxies[profile]?.let { return it }
        // A bind that times out stays registered, so the next read waits on it again rather than asking
        // for (and leaking) another proxy each time.
        bind(profile)?.await(bindTimeoutMs, TimeUnit.MILLISECONDS) ?: return null
        return proxies[profile]
    }

    /** Asks for [profile]'s proxy unless a bind is already under way; null when the system refused to bind. */
    private fun bind(profile: Int): CountDownLatch? = synchronized(lock) {
        binding[profile]?.let { return it }
        // Registered before asking, so an answer that arrives at once still finds its latch.
        val latch = CountDownLatch(1)
        binding[profile] = latch
        val asked = try {
            adapter?.getProfileProxy(app, listener, profile) == true
        } catch (e: RuntimeException) {
            false
        }
        if (asked) {
            latch
        } else {
            binding.remove(profile)
            null
        }
    }

    private fun closeQuietly(profile: Int, proxy: BluetoothProfile) {
        try {
            adapter?.closeProfileProxy(profile, proxy)
        } catch (e: RuntimeException) {
            // Nothing to do: the duplicate is only a held binding.
        }
    }

    private companion object {
        val PROFILES = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
    }
}
