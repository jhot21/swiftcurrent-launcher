package me.jhot.swiftcurrent.context

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.jhot.swiftcurrent.core.ContextState
import me.jhot.swiftcurrent.core.orElse
import java.util.concurrent.atomic.AtomicBoolean

/** What a shell without live context sees: nothing known, so conditional favorites stay hidden. */
val NoContextState: StateFlow<ContextState> = MutableStateFlow(ContextState())

/**
 * Holds the last-known Bluetooth and Meld state for the process. Each [refresh] re-reads every source and
 * keeps the previous value of any that could not be read, so a failed read never blanks known state.
 */
class ContextMonitor(
    private val meld: MeldModes,
    private val bluetooth: BluetoothDevices,
    private val io: CoroutineDispatcher,
    private val scope: CoroutineScope,
) {
    private val current = MutableStateFlow(ContextState())
    val state: StateFlow<ContextState> = current.asStateFlow()

    // One read at a time, so an older, slower read cannot land after a newer one; and a burst of
    // broadcasts while a read waits collapses into that one read instead of queueing a read each.
    private val reading = Mutex()
    private val queued = AtomicBoolean(false)

    fun refresh() {
        if (!queued.compareAndSet(false, true)) return
        scope.launch(io) {
            reading.withLock {
                queued.set(false)
                val read = ContextState(
                    connectedBluetooth = unknownOnFailure { bluetooth.connected() },
                    pairedBluetooth = unknownOnFailure { bluetooth.paired() }?.mapTo(HashSet()) { it.address },
                    activeModes = unknownOnFailure { meld.active() }?.toSet(),
                    knownModes = unknownOnFailure { meld.modes() }?.toSet(),
                )
                current.update { read.keepingLastKnown(it) }
            }
        }
    }

    // Per reader, so one broken source cannot stop the others updating or kill the monitor's scope.
    private inline fun <T> unknownOnFailure(read: () -> T?): T? = orElse(null) {
        read()
    }
}
