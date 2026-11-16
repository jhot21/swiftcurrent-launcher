package me.jhot.swiftcurrent.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.Tokens

/** A short transient notice the shell shows over any screen. */
sealed interface ShellMessage {
    /** The lock accessibility service is off; the bar offers a link to its settings. */
    data object LockNeedsService : ShellMessage

    /** The lock service is on but the system refused the lock; nothing to enable, so no action. */
    data object LockFailed : ShellMessage

    /** Calendar access is missing, so the agenda is hidden; the bar offers this app's settings to grant it. */
    data object CalendarNeedsAccess : ShellMessage

    data class LaunchFailed(val text: String) : ShellMessage

    /** A plain notice (a confirmation or an error from a settings action) with no action. */
    data class Notice(val text: String) : ShellMessage
}

internal const val MESSAGE_VISIBLE_MS = 5_000L

/** The one message bar: tap anywhere to dismiss, or wait. An optional action sits beside the text. */
@Composable
fun ShellMessageBar(
    message: ShellMessage,
    serial: Int,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(message, serial) {
        delay(MESSAGE_VISIBLE_MS)
        onDismiss()
    }
    val text = when (message) {
        ShellMessage.LockNeedsService -> stringResource(R.string.lock_service_disabled)
        ShellMessage.LockFailed -> stringResource(R.string.lock_failed)
        ShellMessage.CalendarNeedsAccess -> stringResource(R.string.calendar_access_needed)
        is ShellMessage.LaunchFailed -> message.text
        is ShellMessage.Notice -> message.text
    }
    val actionLabel = when (message) {
        ShellMessage.LockNeedsService -> stringResource(R.string.lock_service_open_settings)
        ShellMessage.CalendarNeedsAccess -> stringResource(R.string.calendar_access_grant)
        ShellMessage.LockFailed, is ShellMessage.LaunchFailed, is ShellMessage.Notice -> null
    }
    Row(
        modifier
            .testTag("shell-message")
            .fillMaxWidth()
            .background(Tokens.card, Tokens.cardShape)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(text, AppType.caption, Modifier.weight(1f))
        if (actionLabel != null) {
            AppText(
                actionLabel, AppType.caption,
                Modifier.testTag("shell-message-action").clickable(onClick = onAction).padding(8.dp),
                color = Tokens.dot,
            )
        }
    }
}
