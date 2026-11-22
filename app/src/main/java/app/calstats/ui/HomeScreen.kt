package app.calstats.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.DrinkReminder
import app.calstats.MainViewModel
import app.calstats.R
import app.calstats.Screen
import app.calstats.Tab
import app.calstats.Updater
import kotlinx.coroutines.launch

/** Открыть боковое меню из любого модуля (кнопка ☰ в шапке). */
val LocalOpenDrawer = compositionLocalOf<(() -> Unit)?> { null }

@Composable
fun MenuButton() {
    val open = LocalOpenDrawer.current ?: return
    IconButton(onClick = open) { Icon(Icons.Filled.Menu, "Меню") }
}

/** Оболочка приложения: боковое меню со всеми модулями (как в corpdance.ru) и текущий модуль. */
@Composable
fun HomeScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    // Android 13+: один раз просим разрешение на уведомления.
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
    LaunchedEffect(vm.tab) { app.calstats.Usage.module(ctx, vm.tab) }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }

    ModalNavigationDrawer(
        drawerState = drawer,
        // Открывается кнопкой ☰: свайп от края оставляем системному жесту «назад».
        gesturesEnabled = drawer.isOpen,
        drawerContent = { AppDrawer(vm, drawer) },
    ) {
        CompositionLocalProvider(LocalOpenDrawer provides { scope.launch { drawer.open() } }) {
            Box(Modifier.fillMaxSize()) {
                Crossfade(targetState = vm.tab, label = "tab") { t ->
                    when (t) {
                        Tab.HOME -> DashboardTab(vm, snackbar)
                        Tab.CALENDAR -> CalendarTab(vm, snackbar)
                        Tab.TASKS -> TasksTab(vm, snackbar)
                        Tab.FINANCE -> FinanceTab(vm, snackbar)
                        Tab.HABITS -> HabitsTab(vm, snackbar)
                        Tab.HEALTH -> HealthTab(vm, snackbar)
                        Tab.DOCS -> DocsTab(vm, snackbar)
                        Tab.ACCOUNTS -> AccountsTab(vm, snackbar)
                        Tab.GUIDE -> GuideTab(vm, snackbar)
                        Tab.SHOPPING -> ShoppingTab(vm, snackbar)
                        Tab.GOALS -> GoalsTab(vm, snackbar)
                        Tab.JOURNAL -> JournalTab(vm, snackbar)
                        Tab.CAR -> CarTab(vm, snackbar)
                        Tab.HOME_UTIL -> HomeUtilTab(vm, snackbar)
                        Tab.PETS -> PetsTab(vm, snackbar)
                        Tab.MONEY -> MoneyTab(vm, snackbar)
                        Tab.ANALYSIS -> AnalysisTab(vm, snackbar)
                        Tab.WISHES -> WishesTab(vm, snackbar)
                        Tab.NOTES -> NotesTab(vm, snackbar)
                    }
                }
                vm.update?.let { UpdateBar(vm, it, Modifier.align(Alignment.BottomCenter)) }
            }
        }
    }
}

@Composable
private fun AppDrawer(vm: MainViewModel, drawer: DrawerState) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    fun go(action: () -> Unit) {
        action()
        scope.launch { drawer.close() }
    }
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        modifier = Modifier.width(292.dp),
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 10.dp)) {
            // Шапка: аватар и имя — открывают профиль
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { go { vm.push(Screen.Profile) } }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(vm, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Зелёный ритм", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp), maxLines = 1)
                    Text(
                        vm.work.meName.takeIf { it.isNotBlank() && it != "Я" } ?: "Профиль",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            DrawerItem({ ModuleIcon(Tab.HOME, vm.tab == Tab.HOME, 22.dp) }, "Главная", vm.tab == Tab.HOME) { go { vm.tab = Tab.HOME } }
            DrawerDivider()
            vm.enabledModules.filter { it != Tab.ACCOUNTS && it != Tab.GUIDE }.forEach { t ->
                DrawerItem({ ModuleIcon(t, false, 22.dp) }, t.title, vm.tab == t) { go { vm.tab = t } }
            }
            DrawerDivider()
            if (vm.isEnabled(Tab.ACCOUNTS)) {
                DrawerItem({ ModuleIcon(Tab.ACCOUNTS, false, 22.dp) }, Tab.ACCOUNTS.title, vm.tab == Tab.ACCOUNTS) { go { vm.tab = Tab.ACCOUNTS } }
            }
            DrawerItem({ Icon(Icons.Outlined.AccountCircle, null, Modifier.size(22.dp)) }, "Профиль", false) { go { vm.push(Screen.Profile) } }
            if (vm.isEnabled(Tab.GUIDE)) {
                DrawerItem({ ModuleIcon(Tab.GUIDE, false, 22.dp) }, Tab.GUIDE.title, vm.tab == Tab.GUIDE) { go { vm.tab = Tab.GUIDE } }
            }
            DrawerItem({ Icon(Icons.Outlined.Info, null, Modifier.size(22.dp)) }, "О приложении", false) { go { vm.push(Screen.About) } }
            DrawerItem({ Icon(painterResource(R.drawable.ic_apps), null, Modifier.size(22.dp)) }, "Модули", false) { go { vm.push(Screen.Modules) } }
            DrawerItem({ Icon(Icons.Outlined.Settings, null, Modifier.size(22.dp)) }, "Настройки", false) { go { vm.push(Screen.Settings) } }
            DrawerDivider()
            DrawerItem(
                { Icon(painterResource(R.drawable.ic_download), null, Modifier.size(22.dp)) },
                if (vm.update != null) "Обновить приложение" else "Обновления",
                false,
                badge = vm.update != null,
            ) { go { vm.push(Screen.Profile) } }
            DrawerItem({ Icon(Icons.Outlined.Share, null, Modifier.size(22.dp)) }, "Скачать приложение", false) { go { Updater.shareDownloadLink(ctx) } }
            Spacer(Modifier.height(18.dp))
            // Декоративный знак внизу, как танцоры в меню corpdance
            Image(
                painterResource(R.drawable.ic_logo_mark),
                null,
                Modifier.align(Alignment.CenterHorizontally).size(120.dp).alpha(0.18f),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DrawerDivider() = HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)

/** Пункт меню: выбранный — контрастная «пилюля». */
@Composable
private fun DrawerItem(icon: @Composable () -> Unit, label: String, selected: Boolean, badge: Boolean = false, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides fg) {
            icon()
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = fg, modifier = Modifier.weight(1f))
            if (badge) Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        }
    }
}

/** Плашка «Новая версия» внизу экрана — как в corpdance. */
@Composable
private fun UpdateBar(vm: MainViewModel, r: Updater.Release, modifier: Modifier) {
    if (vm.updateDismissed) return
    val ctx = LocalContext.current
    Row(
        modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.inverseSurface).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            when {
                vm.updateProgress != null -> "Загрузка ${vm.updateProgress}%"
                else -> "📲 Новая версия ${r.version}"
            },
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (vm.updateProgress == null) {
            TextButton(onClick = { vm.updateDismissed = true }) { Text("Позже", color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)) }
            TextButton(onClick = { vm.installUpdate(ctx) }) { Text("Установить", color = MaterialTheme.colorScheme.inverseOnSurface, fontWeight = FontWeight.Bold) }
        }
    }
}
