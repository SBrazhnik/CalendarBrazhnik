package app.calstats.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.calstats.DrinkReminder
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import app.calstats.MainViewModel
import app.calstats.R
import app.calstats.Tab

/** Главный экран с нижней навигацией: Календарь · Задачи · Анализ. */
@Composable
fun HomeScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    BackHandler(enabled = vm.tab != Tab.CALENDAR) { vm.tab = Tab.CALENDAR }

    // Android 13+: один раз просим разрешение на уведомления для ежедневного вопроса.
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && vm.reminderEnabled && !DrinkReminder.canNotify(ctx) && !vm.notificationAsked) {
            vm.notificationAsked = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    val selected = vm.tab == t
                    NavigationBarItem(
                        selected = selected,
                        onClick = { vm.tab = t },
                        icon = { TabIcon(t, selected) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            Crossfade(targetState = vm.tab, label = "tab") { t ->
                when (t) {
                    Tab.CALENDAR -> CalendarTab(vm, snackbar)
                    Tab.TASKS -> TasksTab(vm, snackbar)
                    Tab.MONEY -> MoneyTab(vm, snackbar)
                    Tab.ANALYSIS -> AnalysisTab(vm, snackbar)
                }
            }
        }
    }
}

@Composable
private fun TabIcon(t: Tab, selected: Boolean) {
    when (t) {
        Tab.CALENDAR -> Icon(if (selected) Icons.Filled.DateRange else Icons.Outlined.DateRange, null)
        Tab.TASKS -> Icon(if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle, null)
        Tab.MONEY -> Icon(painterResource(R.drawable.ic_tab_money), null)
        Tab.ANALYSIS -> Icon(painterResource(R.drawable.ic_tab_stats), null)
    }
}
