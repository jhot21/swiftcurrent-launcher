package me.jhot.swiftcurrent.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R

/** The grey dot that leads a row. */
@Composable
fun Dot(modifier: Modifier = Modifier) {
    Spacer(modifier.size(Tokens.dotSize).background(Tokens.dot, CircleShape))
}

/**
 * A dot and a label on one line, the shape of a Home favorite, an app list row and a pop-up line.
 * The caller's [modifier] carries the row's size, padding and click. [archived] adds the "download
 * again" mark after the label, tagged [archivedTag] for tests. [ellipsize] keeps a long label to one
 * line; a pop-up line wraps instead.
 */
@Composable
fun DotRow(
    label: String,
    modifier: Modifier = Modifier,
    dotGap: Dp = Tokens.rowDotGap,
    archived: Boolean = false,
    archivedTag: String? = null,
    archivedGap: Dp = Tokens.archivedIconGap,
    ellipsize: Boolean = true,
    color: Color = Tokens.text,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Dot()
        Spacer(Modifier.width(dotGap))
        if (ellipsize) {
            AppText(label, AppType.body, Modifier.weight(1f, fill = false), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            AppText(label, AppType.body, color = color)
        }
        if (archived) ArchivedMark(archivedTag, archivedGap)
    }
}

@Composable
private fun RowScope.ArchivedMark(tag: String?, gap: Dp) {
    val description = stringResource(R.string.applist_archived)
    Spacer(Modifier.width(gap))
    Canvas(
        Modifier.size(Tokens.archivedIconSize)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .semantics { contentDescription = description },
    ) { download(Tokens.dimText, 1.5.dp.toPx()) }
}
