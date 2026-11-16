package me.jhot.swiftcurrent.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.ui.applist.Scrubber
import me.jhot.swiftcurrent.ui.applist.ScrubberDefaults
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.Dot
import me.jhot.swiftcurrent.ui.design.EditorPage
import me.jhot.swiftcurrent.ui.design.Tokens
import me.jhot.swiftcurrent.ui.design.checkGlyph
import me.jhot.swiftcurrent.ui.design.chevronGlyph
import me.jhot.swiftcurrent.ui.design.diamondGlyph
import me.jhot.swiftcurrent.ui.design.linkGlyph
import me.jhot.swiftcurrent.ui.design.pencil
import me.jhot.swiftcurrent.ui.design.plusGlyph
import me.jhot.swiftcurrent.ui.design.sparkleGlyph

// Items EditorPage puts ahead of the editor's own rows: the counter pill and the title.
private const val PAGE_PREFIX_ITEMS = 2

private val CHECK_SIZE = 24.dp
private val HANDLE_SIZE = 40.dp

// The accessibility minimum for a touch target; the row is already at least this tall.
private val RENAME_TARGET = 48.dp
private val ROW_GAP = 24.dp
private val GROUP_INDENT = 24.dp

/**
 * The favorites editor and the pop-up editor: one page over the same kinds of item. Every change
 * goes to [state], which reports it live, so [onDone] only closes (Back does the same). Sections
 * run Selected, Suggestions, the A to Z list, Shortcuts, Pop-up folders and Advanced; the right
 * strip is the app list's scrubber with icons that jump to each. There is no widget row yet.
 *
 * [onCreateFolder] runs with the new empty folder after it has been made and selected, so the caller
 * can ask for its name. [onRename] runs for a selected row's rename button or accessibility action.
 * [onAddShortcut] and
 * [onSelectHiddenApps] are handled by the caller.
 */
@Composable
fun ItemEditor(
    state: ItemEditorState,
    mode: Mode,
    onDone: () -> Unit,
    title: String = if (mode == Mode.Favorites) state.strings.titleFavorites else state.strings.titlePopup,
    ownShortcutsSubtitle: (String) -> String? = { null },
    onAddShortcut: () -> Unit = {},
    onCreateFolder: (me.jhot.swiftcurrent.core.Entry) -> Unit = {},
    onRename: (me.jhot.swiftcurrent.core.Entry) -> Unit = {},
    onSelectHiddenApps: () -> Unit = {},
    insets: WindowInsets = WindowInsets.systemBars,
) {
    BackHandler(onBack = onDone)
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Both read snapshot state, so they rebuild when the lists change and not on every other recomposition.
    val rows by remember(state) { derivedStateOf { EditorSections.rows(state) } }
    val keys by remember(state) { derivedStateOf { EditorSections.uniqueKeys(rows) } }
    val currentRows by rememberUpdatedState(rows)
    fun toggle(entry: me.jhot.swiftcurrent.core.Entry) = state.toggle(entry)

    val strings = state.strings
    val counter = when {
        state.overLimit -> strings.overLimit(state.counter)
        else -> state.counter
    }
    val letters = remember(state.candidates.apps) { EditorSections.letters(state.candidates.apps) }
    val topInset = with(density) { insets.getTop(density).toDp() }
    val bottomInset = with(density) { insets.getBottom(density).toDp() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val strip = maxWidth * ScrubberDefaults.STRIP_FRACTION
        EditorPage(title = title, counter = counter, onDone = onDone, state = listState, endPadding = strip) {
            itemsIndexed(rows, key = { i, _ -> keys[i] }) { _, row ->
                when (row) {
                    is EditorRow.Heading -> Heading(strings.heading(row.section), "editor-header-${row.section}")
                    is EditorRow.Letter -> Heading(row.ch.toString(), "editor-header-${row.ch}")
                    is EditorRow.Hint -> AppText(strings.hint(row.section), AppType.body, Modifier.padding(start = ROW_GAP + 10.dp), color = Tokens.dimText)
                    EditorRow.Boundary -> AppText(
                        strings.notShownOnHome, AppType.label,
                        Modifier.fillMaxWidth().testTag("editor-boundary").padding(vertical = 8.dp), color = Tokens.dimText,
                    )
                    is EditorRow.Selected -> SelectedRow(state, row, mode, ownShortcutsSubtitle, onRename) { toggle(it) }
                    is EditorRow.AppGroup -> GroupHeader(row, strings) { state.toggleGroup(row.app) }
                    is EditorRow.Pick -> PickRow(
                        tag = "pick-${row.section}-${row.entry.id}",
                        checked = state.isSelected(row.entry),
                        label = row.label,
                        subtitle = row.subtitle,
                        indent = if (row.grouped) GROUP_INDENT else 0.dp,
                    ) { toggle(row.entry) }
                    is EditorRow.OwnPick -> PickRow(
                        tag = "pick-own", checked = false, label = state.ownShortcutsLabel(),
                        subtitle = ownShortcutsSubtitle(row.app),
                    ) { state.toggleOwnShortcuts() }
                    is EditorRow.Action -> ActionRow(row.kind, strings.action(row.kind)) {
                        when (row.kind) {
                            ActionKind.AddShortcut -> onAddShortcut()
                            ActionKind.CreateFolder -> onCreateFolder(state.createFolder())
                            ActionKind.SelectHiddenApps -> onSelectHiddenApps()
                        }
                    }
                }
            }
        }
        Scrubber(
            letters = letters,
            topPadding = topInset,
            bottomPadding = bottomInset + 24.dp,
            onJump = { c ->
                val target = EditorSections.indexForScrub(c, currentRows)
                // Undispatched so the jump happens inside the touch event, as in the app list.
                if (target >= 0) scope.launch(start = CoroutineStart.UNDISPATCHED) { listState.scrollToItem(PAGE_PREFIX_ITEMS + target) }
            },
            onPress = {},
            onCog = {},
            modifier = Modifier.align(Alignment.TopEnd).width(strip),
            glyph = { c, color, modifier -> EditorGlyph(c, color, modifier) },
        )
    }
}

@Composable
private fun Heading(text: String, tag: String) {
    AppText(text, AppType.heading, Modifier.padding(top = 16.dp).testTag(tag))
}

@Composable
private fun Checkbox(checked: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.size(CHECK_SIZE).then(
            if (checked) Modifier.background(Tokens.text, shape) else Modifier.border(2.dp, Tokens.text, shape),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Spacer(Modifier.size(16.dp).checkGlyph(Tokens.black, 2.dp))
    }
}

@Composable
private fun Texts(label: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        AppText(label, AppType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) AppText(subtitle, AppType.label, color = Tokens.dimText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PickRow(tag: String, checked: Boolean, label: String, subtitle: String?, indent: Dp = 0.dp, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag(tag)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = indent, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked)
        Spacer(Modifier.width(ROW_GAP))
        Dot()
        Spacer(Modifier.width(ROW_GAP))
        Texts(label, subtitle, Modifier.weight(1f))
    }
}

/** An app's group of shortcuts: tapping the whole row opens or closes it, and a screen reader hears which. */
@Composable
private fun GroupHeader(group: EditorRow.AppGroup, strings: EditorStrings, onToggle: () -> Unit) {
    val state = if (group.open) strings.groupExpanded else strings.groupCollapsed
    Row(
        Modifier.fillMaxWidth().testTag("group-${group.app}")
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(CHECK_SIZE + ROW_GAP))
        Spacer(Modifier.size(Tokens.dotSize + 6.dp).chevronGlyph(Tokens.dimText, 1.6.dp, group.open))
        Spacer(Modifier.width(ROW_GAP - 3.dp))
        Texts(group.app, strings.groupSummary(group.count, group.selected), Modifier.weight(1f))
    }
}

@Composable
private fun ActionRow(kind: ActionKind, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag("action-$kind").clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(CHECK_SIZE + ROW_GAP))
        Dot()
        Spacer(Modifier.width(ROW_GAP))
        AppText(text, AppType.body)
    }
}

/**
 * A selected row. The handle drags it (committed on release, through [ReorderMath]); the move
 * up and move down accessibility actions do the same without a drag.
 */
@Composable
private fun SelectedRow(
    state: ItemEditorState,
    item: EditorRow.Selected,
    mode: Mode,
    ownShortcutsSubtitle: (String) -> String?,
    onRename: (me.jhot.swiftcurrent.core.Entry) -> Unit,
    onToggle: (me.jhot.swiftcurrent.core.Entry) -> Unit,
) {
    val row = item.row
    val index = item.index
    val count = state.rowCount
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var heightPx by remember { mutableIntStateOf(0) }
    val gapPx = with(LocalDensity.current) { Tokens.editorRowGap.toPx() }
    val beyond = state.firstHiddenIndex?.let { index >= it } ?: false
    val live = state.dragPlacement
    val stepAsideTarget = if (live == null) 0f else ReorderMath.shiftFor(index, live.from, live.target, live.pitchPx)
    // Snaps when the drag ends: the list has already reordered, so easing back would run the wrong way.
    val stepAside by animateFloatAsState(stepAsideTarget, if (live == null) snap() else tween(120), label = "step-aside")
    val id = when (row) {
        is PopupRow.Item -> row.entry.id
        is PopupRow.OwnShortcuts -> "own"
    }
    val label: String
    val subtitle: String?
    when (row) {
        is PopupRow.Item -> {
            label = state.labelOf(row.entry)
            // Conditions only mean something on Home's favorites; a favorite copied into a pop-up ignores them.
            val summary = if (mode == Mode.Favorites) conditionSummary(row.entry.conditions, state.strings) else null
            subtitle = if (state.missing(row.entry)) state.strings.notInstalled else summary
        }
        is PopupRow.OwnShortcuts -> {
            label = state.ownShortcutsLabel()
            subtitle = ownShortcutsSubtitle(row.app)
        }
    }

    val dimmed = mode == Mode.Favorites && row is PopupRow.Item && state.isDimmed(row.entry)
    Row(
        Modifier.fillMaxWidth().testTag("sel-$id")
            .onSizeChanged { heightPx = it.height }
            .zIndex(if (dragging) 1f else 0f)
            // The dragged row follows the finger; the rows it passes step aside, so the drop is previewed live.
            .graphicsLayer { translationY = if (dragging) dragY else stepAside }
            // Lifted while held: a card behind it, so it reads as separate from the rows it slides over.
            .then(if (dragging) Modifier.background(Tokens.card, Tokens.cardShape) else Modifier)
            .alpha(if (beyond || dimmed) 0.5f else 1f)
            .semantics {
                if (dimmed) stateDescription = state.strings.hiddenByConditions
                customActions = listOfNotNull(
                    CustomAccessibilityAction(state.strings.moveUp) { if (index > 0) { state.move(index, index - 1); true } else false },
                    CustomAccessibilityAction(state.strings.moveDown) { if (index < count - 1) { state.move(index, index + 1); true } else false },
                    (row as? PopupRow.Item)?.let { item -> CustomAccessibilityAction(state.strings.rename(label)) { onRename(item.entry); true } },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).testTag("toggle-sel-$id")
                .toggleable(
                    value = true, role = Role.Checkbox,
                    onValueChange = {
                        when (row) {
                            is PopupRow.Item -> onToggle(row.entry)
                            is PopupRow.OwnShortcuts -> state.toggleOwnShortcuts()
                        }
                    },
                )
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(true)
            Spacer(Modifier.width(ROW_GAP))
            Dot()
            Spacer(Modifier.width(ROW_GAP))
            Texts(label, subtitle, Modifier.weight(1f))
        }
        if (row is PopupRow.Item) {
            Box(
                Modifier.size(RENAME_TARGET).testTag("rename-$id")
                    .semantics(mergeDescendants = true) { contentDescription = state.strings.rename(label) }
                    .clickable(role = Role.Button, onClick = { onRename(row.entry) }),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(16.dp)) { pencil(Tokens.dimText, 1.4.dp.toPx()) }
            }
        }
        Box(
            Modifier.size(HANDLE_SIZE).testTag("handle-$id").background(Tokens.black, CircleShape)
                .semantics { contentDescription = state.strings.reorder }
                .pointerInput(index, count, heightPx, gapPx) {
                    // A drag the system ends by cancelling this coroutine (the row leaves composition, the keys change, a
                    // lock mid-drag) never reaches onDragEnd or onDragCancel, so the preview would stay stuck without this.
                    try {
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                state.drag = ItemEditorState.Drag(index, 0f, ReorderMath.pitch(heightPx.toFloat(), gapPx), count)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragY += amount.y
                                state.drag = ItemEditorState.Drag(index, dragY, ReorderMath.pitch(heightPx.toFloat(), gapPx), count)
                            },
                            onDragEnd = {
                                val to = ReorderMath.targetIndex(index, dragY, ReorderMath.pitch(heightPx.toFloat(), gapPx), count)
                                dragging = false
                                dragY = 0f
                                state.drag = null
                                if (to != index) state.move(index, to)
                            },
                            onDragCancel = { dragging = false; dragY = 0f; state.drag = null },
                        )
                    } finally {
                        if (dragging) { dragging = false; dragY = 0f; state.drag = null }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(14.dp)) {
                val r = 1.6.dp.toPx()
                for (x in listOf(size.width * 0.2f, size.width * 0.8f)) for (y in listOf(size.height * 0.2f, size.height * 0.8f)) {
                    drawCircle(Tokens.text, r, Offset(x, y))
                }
            }
        }
    }
}

/** Scrubber entries: letters and `#` as text, the six jump targets as small line icons. */
@Composable
private fun EditorGlyph(c: Char, color: Color, modifier: Modifier) {
    val icon = EditorSections.iconOf(c)
    if (icon == null || icon == EditorIcon.Hash) {
        AppText(c.toString(), AppType.label, modifier, color = color)
        return
    }
    val glyph = modifier.size(ScrubberDefaults.starSize)
    val w = 1.4.dp
    when (icon) {
        EditorIcon.Selected -> Spacer(glyph.checkGlyph(color, w))
        EditorIcon.Suggestions -> Spacer(glyph.sparkleGlyph(color, w))
        EditorIcon.Shortcuts -> Spacer(glyph.linkGlyph(color, w))
        EditorIcon.Folders -> Spacer(glyph.diamondGlyph(color, w))
        EditorIcon.Advanced -> Spacer(glyph.plusGlyph(color, w))
        EditorIcon.Hash -> Unit
    }
}
