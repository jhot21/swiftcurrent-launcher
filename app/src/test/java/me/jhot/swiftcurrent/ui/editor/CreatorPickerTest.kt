package me.jhot.swiftcurrent.ui.editor

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.shortcuts.Creator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class CreatorPickerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val maps = Creator("pkg.maps/.Directions", "Directions", "Maps")
    private val mail = Creator("pkg.mail/.Label", "Label", "Mail")

    private fun drawable(): Drawable = BitmapDrawable(compose.activity.resources, Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))

    // The row merges its descendants for accessibility, so the icon's tag only exists in the unmerged tree.
    private fun icon(c: Creator) = compose.onNodeWithTag("creator-icon-${c.component}", useUnmergedTree = true)

    private fun show(iconFor: suspend (Creator) -> Drawable?, onPick: (Creator) -> Unit = {}) =
        compose.setContent { CreatorPicker(listOf(maps, mail), onPick = onPick, onDismiss = {}, iconFor = iconFor) }

    @Test
    fun a_row_draws_the_icon_its_creator_resolves_to() {
        show(iconFor = { drawable() })
        compose.waitForIdle()
        icon(maps).assertExists()
        icon(mail).assertExists()
    }

    @Test
    fun a_creator_without_an_icon_keeps_the_plain_chip() {
        show(iconFor = { c -> if (c == maps) drawable() else null })
        compose.waitForIdle()
        icon(maps).assertExists()
        icon(mail).assertDoesNotExist()
    }

    @Test
    fun a_failing_icon_load_leaves_the_row_usable() {
        val picked = mutableListOf<Creator>()
        show(iconFor = { error("no icon") }, onPick = { picked += it })
        compose.waitForIdle()
        icon(maps).assertDoesNotExist()
        compose.onNodeWithTag("creator-${maps.component}").performClick()
        assertEquals(listOf(maps), picked)
    }
}
