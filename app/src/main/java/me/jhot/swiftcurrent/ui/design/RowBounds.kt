package me.jhot.swiftcurrent.ui.design

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot

/**
 * A row's place on screen, kept for the moment a swipe or an accessibility action needs it. It is a plain
 * field, not state: a list row is positioned again on every scroll frame, and a state write each time
 * would invalidate whatever reads it for a value that matters only once, on a swipe.
 */
class RowBounds {
    private var coords: LayoutCoordinates? = null

    fun track(c: LayoutCoordinates) { coords = c }

    /** The row's bounds in root coordinates now, or [Rect.Zero] if it is not on screen. */
    fun now(): Rect = coords?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero
}
