package me.jhot.swiftcurrent.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.ui.design.SettingsPage

/** The settings front page: the five topics, nothing else. */
@Composable
fun SettingsRoot(nav: SettingsNav) {
    val titles = mapOf(
        SettingsRoute.Home to stringResource(R.string.settings_home),
        SettingsRoute.Apps to stringResource(R.string.settings_apps),
        SettingsRoute.Search to stringResource(R.string.settings_search),
        SettingsRoute.Gestures to stringResource(R.string.settings_gestures),
        SettingsRoute.Advanced to stringResource(R.string.settings_advanced),
    )
    val descriptions = mapOf(
        SettingsRoute.Home to stringResource(R.string.settings_home_desc),
        SettingsRoute.Apps to stringResource(R.string.settings_apps_desc),
        SettingsRoute.Search to stringResource(R.string.settings_search_desc),
        SettingsRoute.Gestures to stringResource(R.string.settings_gestures_desc),
        SettingsRoute.Advanced to stringResource(R.string.settings_advanced_desc),
    )
    Box(Modifier.fillMaxSize().testTag("settings-root")) {
        SettingsPage(title = stringResource(R.string.settings_title)) {
            SETTINGS_TOPICS.forEach { topic ->
                row(icon = {}, title = titles.getValue(topic), description = descriptions.getValue(topic), onClick = { nav.open(topic) })
            }
        }
    }
}
