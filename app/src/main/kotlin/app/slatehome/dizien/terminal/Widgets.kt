package app.slatehome.dizien.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "Amber Slate" - same palette as DiZien Launcher. */
object AS {
    val Term = Color(0xFF12100D)
    val Bg = Color(0xFF1C1814)
    val Surf = Color(0xFF26211B)
    val Surf2 = Color(0xFF322B23)
    val Text = Color(0xFFF3E9D7)
    val Sub = Color(0xFFA39682)
    val Acc = Color(0xFFFFB020)
    val Acc2 = Color(0xFFFF7A1A)
    val AccFg = Color(0xFF1C1814)
    val Line = Color(0xFF40372C)
    val Err = Color(0xFFE06C5B)
}

val Mono = FontFamily(Font(R.font.dejavu_sans_mono), Font(R.font.dejavu_sans_mono_bold, FontWeight.Bold))

@Composable
fun Txt(text: String, size: Float = 14f, color: Color = AS.Text, bold: Boolean = false, modifier: Modifier = Modifier, letter: Float = 0f, lineHeight: Float = 0f) {
    androidx.compose.foundation.text.BasicText(
        text, modifier,
        style = TextStyle(
            color = color, fontSize = size.sp, fontFamily = Mono,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = letter.sp,
            lineHeight = if (lineHeight > 0) lineHeight.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
        ),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AS.Surf)
            .border(BorderStroke(1.dp, AS.Line), RoundedCornerShape(10.dp)).padding(14.dp),
    ) { content() }
}

@Composable
fun Tile(size: Int = 44, content: @Composable () -> Unit) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(AS.Surf2), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun Btn(text: String, primary: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (primary) AS.Acc else AS.Surf2
    val fg = if (primary) AS.AccFg else AS.Text
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(6.dp)).background(if (enabled) bg else AS.Surf2)
            .border(BorderStroke(1.dp, if (primary) AS.Acc else AS.Line), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(text, 14f, if (enabled) fg else AS.Sub, bold = true) }
}

@Composable
fun Pill(text: String, filled: Boolean = true) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (filled) AS.Acc else AS.Surf2)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Txt(text, 12f, if (filled) AS.AccFg else AS.Sub, bold = true) }
}

@Composable
fun SwitchBox(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.width(52.dp).height(30.dp).clip(RoundedCornerShape(50))
            .background(if (on) AS.Acc else AS.Surf2).border(BorderStroke(1.dp, AS.Line), RoundedCornerShape(50))
            .clickable { onChange(!on) }.padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(22.dp).clip(RoundedCornerShape(50)).background(if (on) AS.AccFg else AS.Sub)) }
}

@Composable
fun ProgressBar(frac: Float, modifier: Modifier = Modifier) {
    val f = frac.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(AS.Surf2)) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(10.dp).background(Brush.horizontalGradient(listOf(AS.Acc, AS.Acc2))))
    }
}

@Composable
fun Row2(
    title: String, sub: String? = null, glyph: String = "", onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val m = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(m.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (glyph.isNotEmpty()) { Tile(40) { Txt(glyph, 17f, AS.Acc, bold = true) }; Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Txt(title, 14f, bold = true)
            if (sub != null) Txt(sub, 11.5f, AS.Sub, lineHeight = 15f)
        }
        trailing?.invoke()
    }
}

@Composable
fun Divider() { Box(Modifier.fillMaxWidth().height(1.dp).background(AS.Line)) }

@Composable
fun SectionLabel(text: String) {
    Txt(text, 12f, AS.Sub, bold = true, letter = 1.5f, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
}

fun fmtMb(b: Long) = "%.1f".format(b / 1048576.0)

fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
