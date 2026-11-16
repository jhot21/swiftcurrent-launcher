package me.jhot.swiftcurrent.ui.home

import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Target
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRowsTest {
    @Test
    fun custom_label_wins_and_only_apps_can_be_missing() {
        val app = Entry("a", Target.App("p/.A"), customLabel = "Browser")
        val shortcut = Entry("s", Target.Shortcut("k"))
        val folder = Entry("f", Target.Folder)
        val rows = HomeRows.build(listOf(app, shortcut, folder), installed = { false }, labelFor = { "fallback-${it.id}" })
        assertEquals(listOf("Browser", "fallback-s", "fallback-f"), rows.map { it.label })
        assertEquals(listOf(true, false, false), rows.map { it.missing })
    }

    @Test
    fun an_archived_app_is_flagged_and_other_targets_never_are() {
        val app = Entry("a", Target.App("p/.A"))
        val other = Entry("b", Target.App("q/.B"))
        val shortcut = Entry("s", Target.Shortcut("p/key"))
        val rows = HomeRows.build(
            listOf(app, other, shortcut), installed = { true }, labelFor = { it.id }, archived = { it == "p/.A" },
        )
        assertEquals(listOf(true, false, false), rows.map { it.archived })
    }
}
