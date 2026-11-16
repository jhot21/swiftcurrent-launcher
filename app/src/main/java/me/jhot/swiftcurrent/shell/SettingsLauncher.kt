package me.jhot.swiftcurrent.shell

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.core.orElse
import me.jhot.swiftcurrent.search.LaunchOutcome

/** This app's own page in system settings, where its permissions are granted. */
internal fun appDetailsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

/**
 * Opens a system screen off the main thread. A refusal is shown as a message, and an exception is logged:
 * neither may close the screen the owner is on.
 */
@Composable
internal fun rememberSettingsLauncher(
    deps: LauncherDependencies,
    io: CoroutineDispatcher,
    onMessage: (ShellMessage) -> Unit,
): (Intent) -> Unit {
    val scope = rememberCoroutineScope()
    val message by rememberUpdatedState(onMessage)
    return remember(deps, io, scope) {
        { intent ->
            scope.launch {
                val out = orElse(null) {
                    try {
                        withContext(io) { deps.startActivity(intent) }
                    } catch (e: Exception) {
                        Log.w("SettingsLauncher", "could not open a settings screen", e)
                        throw e
                    }
                }
                if (out is LaunchOutcome.Failed) message(ShellMessage.LaunchFailed(out.message))
            }
        }
    }
}
