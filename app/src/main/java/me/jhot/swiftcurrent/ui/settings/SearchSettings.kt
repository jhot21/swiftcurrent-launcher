package me.jhot.swiftcurrent.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.HiddenContactAction
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.SettingsPage
import me.jhot.swiftcurrent.ui.design.SheetCard
import me.jhot.swiftcurrent.ui.design.SheetScaffold
import me.jhot.swiftcurrent.ui.design.Toggle

/**
 * Search: first, a prompt to make this app the default home app when that is missing, since shortcuts
 * cannot be searched without it ([shortcutsWork] is null until known, and the prompt is hidden once it
 * is met); then the per-app shortcut switches.
 * Then which calendars events are searched in (or a prompt for calendar access), how results are ranked, and clearing the pick history, confirmed first because it cannot be undone.
 */
@Composable
fun SearchSettings(
    shortcutsWork: Boolean?,
    onShortcuts: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    appsFirst: Boolean,
    contactsFirst: Boolean,
    learnFromPicks: Boolean,
    onAppsFirst: (Boolean) -> Unit,
    onContactsFirst: (Boolean) -> Unit,
    onLearnFromPicks: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    hiddenCount: Int = 0,
    onHiddenMethods: () -> Unit = {},
    calendarGranted: Boolean = true,
    onCalendars: () -> Unit = {},
    onGrantCalendar: () -> Unit = {},
) {
    var confirming by remember { mutableStateOf(false) }
    val shortcuts = stringResource(R.string.settings_search_shortcuts)
    val shortcutsDesc = stringResource(R.string.settings_search_shortcuts_desc)
    val needsHome = stringResource(R.string.settings_search_status_needs_home)
    val needsHomeAction = stringResource(R.string.settings_search_status_needs_home_action)
    val hiddenMethods = stringResource(R.string.settings_search_hidden_methods)
    val hiddenMethodsDesc = stringResource(R.string.settings_search_hidden_methods_count, hiddenCount)
    val calendars = stringResource(R.string.settings_search_calendars)
    val calendarsDesc = stringResource(R.string.settings_search_calendars_desc)
    val calendarAccess = stringResource(R.string.settings_search_calendar_access)
    val calendarAccessDesc = stringResource(R.string.settings_search_calendar_access_desc)
    val ranking = stringResource(R.string.settings_search_ranking_section)
    val apps = stringResource(R.string.settings_search_apps_first)
    val appsDesc = stringResource(R.string.settings_search_apps_first_desc)
    val appsToggle = stringResource(R.string.settings_search_apps_first_toggle)
    val contacts = stringResource(R.string.settings_search_contacts_first)
    val contactsDesc = stringResource(R.string.settings_search_contacts_first_desc)
    val contactsToggle = stringResource(R.string.settings_search_contacts_first_toggle)
    val learn = stringResource(R.string.settings_search_learn)
    val learnDesc = stringResource(R.string.settings_search_learn_desc)
    val learnToggle = stringResource(R.string.settings_search_learn_toggle)
    val clear = stringResource(R.string.settings_search_clear)
    val clearDesc = stringResource(R.string.settings_search_clear_desc)
    Box(Modifier.fillMaxSize().testTag("settings-search")) {
        SettingsPage(title = stringResource(R.string.settings_search)) {
            // Only while setup is missing: once shortcuts work there is nothing to act on.
            if (shortcutsWork == false) {
                row(icon = {}, title = needsHome, description = needsHomeAction, onClick = onOpenHomeSettings)
            }
            row(icon = {}, title = shortcuts, description = shortcutsDesc, onClick = onShortcuts)
            row(icon = {}, title = hiddenMethods, description = hiddenMethodsDesc, onClick = onHiddenMethods, modifierTag = "hidden-methods-row")
            if (calendarGranted) {
                row(icon = {}, title = calendars, description = calendarsDesc, onClick = onCalendars, modifierTag = "row-search-calendars")
            } else {
                row(icon = {}, title = calendarAccess, description = calendarAccessDesc, onClick = onGrantCalendar)
            }
            section(ranking)
            row(
                icon = {}, title = apps, description = appsDesc,
                trailing = { Toggle(appsFirst, appsToggle, "toggle-apps-first", onAppsFirst) },
            )
            row(
                icon = {}, title = contacts, description = contactsDesc,
                trailing = { Toggle(contactsFirst, contactsToggle, "toggle-contacts-first", onContactsFirst) },
            )
            row(
                icon = {}, title = learn, description = learnDesc,
                trailing = { Toggle(learnFromPicks, learnToggle, "toggle-learn-from-picks", onLearnFromPicks) },
            )
            row(icon = {}, title = clear, description = clearDesc, onClick = { confirming = true })
        }
        if (confirming) {
            SheetScaffold(title = stringResource(R.string.settings_search_clear_confirm_title), onDismiss = { confirming = false }) {
                AppText(stringResource(R.string.settings_search_clear_confirm_body), AppType.body)
                SheetCard(icon = {}, title = stringResource(R.string.settings_search_clear_confirm_yes), onClick = { confirming = false; onClearHistory() })
                SheetCard(icon = {}, title = stringResource(R.string.settings_search_clear_confirm_no), onClick = { confirming = false })
            }
        }
    }
}

/** Every contact method the owner hid from search, each named as it read; a tap shows it again. */
@Composable
fun HiddenContactMethodsPage(items: List<HiddenContactAction>, onShow: (HiddenContactAction) -> Unit) {
    val none = stringResource(R.string.settings_hidden_methods_none)
    val again = stringResource(R.string.settings_hidden_methods_again)
    Box(Modifier.fillMaxSize().testTag("settings-hidden-methods")) {
        SettingsPage(title = stringResource(R.string.settings_search_hidden_methods)) {
            if (items.isEmpty()) row(icon = {}, title = none)
            items.forEach { h ->
                row(icon = {}, title = "${h.contactName}: ${h.label}", description = again, onClick = { onShow(h) }, modifierTag = "hidden-method-${h.contact}-${h.action}")
            }
        }
    }
}
