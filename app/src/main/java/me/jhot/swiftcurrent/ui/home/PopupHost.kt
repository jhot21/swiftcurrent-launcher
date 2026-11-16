package me.jhot.swiftcurrent.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.DotRow
import me.jhot.swiftcurrent.ui.design.RowBounds
import me.jhot.swiftcurrent.ui.design.SheetHandle
import me.jhot.swiftcurrent.ui.design.Tokens
import me.jhot.swiftcurrent.ui.design.dragSheet
import me.jhot.swiftcurrent.ui.design.rememberSheetDrag
import me.jhot.swiftcurrent.ui.design.sheetOffset
import me.jhot.swiftcurrent.ui.design.swipeRight
import kotlin.math.roundToInt

data class ShortcutRowModel(val id: String, val label: String)

/** The package part of a component name such as `pkg.a/.Main`. */
private fun packageOf(entry: Entry): String? = (entry.target as? Target.App)?.component?.substringBefore('/')

/** One tappable line of the sheet: an entry (an app, a folder) or one of the app's own shortcuts. */
private class PopupItem(val label: String, val entry: Entry?, val shortcut: ShortcutRowModel?)

/**
 * Bottom sheet for an item's pop-up: a header (the item's label and, for a favorite, an Edit
 * button) over a rule, then its pop-up rows in stored order, each with the same dot as a Home row.
 * An entry with no custom pop-up shows only its app's own shortcuts. An [PopupRow.OwnShortcuts] row
 * that is not in the stored list is not drawn, which is how the owner hides it. With nothing to
 * list the sheet says so instead of showing a bare header.
 *
 * [anchor] is the bounds, in root coordinates, of the item the sheet came from (the row that was
 * swiped). The sheet lines its first line up with that item, so the content seems to rise from it.
 * Every line shows: when they would not fit below the anchor the sheet grows upward, and when they
 * would not fit on screen it takes the whole height and scrolls. Without an anchor it sits at the bottom.
 *
 * An app in the sheet can be swiped right to open its own sheet over this one ([onEntrySwipe]).
 * Tapping outside, Back, or dragging the header down all call [onDismiss].
 */
@Composable
fun PopupHost(
    entry: Entry,
    onDismiss: () -> Unit,
    ownShortcuts: (app: String) -> List<ShortcutRowModel> = { emptyList() },
    // Null names an entry by its custom label, else by what it is; never by its raw id.
    labelOf: ((Entry) -> String)? = null,
    // The tapped line's bounds, so a folder opened from it can line up with it in turn.
    onEntry: (Entry, Rect) -> Unit = { _, _ -> },
    onEntrySwipe: (Entry, Rect) -> Unit = { _, _ -> },
    onShortcut: (ShortcutRowModel) -> Unit = {},
    onEdit: (() -> Unit)? = null,
    anchor: Rect? = null,
) {
    BackHandler(onBack = onDismiss)
    val resources = LocalResources.current
    val label: (Entry) -> String = labelOf ?: { e -> e.customLabel ?: resources.getString(neutralLabelRes(e)) }
    val rows = entry.popup?.rows ?: packageOf(entry)?.let { listOf(PopupRow.OwnShortcuts(it)) } ?: emptyList()
    val density = LocalDensity.current
    val topPx = WindowInsets.statusBars.getTop(density) + with(density) { Tokens.popupTopOffset.roundToPx() / 2 }
    val navInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    // Resolved here so an empty sheet can be told apart from one that has rows.
    val lines = rows.mapIndexed { index, row ->
        index to when (row) {
            is PopupRow.Item -> listOf(PopupItem(label(row.entry), row.entry, null))
            is PopupRow.OwnShortcuts -> ownShortcuts(row.app).map { s -> PopupItem(s.label, null, s) }
        }
    }
    val drag = rememberSheetDrag(onDismiss)
    Box(
        Modifier.fillMaxSize().testTag("popup-scrim").background(Tokens.popupScrim.copy(alpha = Tokens.popupScrim.alpha * drag.scrimAlpha))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        SheetLayout(
            modifier = Modifier.sheetOffset(drag),
            anchorTop = anchor?.top,
            topPx = topPx,
            background = {
                // Swallow taps so touches on the sheet never reach the scrim.
                Box(
                    Modifier.fillMaxSize().testTag("popup-sheet").onSizeChanged { drag.height = it.height.toFloat() }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                        .background(Tokens.sheet, Tokens.sheetShape),
                )
            },
            header = {
                Column(Modifier.dragSheet(drag)) {
                    SheetHandle()
                    Row(
                        Modifier.fillMaxWidth().testTag("popup-header"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            label(entry), AppType.heading,
                            Modifier.weight(1f)
                                .padding(horizontal = Tokens.popupRowPaddingH, vertical = Tokens.popupRowPaddingV),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (onEdit != null) {
                            AppText(
                                stringResource(R.string.popup_edit), AppType.body,
                                Modifier.testTag("popup-edit").clickable(onClick = onEdit)
                                    .padding(horizontal = Tokens.popupRowPaddingH, vertical = Tokens.popupRowPaddingV),
                                color = Tokens.dimText,
                            )
                        }
                    }
                    Box(Modifier.padding(horizontal = Tokens.popupRowPaddingH).fillMaxWidth().height(1.dp).background(Tokens.popupRule))
                }
            },
            body = {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = navInset + Tokens.popupCardPadding)) {
                    if (lines.all { it.second.isEmpty() }) {
                        AppText(
                            stringResource(R.string.popup_empty), AppType.body,
                            Modifier.fillMaxWidth().testTag("popup-empty")
                                .padding(horizontal = Tokens.popupRowPaddingH, vertical = Tokens.popupRowPaddingV),
                            color = Tokens.dimText,
                        )
                    }
                    var flat = 0
                    lines.forEach { (index, items) ->
                        Column(Modifier.testTag("popup-row-$index")) {
                            items.forEach { item ->
                                PopupLine(
                                    item.label, Modifier.testTag("popup-line-${flat++}"),
                                    onClick = { bounds -> item.entry?.let { onEntry(it, bounds) } ?: item.shortcut?.let(onShortcut) },
                                    onSwipe = if (item.entry?.target is Target.App) { bounds -> onEntrySwipe(item.entry, bounds) } else null,
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

/**
 * Lays out the sheet over the whole screen, reaching the bottom edge. Its top is the anchor's top
 * minus the header, so the first line lands on the anchor; if the lines would then run past the
 * bottom the sheet moves up, never below the status bar, and past that the body scrolls.
 */
@Composable
private fun SheetLayout(
    modifier: Modifier,
    anchorTop: Float?,
    topPx: Int,
    background: @Composable () -> Unit,
    header: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    Layout(
        content = { background(); header(); body() },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val head = measurables[1].measure(Constraints(maxWidth = width))
        val room = (height - topPx - head.height).coerceAtLeast(0)
        val body = measurables[2].measure(Constraints(maxWidth = width, maxHeight = room))
        val content = head.height + body.height
        val lowest = maxOf(topPx, height - content)
        val wanted = if (anchorTop == null) lowest else anchorTop.roundToInt() - head.height
        val top = wanted.coerceIn(topPx, lowest)
        val back = measurables[0].measure(Constraints.fixed(width, height - top))
        layout(width, height) {
            back.place(0, top)
            head.place(0, top)
            body.place(0, top + head.height)
        }
    }
}

@Composable
private fun PopupLine(label: String, modifier: Modifier, onClick: (Rect) -> Unit, onSwipe: ((Rect) -> Unit)?) {
    val bounds = remember { RowBounds() }
    DotRow(
        label,
        modifier.fillMaxWidth()
            .onGloballyPositioned(bounds::track)
            .clickable { onClick(bounds.now()) }
            .then(if (onSwipe != null) Modifier.swipeRight { onSwipe(bounds.now()) } else Modifier)
            .padding(horizontal = Tokens.popupRowPaddingH, vertical = Tokens.popupRowPaddingV),
        dotGap = Tokens.popupDotGap,
        ellipsize = false,
    )
}
