package me.jhot.swiftcurrent.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R

/**
 * Full-page editor: counter pill, huge thin title, the caller's rows, and a floating Done.
 * Editors apply changes live, so Done only closes the page.
 */
@Composable
fun EditorPage(
    title: String,
    counter: String?,
    onDone: () -> Unit,
    state: LazyListState = rememberLazyListState(),
    // The editor reserves the scrubber strip on the right; other pages keep the plain gutter.
    endPadding: Dp = Tokens.pageGutter,
    content: LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Tokens.surface)) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            // Room under the last row so the floating Done never covers it.
            contentPadding = PaddingValues(start = Tokens.pageGutter, end = endPadding, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Tokens.editorRowGap),
        ) {
            if (counter != null) {
                item {
                    AppText(
                        counter,
                        AppType.body,
                        Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                            .background(Tokens.black, Tokens.pillShape)
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        color = Tokens.dimText,
                    )
                }
            }
            item { AppText(title, AppType.title, Modifier.padding(top = 16.dp, bottom = 8.dp)) }
            content()
        }
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = endPadding, bottom = 24.dp)
                .background(Tokens.dot, Tokens.cardShape)
                .clickable(onClick = onDone)
                .padding(horizontal = 40.dp, vertical = 18.dp),
        ) {
            AppText(stringResource(R.string.editor_done), AppType.body, color = Tokens.black)
        }
    }
}
