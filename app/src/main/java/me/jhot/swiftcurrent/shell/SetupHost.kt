package me.jhot.swiftcurrent.shell

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.lock.accessibilitySettingsIntent
import me.jhot.swiftcurrent.ui.settings.SetupChecklist
import me.jhot.swiftcurrent.ui.settings.SetupPage
import me.jhot.swiftcurrent.ui.settings.SetupStep
import me.jhot.swiftcurrent.ui.settings.StepKind

private val PERMISSIONS = mapOf(
    StepKind.Contacts to Manifest.permission.READ_CONTACTS,
    StepKind.Calendar to Manifest.permission.READ_CALENDAR,
)

/**
 * First-run setup wired to the device. The launcher never makes itself the default and never shows the
 * role dialog: the default-launcher step only opens the system's home-app settings, where the owner
 * chooses. Step state is read again whenever the launcher is shown and after each permission answer.
 * [onFinish] leaves the page (Back does the same); the caller decides whether that marks setup seen.
 */
@Composable
internal fun SetupHost(
    deps: LauncherDependencies,
    io: CoroutineDispatcher,
    permissions: PermissionRequesterFactory,
    onMessage: (ShellMessage) -> Unit,
    onFinish: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val packageName = LocalContext.current.packageName
    var steps by remember { mutableStateOf(StepKind.entries.map { SetupStep(it, false) }) }
    // Permissions the system will no longer ask for: their action opens this app's details instead.
    var blocked by remember { mutableStateOf(emptySet<StepKind>()) }

    suspend fun read() {
        try {
            steps = withContext(io) { SetupChecklist(deps.setupEnv).steps() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep what was shown: a failed read must not close setup.
        }
    }

    val requester = remember { arrayOfNulls<PermissionRequester>(1) }
    requester[0] = permissions { permission, granted ->
        val kind = PERMISSIONS.entries.firstOrNull { it.value == permission }?.key
        if (kind != null) {
            // Denied and the system will not explain itself again: it also will not show the dialog again.
            blocked = if (!granted && requester[0]?.shouldShowRationale(permission) == false) blocked + kind else blocked - kind
        }
        scope.launch { read() }
    }

    WhileStarted(deps) { read() }

    val start = rememberSettingsLauncher(deps, io, onMessage)

    BackHandler(onBack = onFinish)
    SetupPage(
        steps = steps,
        blocked = blocked,
        onAction = { kind ->
            when (kind) {
                StepKind.DefaultLauncher -> start(Intent(Settings.ACTION_HOME_SETTINGS))
                StepKind.Accessibility -> start(accessibilitySettingsIntent())
                StepKind.Contacts, StepKind.Calendar ->
                    if (kind in blocked) start(appDetailsIntent(packageName))
                    else requester[0]?.request(PERMISSIONS.getValue(kind))
            }
        },
        onFinish = onFinish,
    )
}
