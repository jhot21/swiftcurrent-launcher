package me.jhot.swiftcurrent.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.DotRow
import me.jhot.swiftcurrent.ui.design.Tokens

/** [archived]: the app is archived by the system, so a tap downloads it again; Home marks it. */
data class HomeRow(val entry: Entry, val label: String, val missing: Boolean, val archived: Boolean = false)

data class HomeUiState(
    val clock: String,
    val date: String,
    val nextEvent: String?,
    val favorites: List<HomeRow>,
    /**
     * Whether any favorite is stored at all. [favorites] is already filtered by conditions, so it cannot
     * tell "nothing set up" (show the hint) from "everything is hidden right now". Required so no caller
     * can fall back to `favorites.isNotEmpty()`.
     */
    val anyStored: Boolean,
    /** The end of [nextEvent] (" · today +1") that must stay visible when the title is cut. Empty: the whole line truncates. */
    val nextEventTail: String = "",
)

/**
 * Favorites are anchored to the bottom and the clock block sits right above them, so everything
 * above shifts up as the count changes. Rows whose app is gone ([HomeRow.missing]) are skipped
 * before the capacity cut, so they do not use up a slot and a later favorite may take it; the
 * editor is where they are flagged. [insets] feed both the capacity and the padding, so they
 * cannot be counted twice; the host must be edge-to-edge for the real system bars to apply.
 * Date and event are one line each so the header height stays within HEADER_BLOCK_HEIGHT_DP. Rows past capacity are not drawn but stay in the saved data.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onClock: () -> Unit,
    onAgenda: () -> Unit,
    onItem: (Entry) -> Unit,
    onAddFavorites: () -> Unit = {},
    insets: WindowInsets = WindowInsets.systemBars,
    // How many favorites fit, reported for the spacing preview (HomeSpacingPage), which says when a row drops off.
    onCapacity: (Int) -> Unit = {},
    // Space under the last favorite beyond the system bar, in dp: the owner's setting.
    bottomGapDp: Float = HomeLayoutDefaults.BOTTOM_GAP_DP,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Tokens.black)) {
        val topInset = with(density) { insets.getTop(density).toDp() }
        val bottomInset = with(density) { insets.getBottom(density).toDp() }
        val metrics = homeMetrics(maxHeight.value, topInset.value, bottomInset.value, density.fontScale, bottomGapDp)
        // The stored gap shrinks before a favorite is dropped; see FavoritesCapacity.fit.
        val visible = state.favorites.filter { !it.missing }
        val fit = FavoritesCapacity.fit(metrics, visible.size)
        // Same spec as the rows, and a trailing spacer rather than padding: the gap is measured last, so a frame in which
        // it runs ahead of the rows gives way first and never squashes a row or pushes the header out.
        val gapDp by animateDpAsState(fit.gapDp.dp, tween(HOME_ROW_MS), label = "home-gap")
        val capacity = FavoritesCapacity.maxRows(metrics)
        androidx.compose.runtime.LaunchedEffect(capacity) { onCapacity(capacity) }
        val shown = visible.take(fit.rows)
        val rowHeight = (HomeLayoutDefaults.ROW_HEIGHT_DP * density.fontScale).dp

        Column(
            Modifier.fillMaxSize()
                .padding(top = topInset, bottom = bottomInset),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.homeGutter)) {
                AppText(state.clock, AppType.clock, Modifier.testTag("clock").clickable(onClick = onClock))
                AppText(
                    state.date, AppType.body, Modifier.testTag("date").clickable(onClick = onAgenda),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (state.nextEvent != null) {
                    // Only the title gives way, so the time and the "+N" stay readable, as in Niagara.
                    val tail = state.nextEventTail
                    Row(
                        Modifier.testTag("event").padding(top = 4.dp).clickable(onClick = onAgenda)
                            // One text with the whole line, so it reads and matches as before the split.
                            .clearAndSetSemantics { text = AnnotatedString(state.nextEvent) },
                    ) {
                        AppText(
                            state.nextEvent.removeSuffix(tail), AppType.body,
                            color = Tokens.dimText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (tail.isNotEmpty()) {
                            AppText(tail, AppType.body, Modifier.testTag("event-tail"), color = Tokens.dimText, maxLines = 1)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("favorites")) {
                // One hint instead of an empty gap; it is no favorite, so it never counts toward capacity.
                AnimatedRows(items = if (state.anyStored) emptyList() else listOf(Unit), key = { "hint" }) { _, _ ->
                    DotRow(
                        stringResource(R.string.home_add_favorites),
                        Modifier.fillMaxWidth().height(rowHeight).testTag("empty-favorites-hint")
                            .clickable(onClick = onAddFavorites)
                            .padding(start = Tokens.homeGutter + Tokens.rowDotInset, end = Tokens.homeGutter),
                        ellipsize = false,
                    )
                }
                AnimatedRows(items = shown, key = { it.entry.id }) { row, leaving ->
                    DotRow(
                        row.label,
                        Modifier.fillMaxWidth().height(rowHeight).testTag("favorite-${row.entry.id}")
                            // A row on its way out can no longer be tapped or used as a gesture target.
                            .then(if (leaving) Modifier else Modifier.homeGestureTarget(row.entry).clickable { onItem(row.entry) })
                            .padding(start = Tokens.homeGutter + Tokens.rowDotInset, end = Tokens.homeGutter),
                        archived = row.archived,
                        archivedTag = "archived-${row.entry.id}",
                    )
                }
            }
            Spacer(Modifier.height(gapDp))
        }
    }
}
