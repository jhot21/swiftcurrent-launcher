package me.jhot.swiftcurrent.ui.home

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.ui.design.SheetCard
import me.jhot.swiftcurrent.ui.design.SheetScaffold

/** Asks before another app's shortcut is pinned. Back, the scrim and a drag all count as Cancel. */
@Composable
fun PinConfirmDialog(label: String, icon: Drawable?, onAdd: () -> Unit, onCancel: () -> Unit) {
    val shown = label.trim().ifEmpty { stringResource(R.string.label_unknown_shortcut) }
    val bitmap = remember(icon) { icon?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap() }
    SheetScaffold(title = stringResource(R.string.pin_title), onDismiss = onCancel) {
        SheetCard(
            icon = bitmap?.let { b -> { Image(b, contentDescription = null, modifier = Modifier.size(32.dp)) } },
            title = shown,
        )
        SheetCard(icon = null, title = stringResource(R.string.pin_add), onClick = onAdd, modifier = Modifier.testTag("pin-add"))
        SheetCard(icon = null, title = stringResource(R.string.pin_cancel), onClick = onCancel, modifier = Modifier.testTag("pin-cancel"))
    }
}

private const val ICON_PX = 96
