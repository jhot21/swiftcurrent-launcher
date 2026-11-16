package me.jhot.swiftcurrent.ui.home

import android.Manifest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.context.DefaultMeldModes
import me.jhot.swiftcurrent.context.PairedDevice
import me.jhot.swiftcurrent.core.Condition
import me.jhot.swiftcurrent.core.ConditionEval
import me.jhot.swiftcurrent.core.ContextState
import me.jhot.swiftcurrent.core.orElse
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.SheetCard
import me.jhot.swiftcurrent.ui.design.SheetScaffold
import me.jhot.swiftcurrent.ui.design.Tokens

/** The kinds of condition a favorite can be given, each behind its own runtime permission. */
enum class ConditionKind(val permission: String) {
    Bluetooth(Manifest.permission.BLUETOOTH_CONNECT),
    Meld(DefaultMeldModes.PERMISSION),
}

// The only red in the launcher; it marks a condition that can never match.
private val brokenColor = Color(0xFFE5534B)

/**
 * Same subject and polarity. A Bluetooth device is its address: the stored name is only a fallback
 * label, so a device renamed since does not count as a second condition.
 */
internal fun List<Condition>.adding(c: Condition): List<Condition> {
    val duplicate = any { it == c || (it is Condition.Bluetooth && c is Condition.Bluetooth && it.address == c.address && it.whenTrue == c.whenTrue) }
    return if (duplicate) this else this + c
}

/** Staged conditions outlive an activity recreation like the name draft; they are not parcelable, so they go as JSON. */
internal val ConditionsSaver: Saver<List<Condition>?, String> = run {
    val serializer = ListSerializer(Condition.serializer())
    Saver(save = { list -> list?.let { Json.encodeToString(serializer, it) } }, restore = { Json.decodeFromString(serializer, it) })
}

/**
 * The "Show when..." part of the edit sheet: a card per condition with its polarity and a remove
 * button, then "Add condition". [adding] is whether the kind choice is open. Edits go to [onChange];
 * the sheet stages them until Save.
 */
@Composable
internal fun ConditionsSection(
    conditions: List<Condition>,
    context: ContextState,
    adding: Boolean,
    onChange: (List<Condition>) -> Unit,
    onAddingChange: (Boolean) -> Unit,
    onAdd: (ConditionKind) -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("conditions-section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppText(stringResource(R.string.conditions_title), AppType.label, Modifier.padding(start = 4.dp, top = 8.dp), color = Tokens.dimText)
        if (conditions.isEmpty()) {
            AppText(stringResource(R.string.conditions_always), AppType.label, Modifier.padding(start = 4.dp), color = Tokens.dimText)
        }
        // Keyed by position: cards neither animate nor hold state of their own, so a shift after a remove is harmless.
        conditions.forEachIndexed { i, c ->
            key(i) {
                ConditionCard(
                    index = i, condition = c, broken = ConditionEval.isBroken(c, context),
                    onPolarity = { v -> onChange(conditions.mapIndexed { k, old -> if (k == i) old.withPolarity(v) else old }) },
                    onRemove = { onChange(conditions.filterIndexed { k, _ -> k != i }) },
                )
            }
        }
        if (conditions.isNotEmpty()) {
            AppText(stringResource(R.string.conditions_all_must_hold), AppType.label, Modifier.padding(start = 4.dp), color = Tokens.dimText)
        }
        Pill(stringResource(R.string.conditions_add), "condition-add", selected = adding) { onAddingChange(!adding) }
        if (adding) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.conditions_kind_bluetooth), "condition-add-bluetooth") { onAdd(ConditionKind.Bluetooth) }
                Pill(stringResource(R.string.conditions_kind_meld), "condition-add-meld") { onAdd(ConditionKind.Meld) }
            }
        }
    }
}

private fun Condition.withPolarity(v: Boolean): Condition = when (this) {
    is Condition.Bluetooth -> copy(whenTrue = v)
    is Condition.MeldMode -> copy(whenTrue = v)
}

@Composable
private fun Pill(text: String, tag: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.testTag(tag)
            .background(if (selected) Tokens.dot else Tokens.card, Tokens.pillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) { AppText(text, AppType.body, color = if (selected) Tokens.black else Tokens.text) }
}

@Composable
private fun ConditionCard(index: Int, condition: Condition, broken: Boolean, onPolarity: (Boolean) -> Unit, onRemove: () -> Unit) {
    val (kind, subject) = when (condition) {
        is Condition.Bluetooth -> stringResource(R.string.conditions_kind_bluetooth) to
            (if (condition.address == null) stringResource(R.string.conditions_any_bluetooth) else condition.name)
        is Condition.MeldMode -> stringResource(R.string.conditions_kind_meld) to condition.mode
    }
    val (on, off) = when (condition) {
        is Condition.Bluetooth -> stringResource(R.string.conditions_connected) to stringResource(R.string.conditions_not_connected)
        is Condition.MeldMode -> stringResource(R.string.conditions_active) to stringResource(R.string.conditions_inactive)
    }
    val removeLabel = stringResource(R.string.conditions_remove)
    Row(
        Modifier.fillMaxWidth().testTag("condition-$index")
            .background(Tokens.card, Tokens.cardShape)
            .then(if (broken) Modifier.border(1.5.dp, brokenColor, Tokens.cardShape) else Modifier)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(stringResource(R.string.conditions_card, kind, subject), AppType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (broken) {
                val message = if (condition is Condition.Bluetooth) R.string.conditions_broken_device else R.string.conditions_broken_mode
                AppText(stringResource(message), AppType.label, color = brokenColor)
            }
            Row(
                Modifier.testTag("condition-toggle-$index").background(Tokens.sheet, Tokens.pillShape).padding(3.dp).selectableGroup(),
            ) {
                for ((value, label) in listOf(true to on, false to off)) {
                    val selected = condition.whenTrue == value
                    Box(
                        Modifier
                            .background(if (selected) Tokens.dot else Color.Transparent, Tokens.pillShape)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { if (!selected) onPolarity(value) })
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) { AppText(label, AppType.label, color = if (selected) Tokens.black else Tokens.dimText) }
                }
            }
        }
        Box(
            Modifier.testTag("condition-remove-$index").size(48.dp)
                .semantics { contentDescription = removeLabel }
                .clickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(14.dp)) {
                val w = 1.6.dp.toPx()
                drawLine(Tokens.dimText, Offset(w, w), Offset(size.width - w, size.height - w), w, StrokeCap.Round)
                drawLine(Tokens.dimText, Offset(size.width - w, w), Offset(w, size.height - w), w, StrokeCap.Round)
            }
        }
    }
}

/** One read of a picker's list; [items] null means the source could not be read. */
private class Read<T>(val items: List<T>?)

/** Runs a blocking [load] on [io]; null while it runs. A throwing source reads as unknown. */
@Composable
private fun <T> readOnOpen(io: CoroutineDispatcher, load: () -> List<T>?): Read<T>? {
    val read by produceState<Read<T>?>(null) {
        value = Read(
            orElse(null) {
                withContext(io) { load() }
            },
        )
    }
    return read
}

/**
 * Picks a Bluetooth device: "Any Bluetooth device" first, then paired devices by name. The device
 * list is read each time the picker opens. A blank name is stored as the address, so the condition
 * always has something to show once the device is gone.
 */
@Composable
internal fun BluetoothPicker(load: () -> List<PairedDevice>?, io: CoroutineDispatcher, onPick: (Condition.Bluetooth) -> Unit, onDismiss: () -> Unit) {
    val read = readOnOpen(io, load)
    val any = stringResource(R.string.conditions_any_bluetooth)
    SheetScaffold(title = stringResource(R.string.conditions_bluetooth_picker), onDismiss = onDismiss) {
        SheetCard(icon = null, title = any, onClick = { onPick(Condition.Bluetooth(null, any)) }, modifier = Modifier.testTag("bt-any"))
        if (read != null) {
            val devices = read.items
            when {
                devices == null -> Hint(stringResource(R.string.conditions_bluetooth_unknown))
                devices.isEmpty() -> Hint(stringResource(R.string.conditions_bluetooth_none))
                else -> devices.map { it.copy(name = it.name.ifBlank { it.address }) }
                    .sortedBy { it.name.lowercase() }
                    .forEach { d ->
                        SheetCard(
                            icon = null, title = d.name, onClick = { onPick(Condition.Bluetooth(d.address, d.name)) },
                            modifier = Modifier.testTag("bt-device-${d.address}"),
                        )
                    }
            }
        }
    }
}

/** Picks a Meld mode. Meld sends no broadcast when a mode is made, renamed or deleted, so the list is read at every open. */
@Composable
internal fun MeldModePicker(load: () -> List<String>?, io: CoroutineDispatcher, onPick: (Condition.MeldMode) -> Unit, onDismiss: () -> Unit) {
    val read = readOnOpen(io, load)
    SheetScaffold(title = stringResource(R.string.conditions_kind_meld), onDismiss = onDismiss) {
        if (read != null) {
            val modes = read.items
            when {
                modes == null -> Hint(stringResource(R.string.conditions_meld_unknown))
                modes.isEmpty() -> Hint(stringResource(R.string.conditions_meld_none))
                else -> modes.forEach { m ->
                    SheetCard(icon = null, title = m, onClick = { onPick(Condition.MeldMode(m)) }, modifier = Modifier.testTag("meld-mode-$m"))
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    AppText(text, AppType.body, Modifier.padding(start = 4.dp), color = Tokens.dimText)
}
