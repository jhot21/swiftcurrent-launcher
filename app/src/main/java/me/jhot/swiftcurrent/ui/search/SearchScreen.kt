package me.jhot.swiftcurrent.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.search.Action
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.SearchEngine
import me.jhot.swiftcurrent.search.SearchResult
import me.jhot.swiftcurrent.shortcuts.ShortcutAccess
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.RowBounds
import me.jhot.swiftcurrent.ui.design.SheetCard
import me.jhot.swiftcurrent.ui.design.SheetScaffold
import me.jhot.swiftcurrent.ui.design.Tokens
import me.jhot.swiftcurrent.ui.design.gearGlyph
import me.jhot.swiftcurrent.ui.design.swipeRight

private val CloseDragThreshold = 96.dp

/**
 * Search over [engine]. Results arrive in the engine's rank order (apps, shortcuts, contacts).
 *
 * Closing: back, or a clear downward drag. The drag counts either on the field area, or on the
 * results only for the part the list could not scroll itself (the list is at the top), so
 * scrolling through results never closes the screen.
 *
 * [Action.perform] is blocking IPC, so it runs on [dispatcher]; the outcome is handled on the
 * main thread: Launched reports the pick and closes, Failed shows the message here.
 */
@Composable
fun SearchScreen(
    engine: SearchEngine,
    access: ShortcutAccess,
    onClose: () -> Unit,
    contactsDenied: Boolean = false,
    refresh: () -> Unit = {},
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    onOpenSettings: () -> Unit = {},
    // Opens search's own settings page from a button beside the field. Null: no button.
    onSettings: (() -> Unit)? = null,
    // The snapshot test turns the blinking cursor off so the capture is deterministic.
    showCursor: Boolean = true,
    // A swipe to the right on an app result: show that app's menu. Null leaves results with only a tap.
    onResultMenu: ((SearchResult, Rect) -> Unit)? = null,
    // A launch from a result succeeded: the result that owns the action, the action that ran, and the query as typed.
    onPicked: (SearchResult, Action, String) -> Unit = { _, _, _ -> },
    // The owner confirmed hiding one way to reach a contact: the contact's result and the action; the list is asked again once it returns. Null: nothing can be hidden.
    onHideAction: (suspend (SearchResult, Action) -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // What the list shows, as plain values. Each answer from the engine is a fresh set of objects, equal
    // or not, so the rows are built from these instead: a row whose model is unchanged is skipped.
    var rows by remember { mutableStateOf(emptyList<RowModel>()) }
    // The engine's actual results, for running them. Not state: nothing draws from it.
    val latest = remember { LatestResults() }
    var error by remember { mutableStateOf<String?>(null) }
    // The contact method a long press asked about, until the owner answers; and a count that asks the engine again after a hide.
    var pendingHide by remember { mutableStateOf<Pair<SearchResult, Action>?>(null) }
    var hides by remember { mutableIntStateOf(0) }
    val close by rememberUpdatedState(onClose)
    val picked by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val threshold = with(LocalDensity.current) { CloseDragThreshold.toPx() }

    BackHandler { close() }

    LaunchedEffect(Unit) {
        refresh()
        focus.requestFocus()
        keyboard?.show()
    }
    // The keyboard would cover the sheet's buttons, so it steps aside while the owner is asked, then comes back.
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(pendingHide) {
        if (pendingHide != null) {
            asked = true
            keyboard?.hide()
        } else if (asked) {
            asked = false
            keyboard?.show()
        }
    }
    // A new keystroke restarts this effect, which cancels the previous collection.
    LaunchedEffect(query, hides) {
        try {
            engine.results(query).collect { found ->
                latest.list = found
                latest.forQuery = query
                latest.version++
                val next = found.mapIndexed { index, r -> RowModel.of(r, opensOnEnter = index == 0) }
                // An answer that looks the same as the last one (a source answering again, a keystroke that
                // changes nothing) leaves the list untouched.
                if (next != rows) rows = next
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            latest.list = emptyList()
            latest.forQuery = query
            latest.version++
            rows = emptyList()
        }
    }

    // Set and read on the main thread only: a second tap must not start a second launch.
    var launching by remember { mutableStateOf(false) }
    val failedText = stringResource(R.string.search_launch_failed)

    fun fail(message: String) {
        error = message
    }

    // [owner] is the result whose row the action came from: for a shortcut folded onto a contact, the contact.
    fun run(owner: SearchResult, action: Action) {
        if (launching) return
        launching = true
        val typed = query
        scope.launch {
            try {
                when (val out = withContext(dispatcher) { action.perform() }) {
                    LaunchOutcome.Launched -> {
                        picked(owner, action, typed)
                        close()
                    }
                    is LaunchOutcome.Failed -> fail(out.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Binder calls can throw (SecurityException, DeadObjectException); show it, never crash.
                // The detail is for the log: exception text is not written for the person holding the phone.
                android.util.Log.w("SearchScreen", "launching a result failed", e)
                fail(failedText)
            } finally {
                launching = false
            }
        }
    }

    // One pair of callbacks for the life of the screen, so a row's parameters never change just because the
    // screen recomposed. They look the result up when tapped, so they always run the current one.
    val runNow by rememberUpdatedState<(SearchResult, Action) -> Unit>({ r, a -> run(r, a) })
    val onPrimary = remember { { id: String -> latest.list.firstOrNull { it.id == id }?.let { runNow(it, it.primary) }; Unit } }
    LaunchedEffect(latest.version) {
        if (latest.forQuery == query) {
            val pending = latest.pendingEnter
            latest.pendingEnter = null
            if (pending == query) latest.list.firstOrNull()?.let { runNow(it, it.primary) }
        }
    }
    val menuNow by rememberUpdatedState(onResultMenu)
    val onMenu = remember { { id: String, bounds: Rect -> latest.list.firstOrNull { it.id == id }?.let { menuNow?.invoke(it, bounds) }; Unit } }
    val onAction = remember {
        { id: String, index: Int ->
            latest.list.firstOrNull { it.id == id }?.let { r -> r.secondary.getOrNull(index)?.let { runNow(r, it) } }
            Unit
        }
    }

    val holdNow by rememberUpdatedState(onHideAction)
    val onHold = remember {
        { id: String, index: Int ->
            latest.list.firstOrNull { it.id == id }?.let { r ->
                // Only a way with its own id can be told apart later, so only that can be hidden.
                r.secondary.getOrNull(index)?.takeIf { it.recentId != null }?.let { pendingHide = r to it }
            }
            Unit
        }
    }

    val tracker = remember(threshold) { PullToCloseTracker(threshold) }
    val listDrag = remember(tracker) {
        object : NestedScrollConnection {
            // Post-scroll: `available` is what the list left over, so it scrolled `consumed`.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput &&
                    tracker.onDrag(consumed.y + available.y, consumed.y)
                ) {
                    close()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                tracker.onFlingEnd()
                return Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Tokens.black)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = Tokens.pageGutter),
        ) {
            Column(
                Modifier
                    .testTag("search-header")
                    .pointerInput(threshold) {
                        // Watches on the Initial pass without consuming, so the field keeps its own taps.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var total = 0f
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                total += change.position.y - change.previousPosition.y
                                if (total >= threshold) {
                                    close()
                                    break
                                }
                            }
                        }
                    },
            ) {
                Row(Modifier.padding(top = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(Tokens.pillShape)
                            .background(Tokens.card)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        if (query.isEmpty()) {
                            AppText(stringResource(R.string.search_hint), style = AppType.body, color = Tokens.dimText)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                error = null
                            },
                            singleLine = true,
                            textStyle = AppType.body.copy(color = Tokens.text),
                            cursorBrush = SolidColor(if (showCursor) Tokens.text else Color.Transparent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            // The keyboard's Search key opens the top result, the one marked with the enter hint.
                            keyboardActions = KeyboardActions(onSearch = {
                                // The results on hand answer the query on screen, or they are the last query's: then wait for the right ones.
                                if (latest.forQuery == query) latest.list.firstOrNull()?.let { run(it, it.primary) } else latest.pendingEnter = query
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search-field"),
                        )
                    }
                    if (onSettings != null) SettingsButton(onSettings)
                }
                error?.let {
                    AppText(
                        it, style = AppType.caption, color = Tokens.text,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp).testTag("search-error"),
                    )
                }
                if (access == ShortcutAccess.NotDefaultHome) {
                    AppText(
                        stringResource(R.string.search_shortcuts_banner), style = AppType.caption, color = Tokens.dimText,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp).testTag("shortcuts-banner"),
                    )
                }
                if (contactsDenied) {
                    Row(Modifier.padding(start = 20.dp, top = 8.dp).testTag("contacts-prompt"), verticalAlignment = Alignment.CenterVertically) {
                        AppText(stringResource(R.string.search_contacts_prompt), style = AppType.caption, color = Tokens.dimText)
                        AppText(
                            stringResource(R.string.search_contacts_grant), style = AppType.caption, color = Tokens.text,
                            modifier = Modifier.clickable(onClick = onOpenSettings).padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(top = 8.dp).nestedScroll(listDrag).testTag("search-results"),
                verticalArrangement = Arrangement.Top,
            ) {
                items(rows, key = { it.id }) { row -> ResultRow(row, onPrimary, onAction, onHold.takeIf { holdNow != null }, onMenu.takeIf { menuNow != null }) }
            }
        }

        pendingHide?.let { (owner, action) ->
            val hideTitle = stringResource(R.string.search_hide_title)
            SheetScaffold(title = hideTitle, onDismiss = { pendingHide = null }) {
                AppText(
                    stringResource(R.string.search_hide_body, action.label, owner.label), AppType.body,
                    modifier = Modifier.testTag("hide-dialog"),
                )
                SheetCard(
                    icon = {}, title = stringResource(R.string.search_hide_yes),
                    modifier = Modifier.testTag("hide-yes"),
                    onClick = { pendingHide = null; scope.launch { holdNow?.invoke(owner, action); hides++ } },
                )
                SheetCard(icon = {}, title = stringResource(R.string.search_hide_no), modifier = Modifier.testTag("hide-no"), onClick = { pendingHide = null })
            }
        }
    }
}

/** The engine's latest answer, kept to run results from, and which query it answered. */
private class LatestResults {
    var list: List<SearchResult> = emptyList()
    var forQuery: String = ""

    /** The query the owner pressed the keyboard's Search key for before its answer had arrived. */
    var pendingEnter: String? = null

    /** Bumped on every answer, so something can wait for the next one. */
    var version by mutableIntStateOf(0)
}

/**
 * What a result row shows, as plain values: equal when nothing visible changed, which is what lets
 * Compose skip the row. [actions] are the labels of the ways to reach a contact, only on the top
 * result; they are run by position through [LatestResults].
 */
@Immutable
private data class RowModel(
    val id: String,
    val label: String,
    val badge: String?,
    val actions: List<String>,
    /** An event's second line and whether it repeats; only on the top result, like [actions]. */
    val detail: String?,
    /** The calendar named before [detail]; it gives way (ellipsis) when long, so the date never does. */
    val calendar: String?,
    val recurring: Boolean,
    val opensOnEnter: Boolean,
    /** An app result has a menu (its shortcuts) a swipe to the right shows. */
    val hasMenu: Boolean,
) {
    companion object {
        fun of(r: SearchResult, opensOnEnter: Boolean) = RowModel(
            id = r.id,
            // An event past the top row names its calendar before its title, so a long title cannot hide it.
            label = if (!opensOnEnter && r.prefix != null) "${r.prefix}: ${r.label}" else r.label,
            badge = r.badge,
            actions = if (opensOnEnter) r.secondary.map { it.label } else emptyList(),
            detail = if (opensOnEnter) r.detail else null,
            calendar = if (opensOnEnter && r.detail != null) r.prefix else null,
            recurring = opensOnEnter && r.recurring,
            opensOnEnter = opensOnEnter,
            hasMenu = r.kind == Kind.App,
        )
    }
}

/** Marks an event that repeats; its words for a screen reader come from a string resource. */
private const val REPEAT_MARK = "\u21bb"

/** Between an event's calendar and its date, as in Home's agenda line. */
private const val DETAIL_SEPARATOR = " \u00b7 "

/** An action (an event's address can be long) never grows past this, so it ends in an ellipsis instead of wrapping. */
private val ACTION_MAX_WIDTH = 280.dp

/** Told each time a result row's body runs, so a test can see which rows a keystroke re-rendered. Null (nothing) in the app. */
internal val LocalRowRenders = staticCompositionLocalOf<(() -> Unit)?> { null }

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(row: RowModel, onPrimary: (String) -> Unit, onAction: (String, Int) -> Unit, onHold: ((String, Int) -> Unit)?, onMenu: ((String, Rect) -> Unit)?) {
    val bounds = remember { RowBounds() }
    val renders = LocalRowRenders.current
    if (renders != null) androidx.compose.runtime.SideEffect { renders() }
    val showMenuLabel = stringResource(R.string.search_show_menu)
    val hideLabel = stringResource(R.string.search_hide_action)
    val repeatsLabel = stringResource(R.string.search_event_repeats)
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("result-${row.id}")
            .onGloballyPositioned(bounds::track)
            .clickable { onPrimary(row.id) }
            .then(if (row.hasMenu && onMenu != null) Modifier.swipeRight { onMenu(row.id, bounds.now()) }.menuAction(showMenuLabel) { onMenu(row.id, bounds.now()) } else Modifier)
            .padding(top = 12.dp, bottom = if (row.actions.isNotEmpty()) 4.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AppText(row.label, style = AppType.body, color = Tokens.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                row.badge?.let {
                    AppText(it, style = AppType.caption, color = Tokens.dimText)
                }
                row.detail?.let { detail ->
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        row.calendar?.let { calendar ->
                            AppText(
                                calendar, style = AppType.caption, color = Tokens.dimText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).testTag("calendar-${row.id}"),
                            )
                            AppText(DETAIL_SEPARATOR, style = AppType.caption, color = Tokens.dimText)
                        }
                        // Never given less room than it needs: only the calendar's name above is allowed to shrink.
                        AppText(detail, style = AppType.caption, color = Tokens.dimText, maxLines = 1, modifier = Modifier.testTag("detail-${row.id}"))
                        if (row.recurring) {
                            AppText(
                                REPEAT_MARK, style = AppType.caption, color = Tokens.dimText,
                                modifier = Modifier.padding(start = 6.dp).testTag("repeats-${row.id}").semantics { contentDescription = repeatsLabel },
                            )
                        }
                    }
                }
            }
            // Quiet cue for what the keyboard's Search key will launch.
            if (row.opensOnEnter) {
                AppText(stringResource(R.string.search_enter_hint), style = AppType.body, color = Tokens.dimText, modifier = Modifier.testTag("enter-hint").padding(horizontal = 10.dp))
            }
        }
        // Only the top result spells out how to reach it: with several people matching, one line each
        // keeps them easy to tell apart. A contact's actions get their own line, so a long name keeps its width.
        if (row.actions.isNotEmpty()) {
            FlowRow(Modifier.testTag("actions-${row.id}")) {
                row.actions.forEachIndexed { index, label ->
                    AppText(
                        label, style = AppType.label, color = Tokens.dimText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = ACTION_MAX_WIDTH)
                            .testTag("action-${row.id}-$label")
                            .combinedClickable(onLongClick = onHold?.let { hold -> { hold(row.id, index) } }, onClick = { onAction(row.id, index) })
                            .then(if (onHold != null) Modifier.semantics { customActions = listOf(CustomAccessibilityAction(hideLabel) { onHold(row.id, index); true }) } else Modifier)
                            .padding(end = 20.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

/** A long press and a swipe are not discoverable, so the same thing is offered as an accessibility action. */
private fun Modifier.menuAction(label: String, action: () -> Unit): Modifier =
    semantics { customActions = listOf(CustomAccessibilityAction(label) { action(); true }) }

/** A cog beside the field that opens search's settings; its touch area is a full 48dp even though the ring is small. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val label = stringResource(R.string.search_settings)
    Box(
        Modifier.size(48.dp).testTag("search-settings")
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Spacer(Modifier.size(24.dp).gearGlyph(Tokens.dimText, 1.5.dp))
    }
}
