package me.jhot.swiftcurrent.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.Renames
import me.jhot.swiftcurrent.shortcuts.WebLinks
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.SheetScaffold
import me.jhot.swiftcurrent.ui.design.Tokens

private const val MAX_URL_LENGTH = 2048

/**
 * The form behind a browser's "Web link" row. [onSave] gets the address and name as typed: the caller parses
 * the address again, this sheet only keeps Save off until it parses. [clipboardUrl] prefills the address when
 * it is a web address already, so a copied link is one tap away. A blank name is reported as blank and the
 * caller falls back to the host, which the name field shows as its hint.
 */
@Composable
fun WebLinkSheet(browserLabel: String, clipboardUrl: String?, onSave: (url: String, name: String) -> Unit, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(clipboardUrl?.trim()?.takeIf(WebLinks::isWebAddress).orEmpty()) }
    var name by rememberSaveable { mutableStateOf("") }
    val link = WebLinks.parse(url)
    val canSave = link != null
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun save() {
        if (canSave) onSave(url, name)
    }

    SheetScaffold(title = stringResource(R.string.web_link_title, browserLabel), onDismiss = onDismiss) {
        Field(
            value = url, onChange = { url = it.filterNot { c -> c.isISOControl() }.take(MAX_URL_LENGTH) },
            hint = stringResource(R.string.web_link_url_hint), tag = "web-link-url", keyboardType = KeyboardType.Uri,
            ime = ImeAction.Next, onDone = {}, modifier = Modifier.focusRequester(focus),
        )
        if (url.isNotBlank() && link == null) {
            AppText(stringResource(R.string.web_link_error), AppType.label, Modifier.testTag("web-link-error").padding(start = 4.dp), color = Tokens.error)
        }
        Field(
            value = name, onChange = { name = Renames.limit(it) },
            hint = link?.host ?: stringResource(R.string.web_link_name_hint), hintTag = if (link != null) "web-link-name-hint" else null,
            tag = "web-link-name", keyboardType = KeyboardType.Text, ime = ImeAction.Done, onDone = ::save,
        )
        Box(
            Modifier.fillMaxWidth().testTag("web-link-save").alpha(if (canSave) 1f else 0.4f)
                .background(Tokens.text, Tokens.pillShape)
                .clickable(enabled = canSave, role = Role.Button, onClick = ::save)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { AppText(stringResource(R.string.web_link_save), AppType.body, color = Tokens.black) }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    tag: String,
    keyboardType: KeyboardType,
    ime: ImeAction,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    hintTag: String? = null,
) {
    Box(Modifier.fillMaxWidth().background(Tokens.card, Tokens.cardShape).padding(horizontal = 20.dp, vertical = 14.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = AppType.body.copy(color = Tokens.text),
            cursorBrush = SolidColor(Tokens.text),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ime),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = modifier.fillMaxWidth().testTag(tag),
        )
        if (value.isEmpty()) {
            AppText(hint, AppType.body, if (hintTag != null) Modifier.testTag(hintTag) else Modifier, color = Tokens.dimText)
        }
    }
}
