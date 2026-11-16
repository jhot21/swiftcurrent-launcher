package me.jhot.swiftcurrent.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.HapticStrength
import me.jhot.swiftcurrent.ui.design.SettingsPage
import me.jhot.swiftcurrent.ui.design.StepSlider
import me.jhot.swiftcurrent.ui.design.Toggle

/**
 * Gestures: first, a prompt to turn on the accessibility service while quick lock cannot work (it
 * has no switch of its own, and the prompt is hidden once it is on), then the haptics switch, and a
 * read-only list of the gestures so they can be found.
 */
@Composable
fun GesturesSettings(
    lockServiceOn: Boolean,
    haptics: Boolean,
    onHaptics: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    scrubStrength: Int = HapticStrength.DEFAULT,
    onScrubStrength: (Int) -> Unit = {},
    gestureStrength: Int = HapticStrength.DEFAULT,
    onGestureStrength: (Int) -> Unit = {},
) {
    val lock = stringResource(R.string.settings_gestures_lock)
    val lockOffDesc = stringResource(R.string.settings_gestures_lock_off)
    val feedback = stringResource(R.string.settings_gestures_feedback_section)
    val hapticsTitle = stringResource(R.string.settings_gestures_haptics)
    val hapticsDesc = stringResource(R.string.settings_gestures_haptics_desc)
    val hapticsToggle = stringResource(R.string.settings_gestures_haptics_toggle)
    val scrubTitle = stringResource(R.string.settings_gestures_scrub_haptic)
    val gestureTitle = stringResource(R.string.settings_gestures_gesture_haptic)
    val scrubDesc = stringResource(R.string.settings_gestures_haptic_level_desc, strengthText(scrubStrength))
    val gestureDesc = stringResource(R.string.settings_gestures_gesture_haptic_desc, strengthText(gestureStrength))
    val guide = stringResource(R.string.settings_gestures_guide_section)
    val gestures = listOf(
        stringResource(R.string.settings_gesture_search) to stringResource(R.string.settings_gesture_search_desc),
        stringResource(R.string.settings_gesture_list) to stringResource(R.string.settings_gesture_list_desc),
        stringResource(R.string.settings_gesture_popup) to stringResource(R.string.settings_gesture_popup_desc),
        stringResource(R.string.settings_gesture_actions) to stringResource(R.string.settings_gesture_actions_desc),
        stringResource(R.string.settings_gesture_lock) to stringResource(R.string.settings_gesture_lock_desc),
    )
    Box(Modifier.fillMaxSize().testTag("settings-gestures")) {
        SettingsPage(title = stringResource(R.string.settings_gestures)) {
            // Only while the service is off: once it is on, the gesture guide below already says what it does.
            if (!lockServiceOn) {
                row(icon = {}, title = lock, description = lockOffDesc, onClick = onOpenAccessibility)
            }
            section(feedback)
            row(
                icon = {}, title = hapticsTitle, description = hapticsDesc,
                trailing = { Toggle(haptics, hapticsToggle, "toggle-haptics", onHaptics) },
            )
            // Sliding plays the new strength, so it can be felt at once; the far left is no tick at all.
            row(
                icon = {}, title = scrubTitle, description = scrubDesc,
                trailing = if (haptics) ({ StrengthSlider(scrubStrength, scrubTitle, "haptic-scrub", onScrubStrength) }) else null,
            )
            row(
                icon = {}, title = gestureTitle, description = gestureDesc,
                trailing = if (haptics) ({ StrengthSlider(gestureStrength, gestureTitle, "haptic-gesture", onGestureStrength) }) else null,
            )
            section(guide)
            gestures.forEach { (name, what) -> row(icon = {}, title = name, description = what) }
        }
    }
}

@Composable
private fun StrengthSlider(strength: Int, label: String, tag: String, onStrength: (Int) -> Unit) {
    Box(Modifier.width(140.dp)) {
        StepSlider(strength, HapticStrength.MAX, HapticStrength.STEP, label, tag, onStrength)
    }
}

@Composable
private fun strengthText(strength: Int): String =
    if (strength <= HapticStrength.MIN) stringResource(R.string.settings_haptic_off)
    else stringResource(R.string.settings_haptic_percent, strength)
