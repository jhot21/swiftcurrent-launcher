package me.jhot.swiftcurrent.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Tokens {
    val black = Color(0xFF000000)
    val surface = Color(0xFF121212)
    val sheet = Color(0xFF161616)
    val card = Color(0xFF2C2C2C)
    val text = Color(0xFFF2F2F2)
    val dimText = Color(0xFF9A9A9A)
    val dot = Color(0xFFC9C9C9)

    /** Text and borders that say something is wrong with what the owner set up or typed. */
    val error = Color(0xFFE5534B)

    val sheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val cardShape = RoundedCornerShape(16.dp)
    val pillShape = RoundedCornerShape(percent = 50)

    val pageGutter = 24.dp

    // Home and the app list, measured from Niagara's captures (1080px at 420dpi): text starts at 48dp, the dot at
    // 63dp and a row's label at 111dp. Settings and editors keep the narrower pageGutter.
    val homeGutter = 48.dp
    val rowDotInset = 15.dp
    val rowDotGap = 38.dp

    /** One line of Home or the app list, and the dot that leads every such row (and the editor's). */
    val rowHeight = 56.dp
    val dotSize = 10.dp

    /** The "download again" mark beside an archived app, and its distance from the label on Home. */
    val archivedIconSize = 14.dp
    val archivedIconGap = 12.dp

    /** The app list leaves more room before the mark than Home does. */
    val listArchivedGap = 24.dp

    // The app list's floating search button.
    val searchButtonSize = 56.dp
    val searchButtonBottom = 24.dp
    val searchIconSize = 22.dp

    /** Space the editor list puts between rows; the drag handle needs it to know the row pitch. */
    val editorRowGap = 16.dp

    // Pop-up card (swipe right on an item): a floating card, not a sheet.
    val popupShape = cardShape
    val popupScrim = Color(0x66000000)
    val popupTopOffset = 96.dp
    val popupRule = Color(0xFF3A3A3A)
    val popupCardPadding = 12.dp
    val popupRowPaddingH = pageGutter
    val popupRowPaddingV = 12.dp
    val popupDotGap = 24.dp
}
