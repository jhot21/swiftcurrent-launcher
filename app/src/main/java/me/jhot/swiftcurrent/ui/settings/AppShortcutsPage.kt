package me.jhot.swiftcurrent.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.shortcuts.Creator
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.SettingsPage
import me.jhot.swiftcurrent.ui.design.SheetCard
import me.jhot.swiftcurrent.ui.design.SheetScaffold
import me.jhot.swiftcurrent.ui.design.Toggle
import me.jhot.swiftcurrent.ui.design.Tokens

/** What the page asks of its host; each callback may do blocking work off the main thread. */
class AppShortcutsActions(
    val onToggle: (key: String, on: Boolean) -> Unit,
    val onToggleAll: (packageName: String, on: Boolean) -> Unit,
    val onDelete: (UserShortcutRow) -> Unit,
    val onCreate: (Creator) -> Unit,
    val onGrantContacts: () -> Unit,
    val onGrantShortcuts: () -> Unit,
)

/**
 * Per-app shortcut management on the shared settings layout. [openApp] is the package whose sheet is
 * showing (null for none) and is owned by the caller, so a settings host can keep it across a
 * recreation. The sheet opens over the list of apps, which stays where it is, so closing the sheet
 * (Back, the scrim or a drag down) returns to the same place in the list. Back with no sheet calls [onBack].
 */
@Composable
fun AppShortcutsPage(
    groups: List<AppShortcutGroup>,
    sources: List<SourceRow>,
    openApp: String?,
    onOpenApp: (String?) -> Unit,
    actions: AppShortcutsActions,
    onBack: () -> Unit,
    /** The list's scroll state; the caller can hold it, and a test can read where the list is. */
    listState: LazyListState = rememberLazyListState(),
) {
    val group = groups.firstOrNull { it.packageName == openApp }
    // With a sheet open its own Back closes it; this one is the page's.
    BackHandler(enabled = group == null, onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().testTag("app-shortcuts-list")) { AppList(groups, sources, onOpenApp, actions, listState) }
        if (group != null) {
            Box(Modifier.fillMaxSize().testTag("app-shortcuts-app")) { AppSheet(group, { onOpenApp(null) }, actions) }
        }
    }
}

@Composable
private fun AppList(
    groups: List<AppShortcutGroup>,
    sources: List<SourceRow>,
    onOpenApp: (String) -> Unit,
    actions: AppShortcutsActions,
    listState: LazyListState,
) {
    val resources = LocalContext.current.resources
    val title = stringResource(R.string.app_shortcuts_title)
    val sourcesLabel = stringResource(R.string.app_shortcuts_sources)
    val appsLabel = stringResource(R.string.app_shortcuts_apps)
    val none = stringResource(R.string.app_shortcuts_none)
    val sourceNames = mapOf(
        SourceId.Apps to stringResource(R.string.app_shortcuts_source_apps),
        SourceId.Shortcuts to stringResource(R.string.app_shortcuts_source_shortcuts),
        SourceId.Contacts to stringResource(R.string.app_shortcuts_source_contacts),
    )
    val makeOwn = stringResource(R.string.app_shortcuts_make_own)
    SettingsPage(title = title, state = listState) {
        // Only a source that cannot answer yet is shown: its row is the way to grant what it lacks.
        val blocked = sources.filter { it.missingPermission }
        if (blocked.isNotEmpty()) section(sourcesLabel)
        blocked.forEach { s ->
            row(
                icon = {},
                title = sourceNames.getValue(s.id),
                trailing = { PermissionBadge(s.id) },
                onClick = if (s.id == SourceId.Shortcuts) actions.onGrantShortcuts else actions.onGrantContacts,
            )
        }
        section(appsLabel)
        if (groups.isEmpty()) row(icon = {}, title = none)
        groups.forEach { g ->
            val count = when {
                g.count == 0 -> makeOwn
                g.creators.isEmpty() -> resources.getQuantityString(R.plurals.app_shortcuts_count, g.count, g.count)
                else -> resources.getQuantityString(R.plurals.app_shortcuts_count_and_make, g.count, g.count)
            }
            row(icon = {}, title = g.appLabel, description = count, onClick = { onOpenApp(g.packageName) })
        }
    }
}

@Composable
private fun AppSheet(group: AppShortcutGroup, onDismiss: () -> Unit, actions: AppShortcutsActions) {
    val made = stringResource(R.string.app_shortcuts_made)
    val from = stringResource(R.string.app_shortcuts_from, group.appLabel)
    val addNew = stringResource(R.string.app_shortcuts_add_new)
    val toggleAll = stringResource(if (group.allOn) R.string.app_shortcuts_toggle_all_off else R.string.app_shortcuts_toggle_all_on)
    val deleteLabels = group.userMade.associate { it.id to stringResource(R.string.app_shortcuts_delete, it.label) }
    SheetScaffold(title = group.appLabel, onDismiss = onDismiss) {
        if (group.count > 0) {
            SheetCard(icon = null, title = toggleAll, onClick = { actions.onToggleAll(group.packageName, !group.allOn) })
        }
        if (group.userMade.isNotEmpty()) {
            SheetSection(made)
            group.userMade.forEach { s ->
                SheetCard(
                    icon = {
                        DeleteIcon(deleteLabels.getValue(s.id), Modifier.testTag("shortcut-delete-${s.id}")) { actions.onDelete(s) }
                    },
                    title = s.label,
                    trailing = { Toggle(s.enabled, s.label, "shortcut-toggle-${s.key}") { actions.onToggle(s.key, it) } },
                )
            }
        }
        if (group.fromApp.isNotEmpty()) {
            SheetSection(from)
            group.fromApp.forEach { s ->
                SheetCard(
                    icon = null,
                    title = s.label,
                    trailing = { Toggle(s.enabled, s.label, "shortcut-toggle-${s.key}") { actions.onToggle(s.key, it) } },
                )
            }
        }
        if (group.creators.isNotEmpty()) {
            SheetSection(addNew)
            group.creators.forEach { c ->
                SheetCard(icon = null, title = c.label, onClick = { actions.onCreate(c) })
            }
        }
    }
}

@Composable
private fun SheetSection(label: String) {
    AppText(label, AppType.label, Modifier.padding(start = 4.dp, top = 8.dp), color = Tokens.dimText)
}

@Composable
internal fun PermissionBadge(source: SourceId) {
    AppText(
        stringResource(R.string.app_shortcuts_missing_permission),
        AppType.caption,
        Modifier
            .testTag("permission-badge-${source.name.lowercase()}")
            .background(Tokens.card, Tokens.pillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Tokens.dot,
    )
}

@Composable
internal fun DeleteIcon(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(onClick = onClick)
            .size(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = Stroke(width = 2.dp.toPx())
            // Lid, handle and bin of a trash can.
            drawLine(Tokens.dot, Offset(size.width * 0.1f, size.height * 0.25f), Offset(size.width * 0.9f, size.height * 0.25f), w.width)
            drawLine(Tokens.dot, Offset(size.width * 0.38f, size.height * 0.1f), Offset(size.width * 0.62f, size.height * 0.1f), w.width)
            drawRoundRect(
                Tokens.dot, Offset(size.width * 0.2f, size.height * 0.35f),
                Size(size.width * 0.6f, size.height * 0.55f), CornerRadius(3.dp.toPx()), w,
            )
        }
    }
}
