package app.calstats.ui

import android.Manifest
import android.os.Build
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.calstats.MainViewModel
import app.calstats.Tab

/** Оболочка приложения: Главная и включённые модули в нижней панели. */
@Composable
fun HomeScreen(vm: MainViewModel, snackbar: SnackbarHostState) {

    // Android 13+: один раз просим разрешение на уведомления для ежедневного вопроса.
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && vm.reminderEnabled && !DrinkReminder.canNotify(ctx) && !vm.notificationAsked) {
            vm.notificationAsked = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Выключенный модуль открыт — уходим на главную.
    LaunchedEffect(vm.disabledModules) { if (!vm.isEnabled(vm.tab)) vm.tab = Tab.HOME }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                vm.bottomTabs.forEach { t ->
                    val selected = vm.tab == t
                    NavigationBarItem(
                        selected = selected,
                        onClick = { vm.tab = t },
                        icon = { ModuleIcon(t, selected) },
                        label = { Text(t.title, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            Crossfade(targetState = vm.tab, label = "tab") { t ->
                when (t) {
                    Tab.HOME -> DashboardTab(vm, snackbar)
                    Tab.CALENDAR -> CalendarTab(vm, snackbar)
                    Tab.TASKS -> TasksTab(vm, snackbar)
                    Tab.MONEY -> MoneyTab(vm, snackbar)
                    Tab.ANALYSIS -> AnalysisTab(vm, snackbar)
                    Tab.WISHES -> WishesTab(vm, snackbar)
                    Tab.NOTES -> NotesTab(vm, snackbar)
                }
            }
        }
    }
}
