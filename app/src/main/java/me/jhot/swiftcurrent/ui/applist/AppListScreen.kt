package me.jhot.swiftcurrent.ui.applist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.catalog.Section
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.DotRow
import me.jhot.swiftcurrent.ui.design.RowBounds
import me.jhot.swiftcurrent.ui.design.Tokens
import me.jhot.swiftcurrent.ui.design.searchGlyph
import me.jhot.swiftcurrent.ui.design.swipeRight
import me.jhot.swiftcurrent.ui.edge.ScrubberExclusion

private const val AWAITING_JUMP_MAX_MS = 150L

/** How long closing the list takes to slide it off to the right; a starting value, judged on a device. */
internal const val LIST_SLIDE_MS = 180

/**
 * The opacity of a section's rows: the touched letter's block always shows; the others are hidden at once by a press
 * and then follow [reveal] after the lift. With no touched letter (the star, or '#' without apps) nothing is hidden.
 */
internal fun othersAlphaFor(scrubbed: Char?, section: Char, pressed: Boolean, reveal: Float): Float =
    if (scrubbed != null && scrubbed != section) (if (pressed) 0f else reveal) else 1f

/** How long the other letters take to fade in once the finger lifts from the alphabet; a starting value, judged on a device. */
internal const val REVEAL_MS = 160

/**
 * The full app list: sticky letter headers, rows styled like the home favorites, the reserved
 * scrubber strip on the right and a floating search button just left of it. A scrubber jump
 * centres the letter's block (header and apps) in the visible area at once, or puts its header at the top when the block is not shorter than that area; no animated scroll. While a finger is down only that block is drawn. Back, or lifting on the
 * scrubber's star, slides the list off to the right over [LIST_SLIDE_MS] and then calls
 * [onClose]; the star is an action there, not a jump target. The list ends past Z with a cog row,
 * the other way to settings.
 *
 * [edgeScrub] is the finger of an edge swipe on Home, which drives the scrubber from the first
 * frame: the list opens on touch-down, already scrolled to the letter under the finger.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppListScreen(
    sections: List<Section>,
    onLaunch: (AppInfo) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onScrubTap: (Char) -> Unit,
    onClose: () -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    insets: WindowInsets = WindowInsets.systemBars,
    haptics: Boolean = true,
    // When given, plays the scrub tick (its strength is the owner's choice); otherwise the system's own feedback does.
    onTick: (() -> Unit)? = null,
    edgeScrub: EdgeScrub? = null,
    // A long press on a row; the caller gives the feedback and opens the actions.
    onLongPress: (AppInfo) -> Unit = {},
    /** A drag to the right on a row; [Rect] is the row's bounds in root coordinates, so the menu can line up with it. */
    onSwipeRight: (AppInfo, Rect) -> Unit = { _, _ -> },
    // Hoisted so a test can read the reveal; the screen is its only owner otherwise.
    scrub: ScrubState = remember { ScrubState() },
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val hapticsOn by rememberUpdatedState(haptics)
    val tick by rememberUpdatedState(onTick)
    val letters = remember(sections) { scrubberLetters(sections) }
    // One header plus its rows per section; '#' falls back to the top.
    val headerIndex = remember(sections) {
        var i = 0
        buildMap { sections.forEach { put(it.key, SectionSpan(i, it.apps.size)); i += 1 + it.apps.size } }
    }
    // Jumps are queued and conflated: a finger from Home drives the scrubber before the list is
    // composed, so scrolling inline could land late and override a newer letter.
    val jumps = remember { Channel<Char>(Channel.CONFLATED) }
    // Measured, never assumed: the visible area is the list's own viewport, so rotation, font scale and a short screen all follow.
    val viewportPx = remember { mutableIntStateOf(0) }
    // While a finger is down only this letter's block is drawn; null when the letter has no section ('#' without apps).
    var scrubbedKey by remember { mutableStateOf<Char?>(null) }
    // The snap back on release was the "appears instantly" moment. A press hides the others at once; a release fades them in.
    // The list opens from a press (the edge touch from Home), so this is also the list's opening animation.
    // Observed with snapshotFlow, not read in the body, so a press does not recompose the screen.
    LaunchedEffect(scrub) {
        snapshotFlow { scrub.pressed to scrub.releases }.collectLatest { (pressed, releases) ->
            if (pressed) {
                scrub.othersAlpha.snapTo(0f)
            } else if (releases > 0) {
                scrub.othersAlpha.snapTo(0f)
                scrub.othersAlpha.animateTo(1f, tween(REVEAL_MS))
            }
        }
    }
    val rowPx = with(density) { Tokens.rowHeight.roundToPx() }
    val listState by rememberUpdatedState(state)
    // A finger from Home opens the list before its first jump has landed, so the list would show at the top for a
    // frame. Keep it unseen until that jump lands; a press on the star or cog sends no jump, so a short timeout lets go too.
    var awaitingJump by remember { mutableStateOf(edgeScrub?.active == true) }
    LaunchedEffect(jumps) {
        for (c in jumps) {
            val span = headerIndex[c]
            if (c == '#' || span == null) {
                listState.scrollToItem(0)
            } else {
                val block = ScrubTarget.blockHeight(rowPx, rowPx, span.rows)
                // A negative offset past the start of the list is clamped by the scroll limit, which is what stops the first and last letters.
                listState.scrollToItem(span.header, ScrubTarget.offset(block, viewportPx.intValue))
            }
            awaitingJump = false
        }
    }
    LaunchedEffect(Unit) {
        if (awaitingJump) {
            delay(AWAITING_JUMP_MAX_MS)
            awaitingJump = false
        }
    }
    val scrubTap by rememberUpdatedState(onScrubTap)
    val close by rememberUpdatedState(onClose)

    // 0 is in place, 1 is fully off to the right.
    val slide = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    // Always on: a second Back mid-slide must be swallowed, not fall through to the activity. Setting the flag twice is harmless.
    BackHandler { closing = true }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        // Still held unseen for a first jump (a quick tap from Home): there is nothing on screen to slide.
        if (!awaitingJump) slide.animateTo(1f, tween(LIST_SLIDE_MS))
        close()
    }

    val showActions = stringResource(R.string.applist_show_actions)
    val showMenu = stringResource(R.string.search_show_menu)
    val swipe by rememberUpdatedState(onSwipeRight)
    val topInset = with(density) { insets.getTop(density).toDp() }
    val bottomInset = with(density) { insets.getBottom(density).toDp() }
    // The cog lines up with the search button's centre, as in the reference.
    val trackBottom = bottomInset + Tokens.searchButtonBottom +
        Tokens.searchButtonSize / 2 - ScrubberDefaults.slotHeight / 2

    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { translationX = slide.value * size.width }) {
        val stripWidth = maxWidth * ScrubberDefaults.STRIP_FRACTION
        val listBottomPadding = bottomInset + Tokens.searchButtonBottom + Tokens.searchButtonSize
        viewportPx.intValue = with(density) { (maxHeight - topInset - listBottomPadding).roundToPx() }.coerceAtLeast(0)

        Box(Modifier.matchParentSize().background(Tokens.surface)) {
            LazyColumn(
                Modifier.fillMaxSize().padding(top = topInset).graphicsLayer { this.alpha = if (awaitingJump) 0f else 1f }.testTag("applist"),
                state = state,
                contentPadding = PaddingValues(
                    end = stripWidth,
                    bottom = listBottomPadding,
                ),
            ) {
                sections.forEach { section ->
                    // Read inside graphicsLayer, so a finger moving over the strip redraws instead of recomposing the list.
                    val onlyHere: GraphicsLayerScope.() -> Unit = {
                        val key = scrubbedKey
                        this.alpha = othersAlphaFor(key, section.key, scrub.pressed, scrub.othersAlpha.value)
                    }
                    stickyHeader(key = "h-${section.key}") {
                        Box(
                            Modifier.fillMaxWidth().height(Tokens.rowHeight).graphicsLayer(onlyHere).background(Tokens.surface)
                                .padding(horizontal = Tokens.homeGutter).testTag("header-${section.key}"),
                            contentAlignment = Alignment.CenterStart,
                        ) { AppText(section.key.toString(), AppType.heading) }
                    }
                    items(section.apps, key = { it.component }) { app ->
                        val bounds = remember { RowBounds() }
                        DotRow(
                            app.label,
                            Modifier.fillMaxWidth().height(Tokens.rowHeight).graphicsLayer(onlyHere).testTag("row-${app.component}")
                                .onGloballyPositioned(bounds::track)
                                .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
                                .swipeRight { swipe(app, bounds.now()) }
                                // A long press and a swipe are not discoverable, so the same things are offered as accessibility actions.
                                .semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction(showActions) { onLongPress(app); true },
                                        CustomAccessibilityAction(showMenu) { swipe(app, bounds.now()); true },
                                    )
                                }
                                .padding(start = Tokens.homeGutter + Tokens.rowDotInset, end = Tokens.homeGutter),
                            archived = app.archived,
                            archivedTag = "archived-${app.component}",
                            archivedGap = Tokens.listArchivedGap,
                        )
                    }
                }
                item(key = "cog") {
                    Box(
                        Modifier.fillMaxWidth().height(Tokens.rowHeight).testTag("list-cog")
                            .clickable(onClick = onSettings).padding(horizontal = Tokens.homeGutter),
                        contentAlignment = Alignment.CenterStart,
                    ) { AppText(stringResource(R.string.applist_settings), AppType.body, color = Tokens.dimText) }
                }
            }

            SearchButton(
                scrub,
                onSearch,
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = stripWidth, bottom = bottomInset + Tokens.searchButtonBottom),
            )
        }

        Scrubber(
            letters = letters,
            topPadding = topInset,
            bottomPadding = trackBottom,
            onJump = { c ->
                // Fires on the press and on every letter change while dragging, which is the tick we want.
                if (hapticsOn) tick?.invoke() ?: haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scrubbedKey = c.takeIf { headerIndex.containsKey(it) }
                jumps.trySend(c)
            },
            onPress = { c -> scrubTap(c) },
            onCog = onSettings,
            // The star is the way back to favorites: the same slide Back runs, then onClose.
            onStar = { closing = true },
            modifier = Modifier.align(Alignment.TopEnd).width(stripWidth).systemGestureExclusion { c ->
                val b = IntRect(0, 0, c.size.width, c.size.height)
                val r = ScrubberExclusion.rects(b, ScrubberExclusion.CAP_DP, density.density).firstOrNull()
                if (r == null) Rect.Zero else Rect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
            },
            state = scrub,
            external = edgeScrub,
        )
    }
}

/** Faded out while a scrub is pressed, so the bent letters never cross it; reading [scrub] here keeps that recomposition local. */
@Composable
private fun SearchButton(scrub: ScrubState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = !scrub.pressed,
        modifier = modifier,
        enter = fadeIn(tween(ScrubberDefaults.SEARCH_FADE_MS)),
        exit = fadeOut(tween(ScrubberDefaults.SEARCH_FADE_MS)),
    ) { SearchButtonFace(onClick) }
}

@Composable
private fun SearchButtonFace(onClick: () -> Unit) {
    Box(
        Modifier.size(Tokens.searchButtonSize).background(Tokens.dot, CircleShape)
            .clickable(onClick = onClick).testTag("applist-search"),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(Modifier.size(Tokens.searchIconSize).searchGlyph(Tokens.card, 2.dp))
    }
}

/** Where a section sits in the list: the index of its header and how many rows follow it. */
private class SectionSpan(val header: Int, val rows: Int)
