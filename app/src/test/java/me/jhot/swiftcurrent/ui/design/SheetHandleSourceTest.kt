package me.jhot.swiftcurrent.ui.design

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// A source scan, so a new bottom sheet cannot skip the handle that marks it as a sheet.
class SheetHandleSourceTest {
    @Test
    fun every_file_that_draws_a_sheet_also_draws_the_handle() {
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" && it.name != "Tokens.kt" }
            .filter { it.readText().contains("Tokens.sheetShape") }
            .filter { !it.readText().contains("SheetHandle(") }
            .map { it.name }.toList()
        assertTrue("sheets without a SheetHandle: $offenders", offenders.isEmpty())
    }
}
