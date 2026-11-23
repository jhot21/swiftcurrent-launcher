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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.Renames
import me.jhot.swiftcurrent.shortcuts.WebLink
import me.jhot.swiftcurrent.shortcuts.WebLinks
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.SheetScaffold
import me.jhot.swiftcurrent.ui.design.Tokens

private const val MAX_URL_LENGTH = 2048

/** Which wording and address rules the form uses. */
enum class LinkKind { Web, Chat }

/**
 * The form behind a browser's "Web link" row. [onSave] gets the address and name as typed: the caller parses
 * the address again, this sheet only keeps Save off until it parses. [clipboardUrl] prefills the address when
 * it is a web address already, so a copied link is one tap away. A blank name is reported as blank and the
 * caller falls back to the host, which the name field shows as its hint.
 */
@Composable
fun WebLinkSheet(
    browserLabel: String,
    clipboardUrl: String?,
    onSave: (url: String, name: String) -> Unit,
    onDismiss: () -> Unit,
    // The same form serves a Google Chat link: what differs is the wording, what counts as an address
    // (and so what a copied link may prefill), and the name the field offers once there is one.
    kind: LinkKind = LinkKind.Web,
    parse: (String) -> WebLink? = WebLinks::parse,
    isAddress: (String) -> Boolean = WebLinks::isWebAddress,
    nameHint: (WebLink) -> String = { it.host },
) {
    var url by rememberSaveable { mutableStateOf(clipboardUrl?.trim()?.takeIf(isAddress).orEmpty()) }
    var name by rememberSaveable { mutableStateOf("") }
    val link = parse(url)
    val canSave = link != null
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun save() {
        if (canSave) onSave(url, name)
    }

    val chat = kind == LinkKind.Chat
    val title = if (chat) stringResource(R.string.chat_link_title, browserLabel) else stringResource(R.string.web_link_title, browserLabel)
    SheetScaffold(title = title, onDismiss = onDismiss) {
        if (chat) {
            AppText(stringResource(R.string.chat_link_instructions), AppType.label, Modifier.testTag("chat-link-instructions").padding(start = 4.dp), color = Tokens.dimText)
        }
        val error = stringResource(if (chat) R.string.chat_link_error else R.string.web_link_error)
        val errorMessage = error.takeIf { url.isNotBlank() && link == null }
        val urlLabel = stringResource(if (chat) R.string.chat_link_url_hint else R.string.web_link_url_hint)
        Field(
            value = url, onChange = { url = it.filterNot { c -> c.isISOControl() }.take(MAX_URL_LENGTH) },
            hint = urlLabel, label = urlLabel, errorText = errorMessage, tag = "web-link-url", keyboardType = KeyboardType.Uri,
            ime = ImeAction.Next, onDone = {}, modifier = Modifier.focusRequester(focus),
        )
        if (errorMessage != null) {
            AppText(
                errorMessage, AppType.label,
                // Polite: said when it appears, without cutting off what the reader is already saying.
                Modifier.testTag("web-link-error").padding(start = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = Tokens.error,
            )
        }
        val nameLabel = stringResource(R.string.web_link_name_hint)
        Field(
            value = name, onChange = { name = Renames.limit(it) },
            // The visible hint becomes the host once the address is valid; the label does not change.
            hint = link?.let(nameHint) ?: nameLabel, label = nameLabel, hintTag = if (link != null) "web-link-name-hint" else null,
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
    label: String,
    tag: String,
    keyboardType: KeyboardType,
    ime: ImeAction,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    hintTag: String? = null,
    errorText: String? = null,
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
            modifier = modifier.fillMaxWidth().testTag(tag).semantics {
                // The visible hint disappears once text is typed, so the field's name is its own constant label.
                contentDescription = label
                if (errorText != null) error(errorText)
            },
        )
        if (value.isEmpty()) {
            AppText(hint, AppType.body, if (hintTag != null) Modifier.testTag(hintTag) else Modifier, color = Tokens.dimText)
        }
    }
}
