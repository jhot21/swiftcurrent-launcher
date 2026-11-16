package me.jhot.swiftcurrent.shell

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.UserShortcut
import me.jhot.swiftcurrent.shortcuts.AddOutcome
import me.jhot.swiftcurrent.shortcuts.Creator
import me.jhot.swiftcurrent.shortcuts.RejectReason

/**
 * Returns a function that starts a create-shortcut activity and, when it returns a result, stores
 * it. [onAdded] gets the stored shortcut once the shell has taken the write, and is suspend so it
 * can name the app off the main thread; a refused result becomes a [ShellMessage]. The creator is
 * remembered across recreation (only its component, which is all the result needs), so a result
 * that arrives after the activity was rebuilt still lands.
 */
@Composable
internal fun rememberShortcutCreator(
    deps: LauncherDependencies,
    model: ShellData,
    io: CoroutineDispatcher,
    onMessage: (ShellMessage) -> Unit,
    onAdded: suspend (UserShortcut) -> Unit,
): (Creator) -> Unit {
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val onMessageNow by rememberUpdatedState(onMessage)
    val onAddedNow by rememberUpdatedState(onAdded)
    val unavailable = stringResource(R.string.launch_unavailable)
    val failed = stringResource(R.string.search_launch_failed)
    val noIntent = stringResource(R.string.shortcut_rejected_no_intent)
    val noName = stringResource(R.string.shortcut_rejected_no_name)
    val wrongTarget = stringResource(R.string.shortcut_rejected_wrong_target)
    val duplicate = stringResource(R.string.shortcut_rejected_duplicate)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val component = pending
        pending = null
        val data = result.data
        // Cancelled (Back out of the creator) is not an error: nothing to add and nothing to say.
        if (component == null || result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                when (val out = withContext(io) { deps.userShortcuts.addFromResult(Creator(component, "", ""), data, model::update) }) {
                    is AddOutcome.Added -> {
                        model.data.value.userShortcuts.firstOrNull { it.id == out.id }?.let { onAddedNow(it) }
                    }
                    AddOutcome.Duplicate -> onMessageNow(ShellMessage.LaunchFailed(duplicate))
                    is AddOutcome.Rejected -> onMessageNow(
                        ShellMessage.LaunchFailed(
                            when (out.reason) {
                                RejectReason.MissingIntent -> noIntent
                                RejectReason.MissingName -> noName
                                RejectReason.WrongTarget -> wrongTarget
                            },
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("ShortcutCreation", "adding the shortcut failed", e)
                onMessageNow(ShellMessage.LaunchFailed(failed))
            }
        }
    }

    return { creator ->
        val component = ComponentName.unflattenFromString(creator.component)
        if (component == null) {
            onMessageNow(ShellMessage.LaunchFailed(unavailable))
        } else {
            pending = creator.component
            try {
                launcher.launch(Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(component))
            } catch (e: ActivityNotFoundException) {
                pending = null
                onMessageNow(ShellMessage.LaunchFailed(unavailable))
            } catch (e: SecurityException) {
                pending = null
                onMessageNow(ShellMessage.LaunchFailed(unavailable))
            }
        }
    }
}
