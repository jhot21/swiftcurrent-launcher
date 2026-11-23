package me.jhot.swiftcurrent.ui.agenda

import android.text.format.DateFormat
import androidx.compose.foundation.BasicTooltipBox
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberBasicTooltipState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.agenda.AgendaDay
import me.jhot.swiftcurrent.agenda.AgendaEvent
import me.jhot.swiftcurrent.agenda.rememberAgendaFormatter
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.SheetScaffold
import me.jhot.swiftcurrent.ui.design.Tokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Your agenda": days with their events, a colored bar per calendar, the time range with how soon it
 * starts, the location, and a plus button in the header for a new event. Which calendars show is a setting, not
 * a control here. [now] and [zone] are passed in so the sheet shows exactly what the shell computed.
 */
@Composable
fun AgendaSheet(
    days: List<AgendaDay>,
    onNewEvent: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    onEvent: (AgendaEvent) -> Unit = {},
) {
    val formatter = rememberAgendaFormatter()
    val locale = LocalLocale.current.platformLocale
    val dateFormat = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
    }
    SheetScaffold(
        title = stringResource(R.string.agenda_title), onDismiss = onDismiss,
        action = { NewEventButton { onNewEvent(LocalDate.ofInstant(now, zone)) } },
    ) {
        Column(Modifier.testTag("agenda-sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (days.none { it.events.isNotEmpty() }) {
                AppText(stringResource(R.string.agenda_empty), AppType.body, Modifier.padding(start = 4.dp), color = Tokens.dimText)
            }
            days.forEach { day ->
                Row(
                    Modifier.padding(start = 4.dp, top = 12.dp).testTag("agenda-day-${day.date}"),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppText(dateFormat.format(day.date), AppType.body)
                    if (day.label != null) AppText(day.label, AppType.label, color = Tokens.dimText)
                }
                day.events.forEach { e ->
                    EventRow(e, formatter.title(e), formatter.detail(e, now, zone)) { onEvent(e) }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: AgendaEvent, title: String, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag("agenda-event").clickable(role = Role.Button, onClick = onClick).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier.padding(start = 4.dp).width(6.dp).fillMaxHeight()
                .background(Color(event.calendarColor or OPAQUE), RoundedCornerShape(3.dp)),
        )
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            AppText(title, AppType.body)
            if (detail != null) AppText(detail, AppType.label, color = Tokens.dimText)
            if (event.location != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinGlyph(stringResource(R.string.agenda_location))
                    AppText(event.location, AppType.label, color = Tokens.dimText)
                }
            }
        }
    }
}

/** The header's add button. A long press explains it, since the plus has no label of its own. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewEventButton(onClick: () -> Unit) {
    val label = stringResource(R.string.agenda_new_event)
    val edge = with(LocalDensity.current) { 16.dp.roundToPx() }
    BasicTooltipBox(
        positionProvider = remember(edge) { BelowAnchorPositionProvider(edge) },
        state = rememberBasicTooltipState(isPersistent = false),
        tooltip = {
            AppText(
                label, AppType.label,
                Modifier.background(Tokens.card, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).testTag("agenda-new-event")
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(18.dp)) {
                val w = 2.dp.toPx()
                drawLine(Tokens.text, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w)
                drawLine(Tokens.text, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w)
            }
        }
    }
}

/** Centers the tooltip under the button, kept inside the window. */
private class BelowAnchorPositionProvider(private val edge: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(edge, (windowSize.width - popupContentSize.width - edge).coerceAtLeast(edge))
        return IntOffset(x, anchorBounds.bottom)
    }
}

/** A map pin: a ring with a point below it. */
@Composable
private fun PinGlyph(description: String) {
    Canvas(Modifier.size(width = 14.dp, height = 18.dp).semantics { contentDescription = description }) {
        val w = Stroke(1.5.dp.toPx())
        val r = size.width / 2 - w.width
        drawCircle(Tokens.dimText, radius = r, center = Offset(size.width / 2, size.width / 2), style = w)
        drawLine(Tokens.dimText, Offset(size.width / 2 - r * 0.7f, size.width / 2 + r * 0.7f), Offset(size.width / 2, size.height), w.width)
        drawLine(Tokens.dimText, Offset(size.width / 2 + r * 0.7f, size.width / 2 + r * 0.7f), Offset(size.width / 2, size.height), w.width)
    }
}

// The provider's color is RGB in some rows; force the alpha so a color is never invisible.
private const val OPAQUE = 0xFF000000.toInt()
