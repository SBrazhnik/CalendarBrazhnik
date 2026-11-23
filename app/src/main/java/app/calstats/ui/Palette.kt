@file:OptIn(ExperimentalLayoutApi::class)

package app.calstats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** Готовые цвета: 12 оттенков × 5 тонов + серые. */
val PALETTE_COLORS: List<Int> = buildList {
    val hues = listOf(0f, 15f, 30f, 45f, 60f, 90f, 130f, 160f, 190f, 215f, 250f, 290f, 320f, 340f)
    val tones = listOf(0.35f to 0.95f, 0.55f to 0.85f, 0.75f to 0.70f, 0.70f to 0.50f, 0.55f to 0.32f)
    for ((s, v) in tones) for (h in hues) add(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    listOf(0xFFFFFFFF, 0xFFBDBDBD, 0xFF8A8F98, 0xFF5E6472, 0xFF2B303A, 0xFF121721).forEach { add(it.toInt()) }
}

/** Выбор любого цвета: сетка готовых + ползунки оттенок / насыщенность / яркость. */
@Composable
fun ColorPalette(selected: Int, onPick: (Int) -> Unit) {
    val hsv = remember(selected) { FloatArray(3).also { android.graphics.Color.colorToHSV(selected, it) } }
    var h by remember(selected) { mutableStateOf(hsv[0]) }
    var s by remember(selected) { mutableStateOf(hsv[1]) }
    var v by remember(selected) { mutableStateOf(hsv[2]) }
    fun emit() = onPick(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            PALETTE_COLORS.forEach { c ->
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Color(c))
                        .then(if (c == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier.border(1.dp, Color(0x22000000), CircleShape))
                        .clickable { onPick(c) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(selected)))
            Spacer(Modifier.width(10.dp))
            Text("#%06X".format(selected and 0xFFFFFF), style = MaterialTheme.typography.labelLarge)
        }
        Text("Оттенок", style = MaterialTheme.typography.labelSmall)
        Box(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(
                Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }),
            ),
        )
        Slider(h, { h = it; emit() }, valueRange = 0f..360f)
        Text("Насыщенность", style = MaterialTheme.typography.labelSmall)
        Slider(s, { s = it; emit() })
        Text("Яркость", style = MaterialTheme.typography.labelSmall)
        Slider(v, { v = it; emit() })
    }
}

@Suppress("unused")
private fun Color.argb() = toArgb()
