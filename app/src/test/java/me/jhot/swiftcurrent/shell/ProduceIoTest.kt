package me.jhot.swiftcurrent.shell

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ProduceIoTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun shows_what_the_read_answers() {
        var seen = "default"
        compose.setContent {
            val value by produceIo("default", Dispatchers.IO) { "read" }
            seen = value
        }
        compose.waitUntil(5_000) { seen == "read" }
    }

    // A throwing read must not take the composition down; the screen keeps what it had.
    @Test
    fun keeps_the_default_when_the_read_throws() {
        var seen = "default"
        var attempted = false
        compose.setContent {
            val value by produceIo("default", Dispatchers.IO) { attempted = true; error("binder died") }
            seen = value
        }
        compose.waitUntil(5_000) { attempted }
        compose.waitForIdle()
        assertEquals("default", seen)
    }
}
