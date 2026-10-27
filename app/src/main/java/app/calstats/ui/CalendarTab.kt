@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import app.calstats.CalView
import app.calstats.Fmt
import app.calstats.MainViewModel
import app.calstats.R
import app.calstats.Screen
import app.calstats.Sobriety
import java.time.LocalDate

val SOBER_COLOR = Color(0xFF2FA36B)
val DRANK_COLOR = Color(0xFFD64545)

/** Цвет текста поверх цвета события: на светлых — тёмный, на тёмных — белый. */
fun onEventColor(c: Int): Color = if (ColorUtils.calculateLuminance(c) > 0.6) Color(0xFF1F1F1F) else Color.White

/** Вкладка «Календарь» в духе Google Календаря: расписание, день, 3 дня, неделя, месяц. */
@Composable
fun CalendarTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val byDay = remember(vm.instances, vm.calendars) { vm.eventsByDay() }
    var viewMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val d = vm.selectedDate
    val title = Fmt.monthName(d.monthValue) + if (d.year != vm.today.year) " ${d.year}" else ""

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { pickDate(ctx, vm.selectedDate) { vm.selectedDate = it } }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Icon(Icons.Filled.ArrowDropDown, "Перейти к дате")
                    }
                },
                actions = {
                    TodayButton(vm.today.dayOfMonth) { vm.selectedDate = vm.today }
                    Box {
                        IconButton(onClick = { viewMenu = true }) {
                            Icon(painterResource(R.drawable.ic_view_agenda), "Вид: ${vm.calView.title}")
                        }
                        DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                            CalView.entries.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text(v.title) },
                                    trailingIcon = { if (v == vm.calView) Icon(Icons.Filled.Check, null) },
                                    onClick = {
                                        vm.calView = v
                                        viewMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Синхронизировать") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                onClick = {
                                    moreMenu = false
                                    vm.syncNow()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Настройки") },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = {
                                    moreMenu = false
                                    vm.push(Screen.Settings)
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.push(Screen.Edit(presetDate = vm.selectedDate)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) { Icon(Icons.Filled.Add, "Новое событие") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.loading && !vm.loadedOnce) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (vm.calView) {
                CalView.MONTH -> MonthView(vm, byDay) { date ->
                    vm.selectedDate = date
                    vm.calView = CalView.DAY
                }
                CalView.SCHEDULE -> ScheduleView(vm, byDay)
                else -> TimelineView(vm, byDay, vm.calView.days)
            }
        }
    }
}

/** Кнопка «Сегодня» — квадратик с числом, как в Google Календаре. */
@Composable
private fun TodayButton(day: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            Modifier
                .size(22.dp)
                .border(1.8.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Компактная строка «Алкоголь: Не пил / Пил · N дней без алкоголя» для выбранного дня. */
@Composable
fun DrinkRow(vm: MainViewModel, date: LocalDate) {
    if (date.isAfter(vm.today)) return
    val mark = vm.drinks[date]
    val summary = remember(vm.drinks, vm.today) { Sobriety.summary(vm.drinks, vm.today) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Алкоголь", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            FilterChip(
                selected = mark == false,
                onClick = { vm.setDrink(date, if (mark == false) null else false) },
                label = { Text("Не пил") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SOBER_COLOR, selectedLabelColor = Color.White),
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = mark == true,
                onClick = { vm.setDrink(date, if (mark == true) null else true) },
                label = { Text("Пил") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DRANK_COLOR, selectedLabelColor = Color.White),
            )
        }
        val streak = summary.streak
        if (streak != null) {
            Text(
                Sobriety.streakText(summary) +
                    (summary.lastDrank?.let { " · последний раз ${Fmt.dateNoYear(it)}" } ?: "") +
                    if (summary.longest > 0) " · рекорд ${summary.longest}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (streak > 0) SOBER_COLOR else DRANK_COLOR,
            )
        }
    }
    HorizontalDivider()
}
