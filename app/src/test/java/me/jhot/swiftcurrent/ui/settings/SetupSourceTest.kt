package me.jhot.swiftcurrent.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// The app must never change the default launcher itself: setup only opens the system's own screen.
class SetupSourceTest {
    @Test
    fun nothing_requests_the_home_role_or_sets_a_preferred_activity() {
        val banned = listOf("createRequestRoleIntent", "ACTION_REQUEST_ROLE", "addPreferredActivity", "addCategory(Intent.CATEGORY_HOME)")
        val offenders = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            val text = f.readText()
            banned.filter { it in text }.map { "${f.name}: $it" }
        }.toList()
        assertTrue("forbidden: $offenders", offenders.isEmpty())
    }
}
