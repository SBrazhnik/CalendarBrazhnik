package app.calstats.ui

import android.app.DatePickerDialog
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.calstats.Period
import app.calstats.PeriodType
import java.time.LocalDate

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF7FD3BC), secondary = Color(0xFFF2B84B))
        else -> lightColorScheme(primary = Color(0xFF1F6F5C), secondary = Color(0xFFB7791F))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun ColorDot(color: Int, size: Dp = 14.dp, selected: Boolean = false) {
    var m = Modifier.size(size).clip(CircleShape).background(Color(color))
    if (selected) m = m.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
    Box(m)
}

@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun pickDate(ctx: Context, initial: LocalDate, title: String? = null, onPicked: (LocalDate) -> Unit) {
    val dlg = DatePickerDialog(
        ctx,
        { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth,
    )
    if (title != null) dlg.setTitle(title)
    dlg.show()
}

@Composable
fun PeriodSelector(period: Period, loadStart: LocalDate, onChange: (Period) -> Unit) {
    val ctx = LocalContext.current
    fun pickCustom(base: Period) {
        pickDate(ctx, base.customStart, "Начало периода") { s ->
            val endInit = if (base.customEndInclusive.isBefore(s)) s else base.customEndInclusive
            pickDate(ctx, endInit, "Конец периода") { e ->
                val (a, b) = if (e.isBefore(s)) e to s else s to e
                onChange(base.copy(type = PeriodType.CUSTOM, customStart = a, customEndInclusive = b))
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodType.entries.forEach { t ->
                FilterChip(
                    selected = period.type == t,
                    onClick = {
                        if (t == PeriodType.CUSTOM) pickCustom(period)
                        else onChange(period.copy(type = t, anchor = LocalDate.now()))
                    },
                    label = { Text(t.label) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (period.navigable) {
                IconButton(onClick = { onChange(period.shift(-1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Назад")
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Text(
                period.title(loadStart),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            if (period.navigable) {
                IconButton(onClick = { onChange(period.shift(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Вперёд")
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }
        if (period.type == PeriodType.CUSTOM) {
            TextButton(onClick = { pickCustom(period) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Изменить даты")
            }
        } else if (period.navigable && !periodContainsToday(period)) {
            TextButton(
                onClick = { onChange(period.copy(anchor = LocalDate.now())) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("К текущему") }
        }
        Spacer(Modifier.padding(2.dp))
    }
}

private fun periodContainsToday(p: Period): Boolean {
    val today = LocalDate.now()
    val s = p.start(today)
    val e = p.endExclusive(today)
    return !today.isBefore(s) && today.isBefore(e)
}
