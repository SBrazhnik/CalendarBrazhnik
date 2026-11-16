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

/** Палитры тем: строгие «интерьерные» сочетания. */
private val LinenScheme = lightColorScheme(
    primary = Color(0xFF1F3A2B),
    onPrimary = Color(0xFFF4F1EA),
    primaryContainer = Color(0xFFDCE5DA),
    onPrimaryContainer = Color(0xFF0F1F17),
    secondary = Color(0xFF1F3A2B),
    onSecondary = Color(0xFFF4F1EA),
    secondaryContainer = Color(0xFFF1E2CF),
    onSecondaryContainer = Color(0xFF3A2414),
    tertiary = Color(0xFFC27B44),
    tertiaryContainer = Color(0xFFF1E2CF),
    onTertiaryContainer = Color(0xFF3A2414),
    background = Color(0xFFF4F1EA),
    onBackground = Color(0xFF1B1F1A),
    surface = Color(0xFFF4F1EA),
    onSurface = Color(0xFF1B1F1A),
    onSurfaceVariant = Color(0xFF6B6A60),
    surfaceVariant = Color(0xFFE6E0D3),
    surfaceContainerHighest = Color(0xFFE3DDCF),
    surfaceContainerHigh = Color(0xFFE9E4D8),
    surfaceContainer = Color(0xFFFBF9F4),
    surfaceContainerLow = Color(0xFFF7F4EE),
    outline = Color(0xFFB8B0A0),
    outlineVariant = Color(0xFFDDD6C8),
    error = Color(0xFFB3261E),
)

private val GraphiteScheme = darkColorScheme(
    primary = Color(0xFF9DBFA6),
    onPrimary = Color(0xFF10241A),
    primaryContainer = Color(0xFF2F3A33),
    onPrimaryContainer = Color(0xFFD5E6D9),
    secondary = Color(0xFFC9A46A),
    onSecondary = Color(0xFF241A0C),
    secondaryContainer = Color(0xFF3A3122),
    onSecondaryContainer = Color(0xFFF0DFC2),
    tertiary = Color(0xFFC9A46A),
    tertiaryContainer = Color(0xFF332C21),
    onTertiaryContainer = Color(0xFFF0DFC2),
    background = Color(0xFF141615),
    onBackground = Color(0xFFECE8E0),
    surface = Color(0xFF141615),
    onSurface = Color(0xFFECE8E0),
    onSurfaceVariant = Color(0xFFA29E95),
    surfaceVariant = Color(0xFF2A2D2B),
    surfaceContainerHighest = Color(0xFF303331),
    surfaceContainerHigh = Color(0xFF2A2D2B),
    surfaceContainer = Color(0xFF1E201F),
    surfaceContainerLow = Color(0xFF191B1A),
    outline = Color(0xFF5A5D58),
    outlineVariant = Color(0xFF383B38),
    error = Color(0xFFFF8A80),
)

private val ForestScheme = darkColorScheme(
    primary = Color(0xFFD4A960),
    onPrimary = Color(0xFF2A1F0B),
    primaryContainer = Color(0xFF2A4434),
    onPrimaryContainer = Color(0xFFEDE6D6),
    secondary = Color(0xFFE8DCC2),
    onSecondary = Color(0xFF12241A),
    secondaryContainer = Color(0xFF2A4434),
    onSecondaryContainer = Color(0xFFEDE6D6),
    tertiary = Color(0xFFE0954F),
    tertiaryContainer = Color(0xFF3A3524),
    onTertiaryContainer = Color(0xFFF3E4C8),
    background = Color(0xFF0F1F17),
    onBackground = Color(0xFFEDE6D6),
    surface = Color(0xFF0F1F17),
    onSurface = Color(0xFFEDE6D6),
    onSurfaceVariant = Color(0xFFA9B3A4),
    surfaceVariant = Color(0xFF243A2D),
    surfaceContainerHighest = Color(0xFF26402F),
    surfaceContainerHigh = Color(0xFF1F3528),
    surfaceContainer = Color(0xFF16291F),
    surfaceContainerLow = Color(0xFF13241B),
    outline = Color(0xFF4F6656),
    outlineVariant = Color(0xFF2C4334),
    error = Color(0xFFFF8A80),
)

/** Тёмная ли выбранная тема (для строки состояния и фона заметок). */
@Composable
fun isDarkTheme(mode: app.calstats.ThemeMode): Boolean = when (mode) {
    app.calstats.ThemeMode.LIGHT -> false
    app.calstats.ThemeMode.DARK, app.calstats.ThemeMode.FOREST -> true
    app.calstats.ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun AppTheme(mode: app.calstats.ThemeMode = app.calstats.ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val scheme = when (mode) {
        app.calstats.ThemeMode.LIGHT -> LinenScheme
        app.calstats.ThemeMode.DARK -> GraphiteScheme
        app.calstats.ThemeMode.FOREST -> ForestScheme
        app.calstats.ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) ForestScheme else LinenScheme
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
