package me.jhot.swiftcurrent.ui.design

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import me.jhot.swiftcurrent.R

// One variable Nunito file, restricted to the three weights the design uses.
@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = FontFamily(
    Font(
        R.font.nunito,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    ),
)

private val thin = nunito(200)
private val light = nunito(300)
private val regular = nunito(400)

// The only place a text size may be written (TypeScaleTest enforces it).
object AppType {
    val clock = TextStyle(fontFamily = thin, fontWeight = FontWeight(200), fontSize = 76.sp, lineHeight = 84.sp)
    val title = TextStyle(fontFamily = light, fontWeight = FontWeight(300), fontSize = 40.sp, lineHeight = 48.sp)
    val body = TextStyle(fontFamily = regular, fontWeight = FontWeight(400), fontSize = 18.sp, lineHeight = 24.sp)
    val label = TextStyle(fontFamily = regular, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp)

    // Section letters in the app list. Bold is synthesized from the 400 face, so no fourth
    // Nunito weight ships.
    val heading = body.copy(fontWeight = FontWeight.Bold)
    val caption = TextStyle(fontFamily = regular, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
fun AppText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Tokens.text,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(text, modifier, style.copy(color = color, textAlign = textAlign), overflow = overflow, maxLines = maxLines)
}
