package me.jhot.swiftcurrent.ui.home

import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Target

object HomeRows {
    /**
     * Turns the saved favorites into display rows. Only app targets can go missing (uninstalled);
     * shortcuts and folders are never flagged here, since their own validity is checked elsewhere.
     * The input list is not changed: the saved data keeps rows the screen does not draw.
     */
    fun build(
        favorites: List<Entry>,
        installed: (String) -> Boolean,
        labelFor: (Entry) -> String,
        archived: (String) -> Boolean = { false },
    ): List<HomeRow> = favorites.map { e ->
        val target = e.target
        HomeRow(
            entry = e,
            label = e.customLabel ?: labelFor(e),
            missing = target is Target.App && !installed(target.component),
            archived = target is Target.App && archived(target.component),
        )
    }
}
