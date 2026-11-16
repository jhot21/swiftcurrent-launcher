package me.jhot.swiftcurrent.ui.applist

/** Where a scrubbed letter's block (its header plus its apps) should land in the list's visible area. */
internal object ScrubTarget {
    fun blockHeight(headerPx: Int, rowPx: Int, rows: Int): Int = headerPx + rowPx * rows

    /**
     * Scroll offset for the block's header, relative to the top of the visible area: negative to put the
     * block in the middle, 0 when it is as tall as the area or taller (the header goes at the top).
     */
    fun offset(blockPx: Int, viewportPx: Int): Int =
        if (blockPx >= viewportPx) 0 else -((viewportPx - blockPx) / 2)
}
