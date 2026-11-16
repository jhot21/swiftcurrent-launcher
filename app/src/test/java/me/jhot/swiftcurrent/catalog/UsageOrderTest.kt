package me.jhot.swiftcurrent.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageOrderTest {
    private val apps = listOf("Apple", "Apex", "Acorn", "Adobe").map { AppInfo("c/$it", it, "c") }
    private val counts = mapOf("c/Apple" to 9, "c/Acorn" to 5, "c/Apex" to 2)

    @Test
    fun usage_order_within_letter_when_enabled() {
        val s = AppSections.build(apps, emptySet(), counts, sortByUsage = true)
        assertEquals(listOf("Apple", "Acorn", "Apex", "Adobe"), s.single().apps.map { it.label })
    }

    @Test
    fun ties_and_uncounted_apps_fall_back_to_alphabetical() {
        val tied = counts + ("c/Adobe" to 2)
        val s = AppSections.build(apps, emptySet(), tied, sortByUsage = true)
        assertEquals(listOf("Apple", "Acorn", "Adobe", "Apex"), s.single().apps.map { it.label })
    }

    @Test
    fun alphabetical_within_letter_when_disabled() {
        val s = AppSections.build(apps, emptySet(), counts, sortByUsage = false)
        assertEquals(listOf("Acorn", "Adobe", "Apex", "Apple"), s.single().apps.map { it.label })
    }
}
