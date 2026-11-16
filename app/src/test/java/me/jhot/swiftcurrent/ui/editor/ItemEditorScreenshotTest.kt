package me.jhot.swiftcurrent.ui.editor

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Target
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The baseline is the oracle for the editor's look; compare it by eye with the reference capture
// (docs/scratch/niagara/121-favorites-editor.png) and re-record only for an intended change.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class ItemEditorScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun editor_favorites() {
        val selected = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf")
            .map { Entry("fav-${it.lowercase()}", Target.App("pkg.${it.lowercase()}/.Main")) }
        val state = ItemEditorState(selected, editorCandidates(), capacity = 8, strings = ResourceEditorStrings(ApplicationProvider.getApplicationContext<Context>().resources))
        compose.setContent { ItemEditor(state, Mode.Favorites, onDone = {}) }
        compose.onRoot().captureRoboImage("src/test/snapshots/editor_favorites.png")
    }

    // A short page with only shortcuts, so the groups show without scrolling: Maps is open because one of
    // its shortcuts is selected, Tasker is closed, and Solo has a single shortcut and stays a plain row.
    @Test
    fun editor_shortcut_groups() {
        val shortcuts = listOf(
            ShortcutCandidate("maps/home", "Home", "Maps"), ShortcutCandidate("maps/work", "Work", "Maps"),
            ShortcutCandidate("tasker/a", "Bedtime", "Tasker"), ShortcutCandidate("tasker/b", "Wake", "Tasker"),
            ShortcutCandidate("tasker/c", "Focus", "Tasker"), ShortcutCandidate("solo/x", "Scan", "Solo"),
        )
        val selected = listOf(Entry("maps/work", Target.Shortcut("maps/work"), customLabel = "Work"))
        val state = ItemEditorState(
            selected, Candidates(shortcuts = shortcuts), capacity = 8,
            strings = ResourceEditorStrings(ApplicationProvider.getApplicationContext<Context>().resources),
        )
        compose.setContent { ItemEditor(state, Mode.Favorites, onDone = {}) }
        compose.onRoot().captureRoboImage("src/test/snapshots/editor_shortcut_groups.png")
    }
}
