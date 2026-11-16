package me.jhot.swiftcurrent.ui.editor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Copy lives in string resources; a quoted literal handed to a text call is how English sneaks back in.
class EditorLiteralsTest {
    // The whole main source set: a hard-coded sentence anywhere in it is a sentence that is never translated.
    private val root = File("src/main/java")

    // Tags and test identifiers (testTag, tag strings) are not matched: only calls that show text.
    private val shown = Regex(
        """(\bAppText\(|\bText\(|contentDescription\s*=|stateDescription\s*=|CustomAccessibilityAction\(|\btitle\s*=|""" +
            """Failed\(\s*|\bAction\(\s*|\bact\(\s*|\bbadge\s*=[^\n"]*?|\blabel\s*=)\s*"""",
    )

    // `label = "..."` is also the name Compose animations take for the inspector: not user-visible.
    private val allowed = Regex("""(animate\w+AsState|updateTransition|Crossfade|AnimatedContent|rememberInfiniteTransition)""")

    private val interpolationOnly = Regex("""=\s*"(\$\{?[\w.]+\}?[:,\s]*)+"""")

    @Test
    fun no_user_visible_english_literal_anywhere_in_the_main_sources() {
        assertTrue("missing ${root.path}", root.exists())
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val offenders = files.flatMap { f ->
            val text = f.readText()
            shown.findAll(text).mapNotNull { m ->
                val line = text.substring(m.range.first).lineSequence().first().trim()
                val context = text.substring(maxOf(0, m.range.first - 200), m.range.first)
                if (line.startsWith("label") && allowed.containsMatchIn(context)) return@mapNotNull null
                // "${a}: ${b}" joins two names with punctuation and has no words of its own.
                if (interpolationOnly.containsMatchIn(line)) return@mapNotNull null
                "${f.name}:${text.substring(0, m.range.first).count { it == '\n' } + 1}: $line"
            }.toList()
        }
        assertTrue("hard-coded text:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }
}
