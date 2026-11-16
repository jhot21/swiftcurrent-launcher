package me.jhot.swiftcurrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorGuardTest {
    @Test
    fun emulator_guard_rejects_phone_like_fingerprints_and_accepts_ranchu() {
        // Pixel production build
        assertFalse(isEmulator("tegu", "tegu"))
        // Custom ROM whose fingerprint merely contains the old markers
        assertFalse(isEmulator("qcom", "lineage_phone"))
        assertFalse(isEmulator("tegu", "sdk_clone"))
        // Hardware alone is what matters, and an unknown value is a rejection
        assertFalse(isEmulator("", ""))
        // The AVD
        assertTrue(isEmulator("ranchu", "sdk_gphone64_arm64"))
        assertTrue(isEmulator("goldfish", "sdk"))
    }
}
