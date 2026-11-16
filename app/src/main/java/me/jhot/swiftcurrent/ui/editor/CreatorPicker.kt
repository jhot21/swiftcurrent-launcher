package me.jhot.swiftcurrent.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.shortcuts.Creator
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.SheetCard
import me.jhot.swiftcurrent.ui.design.SheetScaffold
import me.jhot.swiftcurrent.ui.design.Tokens

/**
 * The sheet the editor's "Add shortcut" opens: every app's create-shortcut activities, grouped by
 * app. [creators] is null while they are still being looked up. Picking one calls [onPick]; the
 * caller starts it and closes the sheet.
 */
@Composable
fun CreatorPicker(creators: List<Creator>?, onPick: (Creator) -> Unit, onDismiss: () -> Unit) {
    SheetScaffold(title = stringResource(R.string.shortcut_picker_title), onDismiss = onDismiss) {
        if (creators != null && creators.isEmpty()) {
            AppText(
                stringResource(R.string.shortcut_picker_empty), AppType.body,
                Modifier.testTag("creator-picker-empty").padding(start = 4.dp), color = Tokens.dimText,
            )
        }
        creators.orEmpty().groupBy { it.appLabel }.forEach { (app, items) ->
            AppText(app, AppType.label, Modifier.padding(start = 4.dp, top = 8.dp), color = Tokens.dimText)
            items.forEach { c ->
                Box(Modifier.testTag("creator-${c.component}").semantics(mergeDescendants = true) {}) { SheetCard(icon = {}, title = c.label, onClick = { onPick(c) }) }
            }
        }
    }
}
