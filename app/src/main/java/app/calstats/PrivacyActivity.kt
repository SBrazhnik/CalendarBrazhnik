package app.calstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.calstats.ui.AppTheme

/** Экран «Как используются данные о здоровье» — его открывает Health Connect. */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(ThemeMode.SYSTEM) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("Данные о здоровье", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "«Зелёный ритм» читает из Health Connect шаги, сон, пульс, вес, тренировки и активные калории, " +
                                "чтобы показать их в модуле «Здоровье» рядом с дневником питания и дать простые советы.",
                        )
                        Text(
                            "Данные только читаются и показываются на этом телефоне. Приложение ничего не записывает в Health Connect, " +
                                "не отправляет данные о здоровье на серверы и не передаёт их третьим лицам. " +
                                "При поиске продуктов в интернет (Open Food Facts) уходит только текст запроса или штрихкод.",
                        )
                        Text("Доступ можно отозвать в любой момент: Настройки → Health Connect → Разрешения приложений.")
                        Text("Советы приложения не являются медицинскими рекомендациями.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { finish() }) { Text("Понятно") }
                    }
                }
            }
        }
    }
}
