package me.jhot.swiftcurrent.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private const val SCRIM_ALPHA = 0.6f

/**
 * Bottom sheet with a scrim: large top radius, large light title, content below. Tapping the
 * scrim or pressing back calls [onDismiss], so closing always lands on the screen underneath.
 * The handle and title stay in place and only [content] scrolls, whatever its length.
 */
@Composable
fun SheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    /** A control at the end of the title row, such as an add button. */
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val drag = rememberSheetDrag(onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA * drag.scrimAlpha))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            // The keyboard shortens the room the sheet has, outside its own height cap, so a tall keyboard lifts the
            // whole sheet instead of eating the sheet's height. The scrim above stays full screen. It comes before the
            // navigation bar padding below, which then only pads what the keyboard has not already covered.
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .sheetOffset(drag)
                .onSizeChanged { drag.height = it.height.toFloat() }
                // Swallow taps so touches on the sheet itself never reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .background(Tokens.sheet, Tokens.sheetShape)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Dragging the handle or the title down closes the sheet, as a bottom sheet is expected to.
            Column(Modifier.dragSheet(drag)) {
                SheetHandle()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppText(title, AppType.title, Modifier.weight(1f).padding(top = 20.dp, bottom = 12.dp, start = 4.dp))
                    action?.invoke()
                }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** The small bar at the top of every bottom sheet: it says "this is a sheet" and that a drag down closes it. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(width = 40.dp, height = 4.dp).testTag("sheet-handle")
                .background(Tokens.dimText.copy(alpha = 0.5f), CircleShape),
        )
    }
}

/** A sheet row: dark-grey rounded card with a circular grey icon chip. */
@Composable
fun SheetCard(
    icon: (@Composable () -> Unit)?,
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Right-hand control, such as a switch. */
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Tokens.card, Tokens.cardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(44.dp).background(Tokens.dimText.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { icon() }
        }
        Column(Modifier.weight(1f)) {
            AppText(title, AppType.body)
            if (description != null) AppText(description, AppType.label, color = Tokens.dimText)
        }
        if (trailing != null) trailing()
    }
}
