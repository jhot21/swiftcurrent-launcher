package me.jhot.swiftcurrent.ui.design

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TypeScaleTest {
    // A one-off size is how screens drift apart, so every size lives in Type.kt.
    @Test
    fun onlyNamedSizesAreUsed() {
        val root = File("src/main/java/me/jhot/swiftcurrent/ui")
        assertTrue("expected ${root.absolutePath} to exist", root.isDirectory)
        val literal = Regex("""\d+(\.\d+)?\.sp\b""")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "Type.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    if (literal.containsMatchIn(line)) "${file.name}:${i + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        assertTrue("sp literals outside Type.kt:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }
}
