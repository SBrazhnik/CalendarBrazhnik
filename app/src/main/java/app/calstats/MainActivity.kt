package app.calstats

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
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            AppTheme {
                App(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    /** Текст или ссылка из другого приложения (Samsung Notes, Keep, Яндекс, браузер, маркетплейс). */
    private fun handleShare(intent: Intent?) {
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

    if (!vm.hasPermission) {
        PermissionScreen(onGranted = { vm.onResume() })
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
        is Screen.NoteEdit -> app.calstats.ui.NoteEditScreen(vm, screen.id, snackbar)
        is Screen.WishListDetail -> app.calstats.ui.WishListScreen(vm, screen.id, snackbar)
    }
}
