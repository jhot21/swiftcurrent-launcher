package me.jhot.swiftcurrent.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.ui.design.SheetCard
import me.jhot.swiftcurrent.ui.design.SheetScaffold
import me.jhot.swiftcurrent.ui.design.Tokens
import me.jhot.swiftcurrent.ui.design.pencil

/** What each row does is decided by the caller; the sheet only reports the tap. */
data class ActionSheetCallbacks(
    val onEditFavorites: () -> Unit = {},
    val onAppInfo: () -> Unit = {},
    val onHide: () -> Unit = {},
    val onRename: () -> Unit = {},
    /** Set where the sheet is for an app in the app list: a way to add or remove it as a favorite. */
    val favorite: FavoriteAction? = null,
    /** False for an app in the app list, where editing the whole list of favorites is not the point. */
    val showEditFavorites: Boolean = true,
    /** Rename is offered when the entry has somewhere to keep a custom name (it is a favorite). */
    val canRename: Boolean = true,
    /** Set for a shortcut the owner can delete (pinned or made by them); null shows no row. */
    val onDelete: (() -> Unit)? = null,
)

/** The add-or-remove row: [isFavorite] says which it is. */
data class FavoriteAction(val isFavorite: Boolean, val onToggle: () -> Unit)

/**
 * The long-press sheet for a home item. Back and scrim close it through [SheetScaffold]. App info
 * and Hide act on an installed app, so only an app shows them; a shortcut or a folder has no
 * package to open or hide. Every kind can be renamed.
 */
@Composable
fun ActionSheet(
    entry: Entry,
    onDismiss: () -> Unit,
    callbacks: ActionSheetCallbacks = ActionSheetCallbacks(),
    label: String = entry.customLabel ?: stringResource(neutralLabelRes(entry)),
) {
    SheetScaffold(title = label, onDismiss = onDismiss) {
        callbacks.favorite?.let { f ->
            SheetCard(
                icon = { Glyph(ActionGlyph.Edit) },
                title = stringResource(if (f.isFavorite) R.string.action_sheet_remove_favorite else R.string.action_sheet_add_favorite),
                onClick = f.onToggle,
                modifier = Modifier.testTag("sheet-favorite"),
            )
        }
        if (callbacks.showEditFavorites) {
            SheetCard(
                icon = { Glyph(ActionGlyph.Edit) }, title = stringResource(R.string.action_sheet_edit_favorites),
                onClick = callbacks.onEditFavorites,
            )
        }
        if (callbacks.canRename) {
            SheetCard(
                icon = { Glyph(ActionGlyph.Rename) }, title = stringResource(R.string.action_sheet_rename),
                onClick = callbacks.onRename,
            )
        }
        callbacks.onDelete?.let { delete ->
            SheetCard(
                icon = { Glyph(ActionGlyph.Delete) }, title = stringResource(R.string.action_sheet_delete_shortcut),
                onClick = delete,
                modifier = Modifier.testTag("sheet-delete"),
            )
        }
        if (entry.target is Target.App) {
            SheetCard(
                icon = { Glyph(ActionGlyph.Info) }, title = stringResource(R.string.action_sheet_app_info),
                onClick = callbacks.onAppInfo,
            )
            SheetCard(
                icon = { Glyph(ActionGlyph.Hide) }, title = stringResource(R.string.action_sheet_hide),
                onClick = callbacks.onHide,
            )
        }
    }
}

/** Never a raw id: an entry with no label is named by what kind of thing it is. */
internal fun neutralLabelRes(entry: Entry): Int = when (entry.target) {
    is Target.App -> R.string.label_unknown_app
    is Target.Shortcut -> R.string.label_unknown_shortcut
    Target.Folder -> R.string.editor_new_folder
}

private enum class ActionGlyph { Edit, Rename, Info, Hide, Delete }

/** Small line icons on the sheet's chip, drawn like the editor's scrubber glyphs. */
@Composable
private fun Glyph(kind: ActionGlyph) {
    Canvas(Modifier.size(20.dp).testTag("action-glyph-${kind.name}")) {
        val w = 1.6.dp.toPx()
        when (kind) {
            ActionGlyph.Edit -> lines(w)
            ActionGlyph.Rename -> pencil(Tokens.text, w)
            ActionGlyph.Info -> info(w)
            ActionGlyph.Hide -> hide(w)
            ActionGlyph.Delete -> cross(w)
        }
    }
}

private fun DrawScope.lines(width: Float) {
    for (y in listOf(0.25f, 0.5f, 0.75f)) {
        drawLine(Tokens.text, Offset(size.width * 0.1f, size.height * y), Offset(size.width * 0.9f, size.height * y), width)
    }
}

private fun DrawScope.info(width: Float) {
    drawCircle(Tokens.text, radius = size.minDimension / 2f - width, style = Stroke(width))
    drawCircle(Tokens.text, radius = width * 0.8f, center = Offset(size.width / 2f, size.height * 0.3f))
    drawLine(Tokens.text, Offset(size.width / 2f, size.height * 0.45f), Offset(size.width / 2f, size.height * 0.75f), width)
}

private fun DrawScope.hide(width: Float) {
    drawCircle(Tokens.text, radius = size.minDimension / 2f - width, style = Stroke(width))
    drawLine(Tokens.text, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.78f, size.height * 0.22f), width)
}

private fun DrawScope.cross(width: Float) {
    drawLine(Tokens.text, Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.8f), width)
    drawLine(Tokens.text, Offset(size.width * 0.8f, size.height * 0.2f), Offset(size.width * 0.2f, size.height * 0.8f), width)
}
