package me.jhot.swiftcurrent.agenda

import me.jhot.swiftcurrent.core.LauncherData

/** Which calendars show, over `Settings.enabledCalendars` where null means every calendar. */
object CalendarFilter {
    fun isEnabled(enabled: Set<Long>?, id: Long): Boolean = enabled == null || id in enabled

    /** The new setting after flipping [id]; switching every calendar back on returns null. */
    fun toggle(enabled: Set<Long>?, id: Long, all: List<Long>): Set<Long>? {
        val next = (enabled ?: all.toSet()).let { if (id in it) it - id else it + id }
        return if (next.containsAll(all)) null else next
    }

    /** For `LauncherStore.update`, so a toggle is a partial write. */
    fun apply(data: LauncherData, id: Long, all: List<Long>): LauncherData =
        data.copy(settings = data.settings.copy(enabledCalendars = toggle(data.settings.enabledCalendars, id, all)))

    /** As [apply], for the calendars search covers. */
    fun applyToSearch(data: LauncherData, id: Long, all: List<Long>): LauncherData =
        data.copy(settings = data.settings.copy(searchCalendars = toggle(data.settings.searchCalendars, id, all)))
}
