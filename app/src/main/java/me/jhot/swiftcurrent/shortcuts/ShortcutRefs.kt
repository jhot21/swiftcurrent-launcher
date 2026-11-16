package me.jhot.swiftcurrent.shortcuts

import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target

/**
 * Removes everything that points at the shortcut [key]: favorites, library folders, pop-up rows
 * (nested pop-ups and app pop-ups too; own-shortcuts rows stay) and its switch-off key. The
 * caller owns [LauncherData.userShortcuts], which this leaves alone.
 */
fun LauncherData.withoutShortcut(key: String): LauncherData {
    val target = Target.Shortcut(key)
    return copy(
        favorites = favorites.without(target),
        folders = folders.without(target),
        appPopups = appPopups.mapValues { (_, p) -> p.without(target) },
        settings = settings.copy(disabledShortcuts = settings.disabledShortcuts - key),
    )
}

private fun List<Entry>.without(target: Target): List<Entry> =
    filter { it.target != target }.map { e -> e.popup?.let { e.copy(popup = it.without(target)) } ?: e }

private fun Popup.without(target: Target): Popup = Popup(
    rows.mapNotNull { row ->
        when {
            row is PopupRow.Item && row.entry.target == target -> null
            row is PopupRow.Item -> PopupRow.Item(listOf(row.entry).without(target).single())
            else -> row
        }
    },
)
