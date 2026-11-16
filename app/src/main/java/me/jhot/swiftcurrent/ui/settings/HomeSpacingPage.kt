package me.jhot.swiftcurrent.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.HomeSpacing
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.StepSlider
import me.jhot.swiftcurrent.ui.design.Tokens
import me.jhot.swiftcurrent.ui.home.FavoritesCapacity
import me.jhot.swiftcurrent.ui.home.HomeScreen
import me.jhot.swiftcurrent.ui.home.HomeUiState
import me.jhot.swiftcurrent.ui.home.homeMetrics

/**
 * The space under the favorites, adjusted with a preview that is the real Home screen: the same
 * composable, fed the owner's own favorites and the value being chosen, at true scale and with the
 * real system bars, so what is seen here is exactly what Home will show. The controls float over
 * the top, where Home has nothing but empty space.
 *
 * How far the space can go depends on the screen and on how many favorites there are: the most that still
 * keeps the clock and every favorite on screen, worked out with the same capacity math Home uses.
 */
@Composable
fun HomeSpacingPage(
    preview: HomeUiState,
    gapDp: Int,
    onGap: (Int) -> Unit,
) {
    // The number of favorites that fit, as the preview itself measured it, so the note cannot disagree with Home.
    var fits by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val total = preview.favorites.count { !it.missing }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().testTag("settings-spacing")) {
        val insets = WindowInsets.systemBars
        val metrics = homeMetrics(
            heightDp = maxHeight.value,
            topInsetDp = with(density) { insets.getTop(density).toDp().value },
            bottomInsetDp = with(density) { insets.getBottom(density).toDp().value },
            fontScale = density.fontScale,
        )
        val maxGap = HomeSpacing.clamp(FavoritesCapacity.maxGapDp(metrics, total).toInt())
        // The controls sit in the larger of the two empty regions: above the clock, or in the gap under the
        // favorites once that has grown bigger. Otherwise they would cover the very thing being previewed.
        val roomAbove = FavoritesCapacity.maxGapDp(metrics, total) - gapDp
        val controlsAtBottom = gapDp > roomAbove
        HomeScreen(
            state = preview,
            onClock = {}, onAgenda = {}, onItem = {},
            onCapacity = { fits = it },
            bottomGapDp = gapDp.toFloat(),
        )
        Column(
            Modifier.align(if (controlsAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .then(if (controlsAtBottom) Modifier.navigationBarsPadding() else Modifier.statusBarsPadding())
                .testTag(if (controlsAtBottom) "spacing-controls-bottom" else "spacing-controls-top")
                .padding(horizontal = Tokens.pageGutter, vertical = 16.dp)
                .fillMaxWidth()
                .background(Tokens.sheet, Tokens.cardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(stringResource(R.string.settings_home_spacing), AppType.body)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepButton("\u2212", stringResource(R.string.settings_spacing_less), "spacing-less", enabled = gapDp > HomeSpacing.MIN) {
                    onGap(HomeSpacing.clamp(gapDp - HomeSpacing.STEP))
                }
                AppText(
                    stringResource(R.string.settings_spacing_value, gapDp), AppType.body,
                    Modifier.weight(1f).testTag("spacing-value"), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepButton("+", stringResource(R.string.settings_spacing_more), "spacing-more", enabled = gapDp < maxGap) {
                    onGap(HomeSpacing.clamp(minOf(gapDp + HomeSpacing.STEP, maxGap)))
                }
            }
            StepSlider(
                value = gapDp, max = maxGap, step = HomeSpacing.STEP, label = stringResource(R.string.settings_home_spacing),
                tag = "spacing-slider", onValue = { onGap(HomeSpacing.clamp(it)) },
            )
            // With no favorites there is nothing to fit: Home shows one hint row.
            if (total > 0) {
                AppText(
                    if (total <= fits) stringResource(R.string.settings_spacing_all_fit, total)
                    else stringResource(R.string.settings_spacing_some_hidden, fits, total),
                    AppType.label, Modifier.testTag("spacing-fit"),
                    color = if (total <= fits) Tokens.dimText else Tokens.text,
                )
            }
            AppText(
                stringResource(R.string.settings_spacing_max, maxGap), AppType.caption,
                Modifier.testTag("spacing-max"), color = Tokens.dimText,
            )
            AppText(stringResource(R.string.settings_spacing_hint), AppType.caption, color = Tokens.dimText)
            AppText(
                stringResource(R.string.settings_spacing_reset), AppType.label,
                Modifier.testTag("spacing-reset").clickable(enabled = gapDp != HomeSpacing.DEFAULT) { onGap(HomeSpacing.DEFAULT) }
                    .padding(vertical = 8.dp),
                color = if (gapDp != HomeSpacing.DEFAULT) Tokens.text else Tokens.dimText,
            )
        }
    }
}

@Composable
private fun StepButton(glyph: String, description: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).testTag(tag)
            .background(Tokens.card, Tokens.pillShape)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(glyph, AppType.body, color = if (enabled) Tokens.text else Tokens.dimText)
    }
}
