package me.jhot.swiftcurrent.shortcuts

import me.jhot.swiftcurrent.core.Settings

/** Whether a shortcut (by key) may appear in search; the one predicate every shortcut source shares. */
fun interface ShortcutFilter {
    fun enabled(key: String): Boolean

    companion object {
        val All = ShortcutFilter { true }
    }
}

/**
 * Which shortcuts are switched off. Only the DISABLED keys are stored (`Settings.disabledShortcuts`),
 * so a shortcut that appears later is on by default. Keys are "package/id" for the system's
 * shortcuts and "user/<id>" for the ones the owner made. Reads come from [disabled] (the shell's
 * live data) and writes go through [edit], so there is one copy of the truth.
 */
class ShortcutPrefs(
    private val disabled: () -> Set<String>,
    private val edit: suspend ((Settings) -> Settings) -> Unit,
    private val keysOf: suspend (app: String) -> List<String>,
) : ShortcutFilter {
    override fun enabled(key: String) = key !in disabled()

    suspend fun setEnabled(key: String, on: Boolean) = edit { s ->
        s.copy(disabledShortcuts = if (on) s.disabledShortcuts - key else s.disabledShortcuts + key)
    }

    /** "Toggle all" for one app, system and user-made shortcuts alike. */
    suspend fun setAll(app: String, on: Boolean) {
        val keys = keysOf(app).toSet()
        edit { s -> s.copy(disabledShortcuts = if (on) s.disabledShortcuts - keys else s.disabledShortcuts + keys) }
    }
}
