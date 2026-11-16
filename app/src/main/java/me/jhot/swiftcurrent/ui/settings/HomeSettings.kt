package me.jhot.swiftcurrent.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.HomeSpacing
import me.jhot.swiftcurrent.ui.design.SettingsPage

/**
 * Home: the entry to the favorites editor (the only way to start one with no favorites) and the
 * agenda's calendar filter. Without calendar access the filter is replaced by a prompt to grant it.
 */
@Composable
fun HomeSettings(
    calendarGranted: Boolean,
    onFavorites: () -> Unit,
    onCalendars: () -> Unit,
    onGrantCalendar: () -> Unit,
    gapDp: Int = HomeSpacing.DEFAULT,
    onSpacing: () -> Unit = {},
) {
    val favorites = stringResource(R.string.settings_home_favorites)
    val favoritesDesc = stringResource(R.string.settings_home_favorites_desc)
    val spacing = stringResource(R.string.settings_home_spacing)
    val spacingDesc = stringResource(R.string.settings_home_spacing_desc, gapDp)
    val agendaSection = stringResource(R.string.settings_home_agenda_section)
    val calendars = stringResource(R.string.settings_home_calendars)
    val calendarsDesc = stringResource(R.string.settings_home_calendars_desc)
    val access = stringResource(R.string.settings_home_calendar_access)
    val accessDesc = stringResource(R.string.settings_home_calendar_access_desc)
    Box(Modifier.fillMaxSize().testTag("settings-home")) {
        SettingsPage(title = stringResource(R.string.settings_home)) {
            row(icon = {}, title = favorites, description = favoritesDesc, onClick = onFavorites)
            row(icon = {}, title = spacing, description = spacingDesc, onClick = onSpacing, modifierTag = "row-spacing")
            section(agendaSection)
            if (calendarGranted) {
                row(icon = {}, title = calendars, description = calendarsDesc, onClick = onCalendars)
            } else {
                row(icon = {}, title = access, description = accessDesc, onClick = onGrantCalendar)
            }
        }
    }
}
