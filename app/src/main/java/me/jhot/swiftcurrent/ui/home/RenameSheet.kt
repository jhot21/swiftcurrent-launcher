package me.jhot.swiftcurrent.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.context.PairedDevice
import me.jhot.swiftcurrent.core.Condition
import me.jhot.swiftcurrent.core.ContextState
import me.jhot.swiftcurrent.core.Renames
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.SheetScaffold
import me.jhot.swiftcurrent.ui.design.Tokens

/**
 * Asks for a new name. [currentLabel] is what shows now and [defaultLabel] is what a reset goes back
 * to; Reset is offered only while they differ. The draft survives an activity recreation, and the
 * field enforces [Renames.MAX_LENGTH]. A blank save is the same as a reset, so the caller treats an
 * empty string as "clear the custom label".
 *
 * A top-level favorite also gets a "Show when..." section: [conditions] is its stored list, or null
 * where conditions are not offered. Condition edits are staged with the name: dismissing drops both,
 * and Save reports each of [onSave] and [onConditionsChange] only when that part changed. Adding a
 * kind first goes through [requestPermission]; its pickers read [loadPairedDevices] or [loadModes]
 * on [io] every time they open, since both block.
 */
@Composable
fun RenameSheet(
    currentLabel: String,
    defaultLabel: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    showCursor: Boolean = true,
    conditions: List<Condition>? = null,
    context: ContextState = ContextState(),
    onConditionsChange: (List<Condition>) -> Unit = {},
    loadPairedDevices: () -> List<PairedDevice>? = { null },
    loadModes: () -> List<String>? = { null },
    requestPermission: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(currentLabel, TextRange(0, currentLabel.length)))
    }
    var staged by rememberSaveable(stateSaver = ConditionsSaver) { mutableStateOf(conditions) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf<ConditionKind?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    // Compared as stored, so trailing spaces alone are not a change, and a blank draft means the default.
    fun normal(text: String) = Renames.sanitize(text) ?: defaultLabel
    val changed = normal(draft.text) != normal(currentLabel)
    val conditionsChanged = staged != conditions
    val canSave = changed || conditionsChanged
    fun saveConditions() {
        val list = staged
        if (conditionsChanged && list != null) onConditionsChange(list)
    }
    fun save() {
        saveConditions()
        if (changed) onSave(draft.text)
    }
    fun pick(c: Condition) {
        staged = staged?.adding(c)
        picker = null
    }

    SheetScaffold(title = stringResource(R.string.rename_title), onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth().background(Tokens.card, Tokens.cardShape).padding(horizontal = 20.dp, vertical = 14.dp)) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.copy(text = Renames.limit(it.text)) },
                singleLine = true,
                textStyle = AppType.body.copy(color = Tokens.text),
                cursorBrush = SolidColor(if (showCursor) Tokens.text else Color.Transparent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("rename-field"),
            )
        }
        if (currentLabel != defaultLabel) {
            AppText(
                stringResource(R.string.rename_reset, defaultLabel), AppType.body,
                Modifier.fillMaxWidth().testTag("rename-reset")
                    // A reset closes the sheet like Save, so condition edits made alongside it are kept.
                    .clickable(role = Role.Button, onClick = { saveConditions(); onReset() })
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                color = Tokens.dimText, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        staged?.let { list ->
            ConditionsSection(
                conditions = list, context = context, adding = adding,
                onChange = { staged = it },
                onAddingChange = { adding = it },
                onAdd = { kind ->
                    adding = false
                    requestPermission(kind.permission) { granted ->
                        if (granted) {
                            keyboard?.hide()
                            picker = kind
                        }
                    }
                },
            )
        }
        Box(
            Modifier.fillMaxWidth().testTag("rename-save").alpha(if (canSave) 1f else 0.4f)
                .background(Tokens.text, Tokens.pillShape)
                .clickable(enabled = canSave, role = Role.Button, onClick = ::save)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { AppText(stringResource(R.string.rename_save), AppType.body, color = Tokens.black) }
    }
    // A picker is a second full-size sheet after this one, so callers place the sheet in a Box, where it stacks on top.
    when (picker) {
        ConditionKind.Bluetooth -> BluetoothPicker(loadPairedDevices, io, onPick = ::pick, onDismiss = { picker = null })
        ConditionKind.Meld -> MeldModePicker(loadModes, io, onPick = ::pick, onDismiss = { picker = null })
        null -> Unit
    }
}
