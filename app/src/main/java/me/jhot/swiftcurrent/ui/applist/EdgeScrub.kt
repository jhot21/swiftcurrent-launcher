package me.jhot.swiftcurrent.ui.applist

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The scrubber's own press/drag/lift handling, so a finger it never saw land can drive it. */
internal interface ScrubSink {
    /** A fresh touch-down: selects, jumps and counts as a tap on the letter. */
    fun down(y: Float)
    fun move(y: Float)

    /** A lift; lifting on the cog opens settings, or on the star returns to favorites when it has an action. */
    fun up(y: Float)

    /** The finger was taken away (by the system or a reset): end the press with no lift rule. */
    fun cancel()
}

/**
 * Hands a finger that landed on Home's right strip to the app list's scrubber, so the list opens
 * and scrubs in one motion. Home's gesture detector keeps receiving that pointer after the list
 * appears on top (hit testing happened at touch-down), and forwards its positions here.
 *
 * The finger lands before the list is composed, so what happens before the scrubber attaches is
 * kept and replayed on attach: the touch-down as a fresh press, then the latest position, then a
 * lift if the finger already left. Positions are in root coordinates; the scrubber converts.
 *
 * The list shows from the touch-down, so the finger lands on a list already scrolled to the letter
 * under it. [moved] is whether the finger has gone past touch slop since: a lift that never moved
 * was a stray touch, which the shell closes again, while a moved finger leaves the list open.
 */
@Stable
internal class EdgeScrub {
    /** A finger from Home is down and driving the scrubber. */
    var active by mutableStateOf(false)
        private set

    /** The finger has gone past touch slop since it landed. */
    var moved = false
        private set

    private var downY = 0f
    private var y = 0f
    private var pendingUp = false
    private var sink: ScrubSink? = null

    // Whether [sink] has seen the touch-down of the current finger.
    private var delivered = false

    fun press(y: Float) {
        downY = y
        this.y = y
        moved = false
        pendingUp = false
        delivered = false
        active = true
        sink?.let(::deliver)
    }

    fun move(y: Float, moved: Boolean) {
        if (!active) return
        this.y = y
        if (moved) this.moved = true
        if (delivered) sink?.move(y)
    }

    fun release(y: Float) {
        if (!active) return
        this.y = y
        active = false
        val s = sink
        if (s != null && delivered) {
            s.up(y)
            delivered = false
        } else {
            pendingUp = true
        }
    }

    fun cancel() {
        if (delivered) sink?.cancel()
        delivered = false
        pendingUp = false
        active = false
    }

    internal fun attach(sink: ScrubSink) {
        this.sink = sink
        if (active && !delivered) deliver(sink)
        else if (pendingUp) {
            pendingUp = false
            sink.down(downY)
            if (y != downY) sink.move(y)
            sink.up(y)
        }
    }

    internal fun detach(sink: ScrubSink) {
        if (this.sink !== sink) return
        this.sink = null
        delivered = false
    }

    private fun deliver(s: ScrubSink) {
        delivered = true
        s.down(downY)
        if (y != downY) s.move(y)
    }
}
