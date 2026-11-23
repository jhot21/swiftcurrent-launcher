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
import androidx.compose.runtime.remember
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
import me.jhot.swiftcurrent.shortcuts.WebLinks
import me.jhot.swiftcurrent.ui.editor.WebLinkSheet

/** The text for every way an add can end other than success, resolved once so both entry points say the same thing. */
internal class AddMessages(
    private val duplicate: String,
    private val noIntent: String,
    private val noName: String,
    private val wrongTarget: String,
) {
    /** Null for a success; the message for anything else. */
    fun of(out: AddOutcome): String? = when (out) {
        is AddOutcome.Added -> null
        AddOutcome.Duplicate -> duplicate
        is AddOutcome.Rejected -> when (out.reason) {
            RejectReason.MissingIntent -> noIntent
            RejectReason.MissingName -> noName
            RejectReason.WrongTarget -> wrongTarget
        }
    }
}

@Composable
internal fun rememberAddMessages(): AddMessages = AddMessages(
    duplicate = stringResource(R.string.shortcut_rejected_duplicate),
    noIntent = stringResource(R.string.shortcut_rejected_no_intent),
    noName = stringResource(R.string.shortcut_rejected_no_name),
    wrongTarget = stringResource(R.string.shortcut_rejected_wrong_target),
)

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
    val messages = rememberAddMessages()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val component = pending
        pending = null
        val data = result.data
        // Cancelled (Back out of the creator) is not an error: nothing to add and nothing to say.
        if (component == null || result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val out = withContext(io) { deps.userShortcuts.addFromResult(Creator(component, "", ""), data, model::update) }
                if (out is AddOutcome.Added) {
                    model.data.value.userShortcuts.firstOrNull { it.id == out.id }?.let { onAddedNow(it) }
                } else {
                    onMessageNow(ShellMessage.LaunchFailed(messages.of(out).orEmpty()))
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

/** Remembers which browser's Web link form is open, across recreation; null when none is. */
internal val WebLinkCreatorSaver = androidx.compose.runtime.saveable.Saver<Creator?, List<String>>(
    save = { c -> c?.let { listOf(it.packageName, it.appLabel, it.label) }.orEmpty() },
    restore = { l -> if (l.size == 3) Creator.webLink(l[0], l[1], l[2]) else null },
)

/**
 * The form behind a browser's Web link row, shown while [creator] is set and saved through
 * [me.jhot.swiftcurrent.shortcuts.UserShortcuts.addWebLink]. Saving closes the form first, like the rename
 * sheet; a refused address ends in the same message as any other refused shortcut. The sheet stacks over
 * its caller, so call this from inside the Box that holds the screen.
 */
@Composable
internal fun WebLinkHost(
    creator: Creator?,
    deps: LauncherDependencies,
    model: ShellData,
    io: CoroutineDispatcher,
    onMessage: (ShellMessage) -> Unit,
    onAdded: suspend (UserShortcut) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val messages = rememberAddMessages()
    val failed = stringResource(R.string.search_launch_failed)
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val onMessageNow by rememberUpdatedState(onMessage)
    val onAddedNow by rememberUpdatedState(onAdded)
    if (creator == null) return
    // Read once when the form opens: the text on the clipboard then is what the owner just copied.
    val copied = remember(creator) { clipboard.getText()?.text }
    WebLinkSheet(
        browserLabel = creator.appLabel,
        clipboardUrl = copied,
        onDismiss = onDismiss,
        onSave = { url, name ->
            val link = WebLinks.parse(url) ?: return@WebLinkSheet
            onDismiss()
            scope.launch {
                try {
                    val out = withContext(io) {
                        deps.userShortcuts.addWebLink(creator.packageName, link, name, deps.creators.icon(creator), model::update)
                    }
                    if (out is AddOutcome.Added) {
                        model.data.value.userShortcuts.firstOrNull { it.id == out.id }?.let { onAddedNow(it) }
                    } else {
                        onMessageNow(ShellMessage.LaunchFailed(messages.of(out).orEmpty()))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("ShortcutCreation", "adding the web link failed", e)
                    onMessageNow(ShellMessage.LaunchFailed(failed))
                }
            }
        },
    )
}
