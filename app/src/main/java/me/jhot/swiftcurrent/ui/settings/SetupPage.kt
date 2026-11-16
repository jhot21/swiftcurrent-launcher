package me.jhot.swiftcurrent.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.CheckBox
import me.jhot.swiftcurrent.ui.design.SettingsPage
import me.jhot.swiftcurrent.ui.design.Tokens

/**
 * First-run setup and, later, the Setup row in Advanced: the four steps as settings rows, each with
 * an action until it is done. Nothing here is required, so [onFinish] is always available: "Skip"
 * while a step is missing, "Done" once every step is. [blocked] lists permission steps the system
 * will no longer ask for, whose action opens this app's details instead.
 */
@Composable
fun SetupPage(
    steps: List<SetupStep>,
    blocked: Set<StepKind>,
    onAction: (StepKind) -> Unit,
    onFinish: () -> Unit,
) {
    val done = SetupChecklist.doneCount(steps)
    val doneLabel = stringResource(R.string.setup_step_done)
    val progress = stringResource(R.string.setup_progress, done, steps.size)
    val titles = steps.map { stringResource(it.kind.title) }
    val descriptions = steps.map { stringResource(it.kind.description) }
    val open = stringResource(R.string.setup_action_open)
    val allow = stringResource(R.string.setup_action_allow)
    val finish = stringResource(if (done == steps.size) R.string.setup_finish else R.string.setup_skip)
    Box(Modifier.fillMaxSize().testTag("setup-page")) {
        SettingsPage(title = stringResource(R.string.setup_title)) {
            section(progress)
            steps.forEachIndexed { index, step ->
                val kind = step.kind
                val action = if (kind in blocked || kind.opensSettings) open else allow
                row(
                    icon = { Chip(index + 1) },
                    title = titles[index],
                    description = descriptions[index],
                    trailing = {
                        if (step.done) CheckBox(checked = true, label = doneLabel, tag = "setup-done-$kind") {}
                        else ActionButton(
                            label = action,
                            // "Allow" three times over is unreadable to TalkBack; each button names its step.
                            description = stringResource(R.string.setup_action_for, action, titles[index]),
                            tag = "setup-action-$kind",
                        ) { onAction(kind) }
                    },
                )
            }
            row(icon = {}, title = finish, onClick = onFinish)
        }
    }
}

private val StepKind.opensSettings get() = this == StepKind.DefaultLauncher || this == StepKind.Accessibility

private val StepKind.title get() = when (this) {
    StepKind.DefaultLauncher -> R.string.setup_default_title
    StepKind.Contacts -> R.string.setup_contacts_title
    StepKind.Calendar -> R.string.setup_calendar_title
    StepKind.Accessibility -> R.string.setup_lock_title
}

private val StepKind.description get() = when (this) {
    StepKind.DefaultLauncher -> R.string.setup_default_desc
    StepKind.Contacts -> R.string.setup_contacts_desc
    StepKind.Calendar -> R.string.setup_calendar_desc
    StepKind.Accessibility -> R.string.setup_lock_desc
}

@Composable
private fun Chip(number: Int) {
    Box(Modifier.size(32.dp).background(Tokens.card, CircleShape), contentAlignment = Alignment.Center) {
        AppText(number.toString(), AppType.label)
    }
}

/** The pill is small; the touch target around it is the 48dp minimum, so it is not hard to hit. */
@Composable
private fun ActionButton(label: String, description: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.testTag(tag).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            label,
            AppType.label,
            Modifier.background(Tokens.card, Tokens.pillShape).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
