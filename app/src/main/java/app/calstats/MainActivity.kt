package app.calstats

import android.os.Build
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.calstats.ui.AppTheme
import app.calstats.ui.ColorsScreen
import app.calstats.ui.DirectionScreen
import app.calstats.ui.EditScreen
import app.calstats.ui.HomeScreen
import app.calstats.ui.IncomingDialog
import app.calstats.ui.PermissionScreen
import app.calstats.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    companion object {
        /** Открыть модуль из уведомления. */
        const val EXTRA_TAB = "tab"
        const val EXTRA_QUICK = "quick"
    }

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            val dark = app.calstats.ui.isDarkTheme(vm.themeMode)
            androidx.compose.runtime.LaunchedEffect(dark) {
                // Значки строки состояния — под выбранную тему, а не под системную.
                enableEdgeToEdge(
                    statusBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }
            AppTheme(vm.themeMode) {
                App(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        if (AppLock.shouldLock(this)) {
            vm.appLocked = true
            vm.quickInput = null; vm.checkIn = false
        }
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!AppLock.enabled(this))
    }

    override fun onStop() {
        super.onStop()
        AppLock.markBackground(this)
        // Документы снова закрываются, когда приложение уходит в фон (но не при выборе фото).
        if (!vm.keepDocsUnlocked) vm.docsUnlocked = false
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    /** Текст или ссылка из другого приложения (Samsung Notes, Keep, Яндекс, браузер, маркетплейс). */
    private fun handleShare(intent: Intent?) {
        // Файл карты .pkpass: «Открыть с помощью» или «Поделиться»
        val stream = intent?.data ?: (if (intent?.action == Intent.ACTION_SEND) intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) else null)
        if (stream != null && (intent?.type?.contains("pkpass") == true || stream.toString().endsWith(".pkpass", true) || intent?.action == Intent.ACTION_VIEW)) {
            app.calstats.ui.importPkpass(this, stream)?.let { c ->
                vm.pendingCard = c
                vm.tab = Tab.CARDS
                if (!vm.isEnabled(Tab.CARDS)) vm.setModuleEnabled(Tab.CARDS, true)
                return
            }
        }
        intent?.getStringExtra(EXTRA_QUICK)?.let { q ->
            intent.removeExtra(EXTRA_QUICK)
            vm.stack.clear(); vm.stack.add(Screen.Main)
            if (q.startsWith("note:")) {
                vm.tab = Tab.NOTES; vm.push(Screen.NoteEdit(q.removePrefix("note:")))
                return
            }
            if (q.startsWith("card:")) {
                vm.tab = Tab.CARDS; vm.showCardId = q.removePrefix("card:")
                return
            }
            when (q) {
                "voice" -> vm.quickInput = true
                "text" -> vm.quickInput = false
                "checkin" -> vm.checkIn = true
                "water" -> vm.message = "✓ " + vm.applySmart(app.calstats.work.SmartInput.Result(app.calstats.work.SmartInput.Kind.WATER, "250 мл", ml = 250))
                "cashback" -> { vm.subViews["cards"] = "CASHBACK"; vm.tab = Tab.CARDS }
                "cards" -> { if (!vm.isEnabled(Tab.CARDS)) vm.setModuleEnabled(Tab.CARDS, true); vm.tab = Tab.CARDS }
            }
            return
        }
        intent?.getStringExtra(EXTRA_TAB)?.let { t ->
            Tab.entries.firstOrNull { it.name == t }?.takeIf { vm.isEnabled(it) }?.let { vm.stack.clear(); vm.stack.add(Screen.Main); vm.tab = it }
        }
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
        val pkg = referrer?.host.orEmpty()
        val source = when {
            pkg.contains("samsung.android.app.notes") -> "Samsung Notes"
            pkg.contains("google.android.keep") -> "Google Keep"
            pkg.contains("yandex") -> "Яндекс"
            pkg.contains("ozon") -> "Ozon"
            pkg.contains("wildberries") -> "Wildberries"
            else -> "Другое приложение"
        }
        vm.incoming = SharedText(text, intent.getStringExtra(Intent.EXTRA_SUBJECT), source)
    }
}

@Composable
fun App(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.message = null
        }
    }
    val undo = vm.undo
    LaunchedEffect(undo) {
        if (undo != null) {
            val r = snackbar.showSnackbar("Удалено: ${undo.label}", actionLabel = "Отменить", duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.undoDelete(undo)
            else if (vm.undo == undo) vm.undo = null
        }
    }

    if (!vm.hasPermission) {
        PermissionScreen(onGranted = { vm.onResume() })
        return
    }
    val lockCtx = LocalContext.current
    if (vm.appLocked && vm.appLockOn && AppLock.covers(lockCtx, vm.tab)) {
        app.calstats.ui.LockScreen(onUnlock = { vm.appLocked = false; vm.appLockOn = AppLock.enabled(lockCtx) })
        return
    }

    // «Назад»: закрыть экран → вернуться к предыдущему модулю/виду → выйти двойным нажатием.
    val activity = LocalContext.current as? Activity
    var lastBack by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = true) {
        when {
            vm.stack.size > 1 -> vm.pop()
            vm.navigateBack() -> {}
            System.currentTimeMillis() - lastBack < 2000 -> activity?.finish()
            else -> {
                lastBack = System.currentTimeMillis()
                Toast.makeText(activity, "Нажмите «назад» ещё раз, чтобы выйти", Toast.LENGTH_SHORT).show()
            }
        }
    }

    vm.incoming?.let { IncomingDialog(vm, it) }
    if (vm.quickEvent) app.calstats.ui.QuickEventDialog(vm)
    vm.quickInput?.let { app.calstats.ui.QuickInputSheet(vm, it) }
    vm.pendingTx?.let { t -> app.calstats.ui.TxSheet(vm, t) { vm.pendingTx = null } }
    if (vm.checkIn) app.calstats.ui.CheckInSheet(vm)
    vm.focus?.let { app.calstats.ui.FocusDialog(vm, it) }
    if (!vm.whatsNewChecked && vm.stack.size == 1) app.calstats.ui.WhatsNewDialog(vm)

    when (val screen = vm.stack.last()) {
        Screen.Main -> HomeScreen(vm, snackbar)
        is Screen.Direction -> DirectionScreen(vm, screen.key, snackbar)
        is Screen.Edit -> EditScreen(vm, screen, snackbar)
        Screen.Colors -> ColorsScreen(vm, snackbar)
        Screen.Settings -> SettingsScreen(vm, snackbar)
        is Screen.IssueDetail -> app.calstats.ui.IssueScreen(vm, screen.id, snackbar)
        Screen.People -> app.calstats.ui.PeopleScreen(vm, snackbar)
        is Screen.ProjectEdit -> app.calstats.ui.ProjectEditScreen(vm, screen.id, snackbar)
        is Screen.Book -> app.calstats.ui.BookScreen(vm, screen.id, snackbar)
        is Screen.ExpenseEdit -> app.calstats.ui.ExpenseScreen(vm, screen.bookId, screen.expenseId, snackbar)
        Screen.Modules -> app.calstats.ui.ModulesScreen(vm, snackbar)
        Screen.Search -> app.calstats.ui.SearchScreen(vm, snackbar)
        Screen.Trash -> app.calstats.ui.TrashScreen(vm, snackbar)
        is Screen.NoteEdit -> app.calstats.ui.NoteEditScreen(vm, screen.id, snackbar)
        is Screen.WishListDetail -> app.calstats.ui.WishListScreen(vm, screen.id, snackbar)
        is Screen.DocEdit -> app.calstats.ui.DocEditScreen(vm, screen.id, snackbar)
        Screen.Profile -> app.calstats.ui.ProfileScreen(vm, snackbar)
        Screen.About -> app.calstats.ui.AboutScreen(vm, snackbar)
    }
}
