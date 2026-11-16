package me.jhot.swiftcurrent.ui.home

import kotlin.math.floor

/** Layout numbers shared by the screen and the capacity math so the two cannot drift apart. */
object HomeLayoutDefaults {
    /** Pitch of one favorite row at font scale 1 (Niagara's reference is about 56 dp). */
    const val ROW_HEIGHT_DP = 56f

    /**
     * Clock, date and event line plus their spacing, at font scale 1. Measured on the real layout
     * (HomeScreenTest): about 165 dp at scale 1 with an event line, so 172 leaves a little slack.
     * Larger font scales grow the real header more slowly than linearly, so scaling this by
     * fontScale never underestimates it.
     */
    const val HEADER_BLOCK_HEIGHT_DP = 172f

    /** Space kept between the last favorite and the bottom inset; the default of the owner's setting (core.HomeSpacing). */
    const val BOTTOM_GAP_DP = 32f

    /** Only used by tests as plausible Pixel 9a values; the screen reads the real window insets. */
    const val TYPICAL_TOP_INSET_DP = 32f
    const val TYPICAL_BOTTOM_INSET_DP = 24f
}

data class HomeMetrics(
    val heightDp: Float,
    val topInsetDp: Float,
    val bottomInsetDp: Float,
    val fontScale: Float,
    val rowHeightDp: Float,
    val headerBlockHeightDp: Float,
    val bottomGapDp: Float,
)

/** The one place the home layout constants meet the window, so home and the editor count the same capacity. */
fun homeMetrics(
    heightDp: Float,
    topInsetDp: Float,
    bottomInsetDp: Float,
    fontScale: Float,
    bottomGapDp: Float = HomeLayoutDefaults.BOTTOM_GAP_DP,
) = HomeMetrics(
    heightDp = heightDp,
    topInsetDp = topInsetDp,
    bottomInsetDp = bottomInsetDp,
    fontScale = fontScale,
    rowHeightDp = HomeLayoutDefaults.ROW_HEIGHT_DP,
    headerBlockHeightDp = HomeLayoutDefaults.HEADER_BLOCK_HEIGHT_DP,
    bottomGapDp = bottomGapDp,
)

/** What Home draws: how many rows, and the bottom gap actually applied under them. */
data class HomeFit(val rows: Int, val gapDp: Float)

object FavoritesCapacity {
    /**
     * How many favorites fit without scrolling. Both the row pitch and the header block are text,
     * so both are multiplied by [HomeMetrics.fontScale]; the insets and the bottom gap are fixed dp.
     * Never negative.
     */
    fun max(m: HomeMetrics): Int {
        val available = m.heightDp - m.topInsetDp - m.bottomInsetDp -
            m.headerBlockHeightDp * m.fontScale - m.bottomGapDp
        val pitch = m.rowHeightDp * m.fontScale
        if (pitch <= 0f || available <= 0f) return 0
        return floor(available / pitch).toInt().coerceAtLeast(0)
    }

    /**
     * The bottom gap Home actually applies: the stored one, cut to what still leaves the clock, date and event
     * line on screen. The gap is chosen on one screen and kept, so a rotation, a bigger font or another display
     * can leave it larger than the room, and the header is bottom-aligned, so it would be placed above the top.
     * Favorites already give way through [max]; this keeps the header from being pushed out as well.
     */
    fun clampGapDp(m: HomeMetrics): Float {
        val room = m.heightDp - m.topInsetDp - m.bottomInsetDp - m.headerBlockHeightDp * m.fontScale
        return m.bottomGapDp.coerceIn(0f, room.coerceAtLeast(0f))
    }

    /**
     * The largest bottom gap (dp) at which [favoriteCount] favorites, and the clock above them, still
     * all fit on the screen: the inverse of [max], so the two cannot disagree. An empty Home shows one
     * hint row, so it counts as one. Never negative: if they do not fit even with no gap, there is none.
     */
    fun maxGapDp(m: HomeMetrics, favoriteCount: Int): Float {
        val rows = maxOf(favoriteCount, 1)
        val room = m.heightDp - m.topInsetDp - m.bottomInsetDp - m.headerBlockHeightDp * m.fontScale -
            rows * m.rowHeightDp * m.fontScale
        return room.coerceAtLeast(0f)
    }

    /** The most rows any gap could allow: the capacity with no bottom gap. Home shrinks the gap before it drops a row. */
    fun maxRows(m: HomeMetrics): Int = max(m.copy(bottomGapDp = 0f))

    /**
     * Rows and gap for [visibleCount] favorites. The stored gap gives way before a row does: rows are cut only
     * past [maxRows], and the gap is the stored one cut to what those rows leave, so it never grows past the setting.
     */
    fun fit(m: HomeMetrics, visibleCount: Int): HomeFit {
        val rows = minOf(visibleCount, maxRows(m))
        return HomeFit(rows, minOf(clampGapDp(m), maxGapDp(m, rows)))
    }
}
