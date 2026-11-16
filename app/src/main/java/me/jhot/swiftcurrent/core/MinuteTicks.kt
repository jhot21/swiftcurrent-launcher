package me.jhot.swiftcurrent.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Emits [nowMs] at once, then at the top of every minute, and again as soon as [changes] fires: the owner
 * set the clock or the time zone, so the minute the next tick was aimed at is no longer the next one.
 */
fun minuteTicks(nowMs: () -> Long, changes: Flow<Unit>): Flow<Long> = channelFlow {
    val changed = Channel<Unit>(Channel.CONFLATED)
    launch { changes.collect { changed.trySend(Unit) } }
    while (true) {
        val now = nowMs()
        send(now)
        // A little past the boundary, so the tick never lands just before the minute it is for.
        withTimeoutOrNull(MINUTE_MS - (now % MINUTE_MS) + SLACK_MS) { changed.receive() }
    }
}

private const val MINUTE_MS = 60_000L
private const val SLACK_MS = 50L
