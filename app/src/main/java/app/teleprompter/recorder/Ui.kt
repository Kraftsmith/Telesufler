package app.teleprompter.recorder

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale

val Ink = Color(0xFF0F131A)
val CardColor = Color(0xFF1C212B)
val CardRaised = Color(0xFF262C38)
val Line = Color(0xFF323946)
val Fg = Color(0xFFF4F5F7)
val Muted = Color(0xFF9AA3B2)
val Accent = Color(0xFF10B981)
val AccentSoft = Color(0xFF34D399)
val AccentDim = Color(0xFF17362F)
val OnAccent = Color(0xFF06281E)
val Tally = Color(0xFFFF3B30)
val Chip = Color(0xD91C212B)

val PromptColors = listOf(Color(0xFFF4F5F7), Color(0xFFFFE9B0), Color(0xFFFFC24A), Color(0xFF34D399))

fun fmt(sec: Long): String = String.format(Locale.ROOT, "%02d:%02d", sec / 60, sec % 60)

@Composable
fun SheetTitle(title: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Fg)
}

@Composable
fun Note(text: String) {
    Text(text, fontSize = 14.sp, color = Muted)
}
