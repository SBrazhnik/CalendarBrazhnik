@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.Period
import app.calstats.R
import app.calstats.PeriodType
import java.time.LocalDate

/*
 * Палитры в стиле corpdance.ru: плоские карточки на ровном фоне, тонкие линии,
 * выбранное — «пилюля» контрастного цвета (secondaryContainer). secondary = цвет карточки
 * (верхние сводные блоки модулей выглядят как обычные карточки).
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF3F3561),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEFEBFC),
    onPrimaryContainer = Color(0xFF3F3561),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF3A3F47),
    secondaryContainer = Color(0xFF3A3F47),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF7C5CE6),
    tertiaryContainer = Color(0xFFEFEBFC),
    onTertiaryContainer = Color(0xFF3F3561),
    background = Color(0xFFF3F2F7),
    onBackground = Color(0xFF3A3F47),
    surface = Color(0xFFF3F2F7),
    onSurface = Color(0xFF3A3F47),
    onSurfaceVariant = Color(0xFF7A808B),
    surfaceVariant = Color(0xFFEEECF4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F7FB),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEECF4),
    surfaceContainerHighest = Color(0xFFE6E4EE),
    inverseSurface = Color(0xFF3A3F47),
    inverseOnSurface = Color(0xFFF1F2F5),
    outline = Color(0xFFC9C6D3),
    outlineVariant = Color(0xFFE3E1EA),
    error = Color(0xFFC93D3D),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC9B8FF),
    onPrimary = Color(0xFF121721),
    primaryContainer = Color(0xFF3F3561),
    onPrimaryContainer = Color(0xFFF1F2F5),
    secondary = Color(0xFF1D222A),
    onSecondary = Color(0xFFF1F2F5),
    secondaryContainer = Color(0xFFF1F2F5),
    onSecondaryContainer = Color(0xFF121721),
    tertiary = Color(0xFFC9B8FF),
    tertiaryContainer = Color(0xFF2E2A45),
    onTertiaryContainer = Color(0xFFE6DEFF),
    background = Color(0xFF121721),
    onBackground = Color(0xFFE2E4E8),
    surface = Color(0xFF121721),
    onSurface = Color(0xFFE2E4E8),
    onSurfaceVariant = Color(0xFF9EA3AB),
    surfaceVariant = Color(0xFF262C36),
    surfaceContainerLowest = Color(0xFF0B0F16),
    surfaceContainerLow = Color(0xFF181D26),
    surfaceContainer = Color(0xFF1D222A),
    surfaceContainerHigh = Color(0xFF262C36),
    surfaceContainerHighest = Color(0xFF2B303A),
    inverseSurface = Color(0xFFF1F2F5),
    inverseOnSurface = Color(0xFF121721),
    outline = Color(0xFF4A505B),
    outlineVariant = Color(0xFF2A2F37),
    error = Color(0xFFFF8080),
)

private val ForestScheme = darkColorScheme(
    primary = Color(0xFFD4A960),
    onPrimary = Color(0xFF1B140A),
    primaryContainer = Color(0xFF2A4434),
    onPrimaryContainer = Color(0xFFEDE6D6),
    secondary = Color(0xFF172A20),
    onSecondary = Color(0xFFEDE6D6),
    secondaryContainer = Color(0xFFEDE6D6),
    onSecondaryContainer = Color(0xFF0C1913),
    tertiary = Color(0xFFE0954F),
    tertiaryContainer = Color(0xFF3A3524),
    onTertiaryContainer = Color(0xFFF3E4C8),
    background = Color(0xFF0C1913),
    onBackground = Color(0xFFE2DCCC),
    surface = Color(0xFF0C1913),
    onSurface = Color(0xFFE2DCCC),
    onSurfaceVariant = Color(0xFFA9B3A4),
    surfaceVariant = Color(0xFF223A2C),
    surfaceContainerLowest = Color(0xFF07110C),
    surfaceContainerLow = Color(0xFF12241A),
    surfaceContainer = Color(0xFF172A20),
    surfaceContainerHigh = Color(0xFF1F3528),
    surfaceContainerHighest = Color(0xFF26402F),
    inverseSurface = Color(0xFFEDE6D6),
    inverseOnSurface = Color(0xFF0C1913),
    outline = Color(0xFF4F6656),
    outlineVariant = Color(0xFF243A2D),
    error = Color(0xFFFF8A80),
)

/** Шрифты: Inter — основной текст (максимально читаемый), Manrope — заголовки и крупные цифры (строгий, плотный). */
private fun vf(res: Int, w: Int) = Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Golos = FontFamily(vf(R.font.inter, 400), vf(R.font.inter, 500), vf(R.font.inter, 600), vf(R.font.inter, 700))
val DisplayFont = FontFamily(vf(R.font.manrope, 600), vf(R.font.manrope, 700), vf(R.font.manrope, 800))
/** Подписи под заголовками — тем же строгим шрифтом, без рукописного. */
val ScriptFont = FontFamily(vf(R.font.inter, 400), vf(R.font.inter, 500))

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
        app.calstats.ThemeMode.LIGHT -> LightScheme
        app.calstats.ThemeMode.DARK -> DarkScheme
        app.calstats.ThemeMode.FOREST -> ForestScheme
        app.calstats.ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) ForestScheme else LightScheme
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
    val base = Typography()
    fun TextStyle.g(w: FontWeight? = null) = copy(fontFamily = Golos, fontWeight = w ?: fontWeight)
    fun TextStyle.d(size: Int) = copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = (size * 0.8f).sp, lineHeight = (size * 0.96f).sp, letterSpacing = (-0.5).sp)
    val typography = Typography(
        displayLarge = base.displayLarge.d(52),
        displayMedium = base.displayMedium.d(44),
        // Крупные цифры в карточках (KPI) — как на corpdance
        displaySmall = base.displaySmall.d(36),
        headlineLarge = base.headlineLarge.d(36),
        headlineMedium = base.headlineMedium.d(32),
        // Заголовки экранов: «ГЛАВНАЯ», «ЗАДАЧИ»…
        headlineSmall = base.headlineSmall.d(30),
        titleLarge = base.titleLarge.g(FontWeight.Medium),
        titleMedium = base.titleMedium.g(FontWeight.Medium),
        titleSmall = base.titleSmall.g(FontWeight.Medium),
        bodyLarge = base.bodyLarge.g(),
        bodyMedium = base.bodyMedium.g(),
        bodySmall = base.bodySmall.g(),
        labelLarge = base.labelLarge.g(FontWeight.Medium),
        labelMedium = base.labelMedium.g(FontWeight.Medium),
        labelSmall = base.labelSmall.g(FontWeight.Medium),
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
