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
    // «Зелёный ритм»: глубокий тёмно-синий, лаванда и тёмный зелёный (как Green Shadow у Galaxy Z Fold 8 Ultra)
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF8FBF9F),
            onPrimary = Color(0xFF0E2416),
            primaryContainer = Color(0xFF2F4F3E),
            onPrimaryContainer = Color(0xFFCFE8D7),
            secondary = Color(0xFFC9B8FF),
            onSecondary = Color(0xFF231A4A),
            secondaryContainer = Color(0xFF3A3264),
            onSecondaryContainer = Color(0xFFE8E0FF),
            tertiary = Color(0xFFC9B8FF),
            tertiaryContainer = Color(0xFF2E2A52),
            onTertiaryContainer = Color(0xFFE8E0FF),
            background = Color(0xFF121721),
            onBackground = Color(0xFFEEF0F7),
            surface = Color(0xFF121721),
            onSurface = Color(0xFFEEF0F7),
            onSurfaceVariant = Color(0xFFA3A9C0),
            surfaceVariant = Color(0xFF262D42),
            surfaceContainerHighest = Color(0xFF2A3148),
            surfaceContainerHigh = Color(0xFF232A3F),
            surfaceContainer = Color(0xFF1C2232),
            surfaceContainerLow = Color(0xFF171C29),
            outline = Color(0xFF4A5370),
            outlineVariant = Color(0xFF323A52),
            error = Color(0xFFFF8A80),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF34553F),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD3E3D6),
            onPrimaryContainer = Color(0xFF0E2416),
            secondary = Color(0xFF1D2333),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE8E2FF),
            onSecondaryContainer = Color(0xFF2A2160),
            tertiary = Color(0xFF6B5BC4),
            tertiaryContainer = Color(0xFFE8E2FF),
            onTertiaryContainer = Color(0xFF2A2160),
            background = Color(0xFFF6F6F2),
            onBackground = Color(0xFF121721),
            surface = Color(0xFFF6F6F2),
            onSurface = Color(0xFF121721),
            onSurfaceVariant = Color(0xFF5B6275),
            surfaceVariant = Color(0xFFE6E7E0),
            surfaceContainerHighest = Color(0xFFE4E5DD),
            surfaceContainerHigh = Color(0xFFEBECE5),
            surfaceContainer = Color(0xFFEFF0EA),
            surfaceContainerLow = Color(0xFFF3F4EE),
            outline = Color(0xFF8A8F80),
            outlineVariant = Color(0xFFD3D5CB),
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
        modifier = Modifier.height(size).width(size),
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
