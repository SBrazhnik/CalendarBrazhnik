package app.calstats

import android.os.Bundle
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
import app.calstats.ui.PermissionScreen
import app.calstats.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                App(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
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

    BackHandler(enabled = vm.stack.size > 1) { vm.pop() }

    when (val screen = vm.stack.last()) {
        Screen.Main -> HomeScreen(vm, snackbar)
        is Screen.Direction -> DirectionScreen(vm, screen.key, snackbar)
        is Screen.Edit -> EditScreen(vm, screen, snackbar)
        Screen.Colors -> ColorsScreen(vm, snackbar)
        Screen.Settings -> SettingsScreen(vm, snackbar)
    }
}
