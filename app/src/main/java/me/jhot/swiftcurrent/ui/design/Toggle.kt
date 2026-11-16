package me.jhot.swiftcurrent.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The switch every settings row puts in its trailing slot. [label] is the accessible name (the row
 * title is not part of the switch's own node) and [tag] its test identifier.
 */
@Composable
fun Toggle(on: Boolean, label: String, tag: String, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .testTag(tag)
            .semantics { contentDescription = label }
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .size(width = 48.dp, height = 28.dp)
            .background(if (on) Tokens.dot else Tokens.card, Tokens.pillShape)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(22.dp).background(if (on) Tokens.black else Tokens.dimText, Tokens.pillShape))
    }
}

/** A checklist box for rows that pick several items; same contract as [Toggle]. */
@Composable
fun CheckBox(checked: Boolean, label: String, tag: String, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .testTag(tag)
            .semantics { contentDescription = label }
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .size(28.dp)
            .background(if (checked) Tokens.dot else Tokens.card, RoundedCornerShape(8.dp))
            .border(1.dp, Tokens.dimText, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(Modifier.size(16.dp)) {
                val w = 2.dp.toPx()
                drawLine(Tokens.black, Offset(size.width * 0.1f, size.height * 0.55f), Offset(size.width * 0.4f, size.height * 0.85f), w, StrokeCap.Round)
                drawLine(Tokens.black, Offset(size.width * 0.4f, size.height * 0.85f), Offset(size.width * 0.9f, size.height * 0.15f), w, StrokeCap.Round)
            }
        }
    }
}
