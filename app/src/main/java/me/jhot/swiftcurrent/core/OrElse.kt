package me.jhot.swiftcurrent.core

import kotlinx.coroutines.CancellationException

/**
 * Runs [block], answering [fallback] when it throws. Cancellation is rethrown: swallowing it would keep a
 * cancelled coroutine running. Inline so [block] may suspend.
 */
inline fun <T> orElse(fallback: T, block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    fallback
}
