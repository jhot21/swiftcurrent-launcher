package me.jhot.swiftcurrent

import android.os.Build

/**
 * True only for the Android emulator. Device tests that change the default home app use this to
 * skip on anything else, so it keys on emulator hardware ("ranchu"/"goldfish") plus an SDK
 * product; fingerprint substrings can appear in custom phone builds and are not trusted.
 */
fun isEmulator(hardware: String, product: String): Boolean =
    hardware in setOf("ranchu", "goldfish") && product.startsWith("sdk")

fun isRunningOnEmulator(): Boolean = isEmulator(Build.HARDWARE, Build.PRODUCT)
