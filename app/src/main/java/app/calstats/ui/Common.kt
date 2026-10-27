package app.calstats.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Period
import app.calstats.R
import app.calstats.PeriodType
import java.time.LocalDate

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFF08A68),
            onPrimary = Color(0xFF171A33),
            primaryContainer = Color(0xFF5A2516),
            onPrimaryContainer = Color(0xFFFFDBD0),
            secondary = Color(0xFFEEF0F4),
            onSecondary = Color(0xFF171A33),
            background = Color(0xFF12152A),
            surface = Color(0xFF12152A),
            surfaceVariant = Color(0xFF262A47),
            surfaceContainerHighest = Color(0xFF262A47),
            surfaceContainerHigh = Color(0xFF20243F),
            surfaceContainer = Color(0xFF1B1F38),
            surfaceContainerLow = Color(0xFF171A33),
            tertiaryContainer = Color(0xFF2E3358),
            onTertiaryContainer = Color(0xFFEEF0F4),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFE4572E),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDBD0),
            onPrimaryContainer = Color(0xFF3A0B00),
            secondary = Color(0xFF171A33),
            onSecondary = Color.White,
            background = Color(0xFFF7F7FA),
            surface = Color(0xFFF7F7FA),
            surfaceVariant = Color(0xFFE6E8EF),
            surfaceContainerHighest = Color(0xFFE6E8EF),
            surfaceContainerHigh = Color(0xFFECEDF2),
            surfaceContainer = Color(0xFFF0F1F5),
            surfaceContainerLow = Color(0xFFF3F4F7),
            tertiaryContainer = Color(0xFFDDE0F0),
            onTertiaryContainer = Color(0xFF171A33),
        )
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
    val base = Typography()
    val typography = base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
    MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography, content = content)
}

/** Логотип: полный (с надписью) или только знак для шапки. */
@Composable
fun AppLogo(size: Dp, full: Boolean = true) {
    Image(
        painter = painterResource(if (full) R.drawable.ic_logo else R.drawable.ic_logo_mark),
        contentDescription = null,
        modifier = Modifier.height(size),
    )
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
