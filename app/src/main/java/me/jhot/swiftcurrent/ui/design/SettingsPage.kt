package me.jhot.swiftcurrent.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

interface SettingsScope {
    fun section(label: String)

    fun row(
        icon: @Composable () -> Unit,
        title: String,
        description: String? = null,
        trailing: (@Composable () -> Unit)? = null,
        onClick: (() -> Unit)? = null,
        modifierTag: String? = null,
    )
}

private class SettingsEntries : SettingsScope {
    val entries = mutableListOf<@Composable () -> Unit>()

    override fun section(label: String) {
        entries += { SectionLabel(label) }
    }

    override fun row(
        icon: @Composable () -> Unit,
        title: String,
        description: String?,
        trailing: (@Composable () -> Unit)?,
        onClick: (() -> Unit)?,
        modifierTag: String?,
    ) {
        entries += { SettingsRow(icon, title, description, trailing, onClick, modifierTag) }
    }
}

/**
 * The one layout every settings page uses: a big thin centered title that collapses into a pill
 * once the list scrolls. [state] is exposed so callers (and screenshot tests) can drive the scroll.
 */
@Composable
fun SettingsPage(
    title: String,
    state: LazyListState = rememberLazyListState(),
    content: SettingsScope.() -> Unit,
) {
    val entries = SettingsEntries().apply(content).entries
    val collapsed by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 120 }
    }
    Box(Modifier.fillMaxSize().background(Tokens.surface)) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth().statusBarsPadding().height(220.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    AppText(
                        title,
                        AppType.title,
                        Modifier.padding(horizontal = Tokens.pageGutter, vertical = 24.dp),
                    )
                }
            }
            entries.forEach { entry -> item { entry() } }
            item { Box(Modifier.navigationBarsPadding()) }
        }
        AnimatedVisibility(
            visible = collapsed,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            AppText(
                title,
                AppType.body,
                Modifier
                    .background(Tokens.card, Tokens.pillShape)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    AppText(
        label,
        AppType.label,
        Modifier.padding(start = Tokens.pageGutter, end = Tokens.pageGutter, top = 24.dp, bottom = 4.dp),
        color = Tokens.dimText,
    )
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String?,
    trailing: (@Composable () -> Unit)?,
    onClick: (() -> Unit)?,
    tag: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Tokens.pageGutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f)) {
            AppText(title, AppType.body)
            if (description != null) AppText(description, AppType.label, color = Tokens.dimText)
        }
        if (trailing != null) trailing()
    }
}
