package me.jhot.swiftcurrent.core

import kotlinx.serialization.Serializable

/** A state a favorite can depend on: it shows on Home only while every one of its conditions holds. */
@Serializable
sealed interface Condition {
    /** True: show while the state holds. False: show while it does not. */
    val whenTrue: Boolean

    /** A Bluetooth device is connected. A null [address] means any device; [name] is the last-known label. */
    @Serializable
    data class Bluetooth(val address: String?, val name: String, override val whenTrue: Boolean = true) : Condition

    /** The named Meld mode is active. */
    @Serializable
    data class MeldMode(val mode: String, override val whenTrue: Boolean = true) : Condition
}

object ConditionEval {
    /** The thing the condition names (a paired device, a Meld mode) is known to no longer exist. */
    fun isBroken(c: Condition, s: ContextState): Boolean = when (c) {
        is Condition.Bluetooth -> c.address != null && s.pairedBluetooth?.let { c.address !in it } == true
        is Condition.MeldMode -> s.knownModes?.let { c.mode !in it } == true
    }

    /** Polarity applies only to known state, so unknown or broken hides the favorite either way. */
    fun holds(c: Condition, s: ContextState): Boolean {
        if (isBroken(c, s)) return false
        val state = when (c) {
            is Condition.Bluetooth -> s.connectedBluetooth?.let { if (c.address == null) it.isNotEmpty() else c.address in it }
            is Condition.MeldMode -> s.activeModes?.let { c.mode in it }
        } ?: return false
        return state == c.whenTrue
    }

    fun shown(e: Entry, s: ContextState): Boolean = e.conditions.all { holds(it, s) }
}
