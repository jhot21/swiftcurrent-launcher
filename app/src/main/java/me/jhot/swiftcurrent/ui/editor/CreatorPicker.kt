package me.jhot.swiftcurrent.ui.editor

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CancellationException
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
 * caller starts it and closes the sheet. [iconFor] resolves a row's icon off the main thread; a row
 * keeps the plain chip until it arrives, and for good when it returns null or throws.
 */
@Composable
fun CreatorPicker(
    creators: List<Creator>?,
    onPick: (Creator) -> Unit,
    onDismiss: () -> Unit,
    iconFor: suspend (Creator) -> Drawable? = { null },
) {
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
                Box(Modifier.testTag("creator-${c.component}").semantics(mergeDescendants = true) {}) {
                    SheetCard(icon = { CreatorIcon(c, iconFor) }, title = c.label, onClick = { onPick(c) })
                }
            }
        }
    }
}

@Composable
private fun CreatorIcon(creator: Creator, iconFor: suspend (Creator) -> Drawable?) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, creator.component) {
        value = try {
            iconFor(creator)?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // One creator's broken icon must not take the sheet down; the row just stays plain.
            null
        }
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp).testTag("creator-icon-${creator.component}")) }
}

private const val ICON_PX = 96
