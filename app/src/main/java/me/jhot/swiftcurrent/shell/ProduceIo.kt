package me.jhot.swiftcurrent.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.core.orElse

/**
 * [produceState] for a blocking read: [read] runs on [io], and a read that throws leaves the current
 * value ([default] until a read has succeeded) instead of crashing the composition.
 */
@Composable
internal fun <T> produceIo(default: T, io: CoroutineDispatcher, vararg keys: Any?, read: suspend () -> T): State<T> =
    produceState(default, *keys) {
        value = orElse(value) { withContext(io) { read() } }
    }
