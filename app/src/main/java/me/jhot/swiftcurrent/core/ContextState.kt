package me.jhot.swiftcurrent.core

/**
 * Snapshot of the outside state favorites' conditions are checked against. A null field means unknown
 * (never read, or the last read failed), which is different from an empty set.
 */
data class ContextState(
    /** Addresses of connected Bluetooth devices. Empty when the adapter is off. */
    val connectedBluetooth: Set<String>? = null,
    /** Addresses of paired devices; unknown while the adapter is off. */
    val pairedBluetooth: Set<String>? = null,
    val activeModes: Set<String>? = null,
    val knownModes: Set<String>? = null,
) {
    /** A failed read must not blank state we already knew, so each unknown field falls back to [previous]. */
    fun keepingLastKnown(previous: ContextState): ContextState = ContextState(
        connectedBluetooth = connectedBluetooth ?: previous.connectedBluetooth,
        pairedBluetooth = pairedBluetooth ?: previous.pairedBluetooth,
        activeModes = activeModes ?: previous.activeModes,
        knownModes = knownModes ?: previous.knownModes,
    )
}
