package me.jhot.swiftcurrent.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.agenda.CalendarFilter
import me.jhot.swiftcurrent.agenda.CalendarInfo
import me.jhot.swiftcurrent.ui.design.SettingsPage
import me.jhot.swiftcurrent.ui.design.Toggle

/**
 * Which calendars the agenda shows, on the shared settings layout. [enabled] is the stored setting,
 * where null means every calendar; [onToggle] gets the id, and the host applies it with
 * [CalendarFilter.apply] through the store.
 */
@Composable
fun CalendarFilterList(
    calendars: List<CalendarInfo>,
    enabled: Set<Long>?,
    onToggle: (Long) -> Unit,
) {
    // The page's content lambda is not composable, so resolve the words first.
    val sectionLabel = stringResource(R.string.calendar_filter_section)
    val none = stringResource(R.string.calendar_filter_none)
    SettingsPage(title = stringResource(R.string.calendar_filter_title)) {
        section(sectionLabel)
        calendars.forEach { c ->
            row(
                icon = { Box(Modifier.size(14.dp).background(Color(c.color or OPAQUE), CircleShape)) },
                title = c.name,
                description = c.accountName.ifEmpty { null },
                trailing = {
                    Toggle(
                        on = CalendarFilter.isEnabled(enabled, c.id),
                        label = stringResource(R.string.calendar_filter_toggle, c.name),
                        tag = "calendar-toggle-${c.id}",
                        onChange = { onToggle(c.id) },
                    )
                },
            )
        }
        if (calendars.isEmpty()) row(icon = {}, title = none)
    }
}

private const val OPAQUE = 0xFF000000.toInt()
