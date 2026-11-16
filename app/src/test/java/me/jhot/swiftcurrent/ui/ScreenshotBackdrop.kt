package me.jhot.swiftcurrent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Sheets are drawn over a scrim and the scrim shows whatever is behind it. Without a backdrop that is
 * the harness's default window colour, which changed between Roborazzi releases, so every sheet baseline
 * went from black to grey with no change to the sheet. Pinning it keeps the baselines about the app.
 */
@Composable
fun ScreenshotBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.background(Color.Black)) { content() }
}
