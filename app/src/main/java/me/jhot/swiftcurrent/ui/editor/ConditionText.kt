package me.jhot.swiftcurrent.ui.editor

import me.jhot.swiftcurrent.core.Condition

/** The line under a favorite in the editor, or null when it has no conditions. */
fun conditionSummary(conditions: List<Condition>, strings: EditorStrings): String? {
    if (conditions.isEmpty()) return null
    val clauses = conditions.map { c ->
        when (c) {
            is Condition.MeldMode -> strings.meldModeClause(c.mode, c.whenTrue)
            is Condition.Bluetooth ->
                if (c.address == null) strings.anyBluetoothClause(c.whenTrue) else strings.bluetoothClause(c.name, c.whenTrue)
        }
    }
    return strings.shownWhen(clauses.reduce(strings::clausesAnd))
}
