package me.jhot.swiftcurrent.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Runs [block] each time the screen is started and cancels it when it stops, so state changed in system
 * settings (a permission, a service) is read again on return. With no lifecycle owner (previews, tests)
 * it runs once. Restarts when any of [keys] change.
 */
@Composable
internal fun WhileStarted(vararg keys: Any?, block: suspend () -> Unit) {
    val owner = LocalView.current.findViewTreeLifecycleOwner()
    LaunchedEffect(*keys, owner) {
        if (owner == null) block() else owner.repeatOnLifecycle(Lifecycle.State.STARTED) { block() }
    }
}
