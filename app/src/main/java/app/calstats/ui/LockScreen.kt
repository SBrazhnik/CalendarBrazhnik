package app.calstats.ui

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.AppLock
import kotlinx.coroutines.delay

/** Системное окно отпечатка/лица. withDevice — разрешить пароль телефона (для «Забыли пароль?»). */
fun bioPrompt(ctx: Context, title: String, withDevice: Boolean, onOk: () -> Unit) {
    if (Build.VERSION.SDK_INT < 29) return
    val b = BiometricPrompt.Builder(ctx).setTitle(title)
    if (Build.VERSION.SDK_INT >= 30) {
        b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or if (withDevice) BiometricManager.Authenticators.DEVICE_CREDENTIAL else 0)
    } else if (withDevice) {
        @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
    }
    if (!withDevice) b.setNegativeButton("Ввести пароль", ctx.mainExecutor) { _, _ -> }
    runCatching {
        b.build().authenticate(CancellationSignal(), ctx.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onOk()
        })
    }
}

/**
 * Клавиатура пароля. mode: проверка (onDone вызывается при верном пароле)
 * или ввод нового (onDone получает пароль, который ввели дважды одинаково).
 */
@Composable
fun PinPad(title: String, subtitle: String?, error: String?, onDigit: (String) -> Unit, length: Int, entered: Int, bio: (() -> Unit)?, extra: (@Composable () -> Unit)? = null) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🌿", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(length) { i ->
                Box(
                    Modifier.size(14.dp).then(
                        if (i < entered) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    ),
                )
            }
        }
        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (bio != null) "bio" else "", "0", "del")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(72.dp).then(
                            if (k.isEmpty()) Modifier else Modifier.background(
                                if (k.length == 1) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface, CircleShape,
                            ).clickable { if (k == "bio") bio?.invoke() else onDigit(k) },
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "bio" -> Text("👆", fontSize = 28.sp)
                            "del" -> Text("⌫", fontSize = 24.sp)
                            "" -> {}
                            else -> Text(k, fontSize = 28.sp, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
        extra?.invoke()
    }
}

/** Экран блокировки при входе. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableStateOf(AppLock.waitSeconds(ctx)) }
    var forgot by remember { mutableStateOf(false) }
    val bio = AppLock.bio(ctx) && AppLock.bioAvailable(ctx)
    val len = AppLock.pinLength(ctx)
    LaunchedEffect(Unit) { if (bio) bioPrompt(ctx, "Зелёный ритм", false, onUnlock) }
    LaunchedEffect(wait) {
        while (wait > 0) { delay(1000); wait = AppLock.waitSeconds(ctx) }
        if (error?.startsWith("Подождите") == true) error = null
    }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
            PinPad(
                title = "Введите пароль", subtitle = null,
                error = if (wait > 0) "Подождите $wait с" else error,
                length = len, entered = pin.length,
                bio = if (bio) ({ bioPrompt(ctx, "Зелёный ритм", false, onUnlock) }) else null,
                onDigit = { d ->
                    if (wait > 0) return@PinPad
                    if (d == "del") { pin = pin.dropLast(1); return@PinPad }
                    if (pin.length >= len) return@PinPad
                    pin += d
                    error = null
                    if (pin.length == len) {
                        if (AppLock.check(ctx, pin)) { onUnlock(); return@PinPad }
                        pin = ""
                        wait = AppLock.waitSeconds(ctx)
                        error = "Неверный пароль"
                    }
                },
                extra = { TextButton(onClick = { forgot = true }) { Text("Забыли пароль?") } },
            )
        }
    }
    if (forgot) AlertDialog(
        onDismissRequest = { forgot = false },
        title = { Text("Забыли пароль") },
        text = {
            Text(
                if (AppLock.deviceSecure(ctx)) "Подтвердите, что это вы, паролем или отпечатком телефона. Пароль приложения будет отключён — задайте новый в Настройках. Данные не пострадают."
                else "На телефоне нет блокировки экрана, поэтому сбросить пароль нельзя. Помогут только «Очистить данные» приложения и восстановление из резервной копии.",
            )
        },
        confirmButton = {
            if (AppLock.deviceSecure(ctx)) TextButton(onClick = {
                forgot = false
                bioPrompt(ctx, "Сброс пароля приложения", true) { AppLock.disable(ctx); onUnlock() }
            }) { Text("Подтвердить") }
        },
        dismissButton = { TextButton(onClick = { forgot = false }) { Text("Отмена") } },
    )
}

/** Задать новый пароль: ввести дважды. */
@Composable
fun SetPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
                PinPad(
                    title = if (first == null) "Новый пароль" else "Повторите пароль",
                    subtitle = if (first == null) "4–6 цифр" else null,
                    error = error, length = first?.length ?: 6, entered = pin.length, bio = null,
                    onDigit = { d ->
                        if (d == "del") { pin = pin.dropLast(1); return@PinPad }
                        if (pin.length >= 6) return@PinPad
                        pin += d; error = null
                        val f = first
                        if (f != null && pin.length == f.length) {
                            if (pin == f) onSet(pin) else { error = "Не совпадает, начните заново"; first = null; pin = "" }
                        }
                    },
                    extra = {
                        Row {
                            TextButton(onClick = onDismiss) { Text("Отмена") }
                            if (first == null) TextButton(onClick = { first = pin; pin = "" }, enabled = pin.length >= 4) { Text("Дальше") }
                        }
                    },
                )
            }
        }
    }
}
