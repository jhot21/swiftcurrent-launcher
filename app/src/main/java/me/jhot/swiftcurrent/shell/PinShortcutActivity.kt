package me.jhot.swiftcurrent.shell

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.shortcuts.LauncherAppsPinSource
import me.jhot.swiftcurrent.shortcuts.PendingPin
import me.jhot.swiftcurrent.shortcuts.PinRequestSource
import me.jhot.swiftcurrent.ui.home.PinConfirmDialog

/**
 * The system starts this when an app asks to pin a shortcut and Swiftcurrent is the home app.
 * Add accepts the request; the pinned shortcut then shows up in the shortcut index like any other.
 * Nothing is placed on Home: the owner puts it where they want with the item editor.
 */
class PinShortcutActivity : ComponentActivity() {
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val density = resources.displayMetrics.densityDpi
        var pin by mutableStateOf<PendingPin?>(null)
        // Any app can start this activity with a request whose binder is its own, so reading the request
        // may block or throw. It happens off the main thread (Home shares this process) and any failure just closes.
        lifecycleScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                runCatching { (sourceOverride ?: LauncherAppsPinSource(applicationContext)).from(intent) }.getOrNull()
            }
            if (resolved == null) finish() else pin = resolved
        }
        setContent {
            val shown = pin ?: return@setContent
            val icon: Drawable? by produceState<Drawable?>(null, shown) {
                value = withContext(Dispatchers.IO) { runCatching { shown.icon(density) }.getOrNull() }
            }
            PinConfirmDialog(label = shown.label, icon = icon, onAdd = { add(shown) }, onCancel = ::finish)
        }
    }

    private fun add(pin: PendingPin) {
        // A second tap while the first is still talking to the system must not accept twice.
        if (handled) return
        handled = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { runCatching { pin.accept() } }
            finish()
        }
    }

    companion object {
        /** Tests put a fake request here; production reads the real one from `LauncherApps`. */
        @Volatile internal var sourceOverride: PinRequestSource? = null
    }
}
