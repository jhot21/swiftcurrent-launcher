package me.jhot.swiftcurrent.shell

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import me.jhot.swiftcurrent.core.HapticStrength

/** What a tick is made of: how long it buzzes and how hard (1..255). */
data class HapticSpec(val millis: Long, val amplitude: Int)

/** The numbers behind each strength, in one place so they can be tuned and tested. */
object HapticTuning {
    /** A scrub tick fires on every letter, so it is short; the gesture tick fires once, so it can be a firmer buzz. */
    fun scrub(strength: Int) = spec(10, strength)

    fun gesture(strength: Int) = spec(24, strength)

    /** Null at zero: no tick at all. Otherwise the strength is a share of the vibrator's full amplitude. */
    private fun spec(millis: Long, strength: Int): HapticSpec? {
        val s = HapticStrength.clamp(strength)
        if (s == 0) return null
        return HapticSpec(millis, (255 * s / HapticStrength.MAX).coerceIn(1, 255))
    }
}

/** Plays the launcher's own ticks. Separate from the Compose haptics so the strength can be chosen. */
interface HapticPlayer {
    fun scrub(strength: Int)
    fun gesture(strength: Int)

    object None : HapticPlayer {
        override fun scrub(strength: Int) = Unit
        override fun gesture(strength: Int) = Unit
    }
}

/** Buzzes the phone's vibrator at a chosen amplitude, as touch feedback so the system's own touch-feedback setting still applies. */
class VibratorHaptics(context: Context) : HapticPlayer {
    private val vibrator = context.applicationContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)

    override fun scrub(strength: Int) = play(HapticTuning.scrub(strength))

    override fun gesture(strength: Int) = play(HapticTuning.gesture(strength))

    private fun play(spec: HapticSpec?) {
        val v = vibrator ?: return
        if (spec == null) return
        val amplitude = if (v.hasAmplitudeControl()) spec.amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        try {
            v.vibrate(VibrationEffect.createOneShot(spec.millis, amplitude), attributes)
        } catch (e: RuntimeException) {
            // A haptic is never worth a crash.
            android.util.Log.w("VibratorHaptics", "could not vibrate", e)
        }
    }
}
