@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.MainViewModel
import app.calstats.Screen
import app.calstats.Tab

/** Инструкция по модулю: шаги и схема экрана с отмеченными цифрами местами. */
class Guide(
    val tab: Tab?,
    val title: String,
    val intro: String,
    val steps: List<String>,
    val tips: List<String> = emptyList(),
    val screen: (() -> Unit)? = null,
    val mock: @Composable () -> Unit,
)

// ---------- Элементы схем ----------

/** Рамка «телефона» со шапкой экрана. */
@Composable
private fun Phone(title: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 300.dp).fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .border(3.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("☰", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 18.sp), modifier = Modifier.weight(1f))
            }
            content()
        }
    }
}

/** Номер шага на схеме. */
@Composable
private fun Num(n: Int) {
    Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFE0954F)), contentAlignment = Alignment.Center) {
        Text("$n", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Выделенный элемент с номером шага. */
@Composable
private fun Mark(n: Int?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        Box(if (n != null) Modifier.border(2.dp, Color(0xFFE0954F), RoundedCornerShape(10.dp)).padding(2.dp) else Modifier) { content() }
        if (n != null) Box(Modifier.align(Alignment.TopEnd).padding(1.dp)) { Num(n) }
    }
}

@Composable
private fun Chips(items: List<String>, selected: Int = 0, mark: Int? = null) {
    Mark(mark) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, t ->
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (i == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(t, fontSize = 10.sp, color = if (i == selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MCard(title: String, value: String? = null, sub: String? = null, mark: Int? = null, modifier: Modifier = Modifier, color: Color? = null) {
    Mark(mark, modifier) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(8.dp)) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) Text(value, style = MaterialTheme.typography.displaySmall.copy(fontSize = 18.sp, lineHeight = 20.sp), maxLines = 1)
            if (sub != null) Text(sub, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun MRow(text: String, right: String = "", mark: Int? = null) {
    Mark(mark) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(text, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(right, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Fab(text: String, mark: Int? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Mark(mark) {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("+ $text", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun Two(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit, gap: Dp = 6.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        a(Modifier.weight(1f))
        b(Modifier.weight(1f))
    }
}

// ---------- Инструкции ----------

val GUIDES: List<Guide> = listOf(
    Guide(
        null, "Начало работы",
        "Приложение собрано из модулей. Включите нужные, остальные спрячьте — данные не пропадут.",
        listOf(
            "Кнопка ☰ слева вверху открывает меню со всеми модулями.",
            "Внизу меню — Профиль, Инструкции, Модули, Настройки и «О приложении».",
            "«Модули» — включить или выключить модуль и поменять порядок в меню.",
            "Тема оформления (светлая, тёмная, «Лес») — в Настройках.",
        ),
        tips = listOf("Жест «назад» возвращает на предыдущий модуль, двойной «назад» — выход."),
    ) {
        Row(Modifier.fillMaxWidth().widthIn(max = 300.dp), horizontalArrangement = Arrangement.Center) {
            Column(
                Modifier.width(170.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Mark(1) { Text("☰  Зелёный ритм", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                listOf("Главная", "Календарь", "Задачи", "Финансы", "Здоровье").forEachIndexed { i, t ->
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(if (i == 0) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).padding(4.dp),
                    ) { Text(t, fontSize = 10.sp, color = if (i == 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface) }
                }
                HorizontalDivider()
                Mark(2) { Text("Профиль · Инструкции", fontSize = 10.sp) }
                Mark(3) { Text("Модули", fontSize = 10.sp) }
                Mark(4) { Text("Настройки", fontSize = 10.sp) }
            }
        }
    },
    Guide(
        Tab.HOME, "Главная",
        "Сводка дня из всех модулей. Плитки можно расставить как удобно.",
        listOf(
            "Быстрые кнопки: + Трата, + Событие, + Задача, + Заметка.",
            "Нажмите плитку — откроется модуль.",
            "Зажмите плитку и перетащите на новое место.",
            "В режиме настройки (✎ вверху) S / M / L меняет размер, глаз — скрывает плитку.",
        ),
    ) {
        Phone("Главная") {
            Mark(1) { Chips(listOf("+ Трата", "+ Событие", "+ Задача"), -1) }
            Mark(2) { MCard("Календарь", "3 события", "10:00 Планёрка") }
            Two({ MCard("Финансы", "1 250 ₽", "сегодня", modifier = it) }, { MCard("Здоровье", "1 240", "ккал из 2100", mark = 3, modifier = it) })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MCard("Привычки", "3/5", modifier = Modifier.weight(1f))
                MCard("Документы", "2", modifier = Modifier.weight(1f))
                Mark(4, Modifier.weight(1f)) { MCard("S M L 👁", "", "размер") }
            }
        }
    },
    Guide(
        Tab.CALENDAR, "Календарь",
        "События из Google Календаря телефона: всё, что добавлено здесь, появляется в Google, и наоборот.",
        listOf(
            "Переключатель вида: список, день, 3 дня, неделя, расписание.",
            "Месяц сверху сворачивается — потяните его вверх или вниз.",
            "Нажмите на день, затем «+», чтобы добавить событие с повтором и напоминаниями.",
            "Строка «Пил / Не пил» под днём — учёт алкоголя прямо в календаре.",
        ),
        tips = listOf("Цвет события определяет направление в «Анализе».", "⟳ — синхронизировать с Google сейчас."),
    ) {
        Phone("Календарь") {
            Chips(listOf("Список", "День", "3 дня", "Неделя"), 0, 1)
            Mark(2) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(6.dp)) {
                    Text("Ноябрь", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("пн вт ср чт пт сб вс", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("17 18 19 20 21 22 23", fontSize = 9.sp)
                }
            }
            MRow("10:00 Планёрка", "1 ч")
            MRow("19:00 Тренировка", "1,5 ч")
            Mark(4) { Chips(listOf("Не пил", "Пил"), 0) }
            Fab("Событие", 3)
        }
    },
    Guide(
        Tab.TASKS, "Задачи",
        "Задачи и проекты как в Jira: статусы, исполнители, сроки, спринты.",
        listOf(
            "Выберите проект или «Все задачи».",
            "Вид: доска по статусам или список.",
            "«+ Задача» — название, срок, исполнитель, приоритет. Сохраняется сразу.",
            "Задачу можно получить из Telegram: «задача Позвонить в банк».",
        ),
    ) {
        Phone("Задачи") {
            Chips(listOf("Все", "Дом", "Работа"), 0, 1)
            Chips(listOf("Доска", "Список"), 0, 2)
            Two({ MCard("К работе", null, "• Купить билеты\n• Отчёт", modifier = it) }, { MCard("В работе", null, "• Ремонт", modifier = it) })
            Fab("Задача", 3)
        }
    },
    Guide(
        Tab.FINANCE, "Финансы",
        "Счета, операции, бюджеты, регулярные платежи, цели и накопления.",
        listOf(
            "«Обзор» — баланс всех счетов, доходы и расходы месяца, накопления и капитал.",
            "«+ Операция» — расход, доход или перевод между счетами.",
            "«Бюджеты» — лимиты по категориям, «Платежи» — подписки и ЖКХ, «Цели» — на что копите.",
            "⋮ → «Импорт выписки» загружает CSV из банка (Т-Банк, Сбер и др.).",
        ),
        tips = listOf("Трату можно записать и сообщением боту: «трата 350 кофе»."),
    ) {
        Phone("Финансы") {
            Chips(listOf("Обзор", "Операции", "Долги", "Бюджеты"), 0, 1)
            MCard("Все счета", "84 300 ₽", "доходы +120 000 · расходы −35 700")
            MCard("Накопления", "250 000 ₽", "мне должны 15 000 · я должен 5 000", mark = 3)
            Fab("Операция", 2)
        }
    },
    Guide(
        Tab.FINANCE, "Долги и займы",
        "Финансы → «Долги»: кто сколько и за что должен — люди и компании, с процентами в месяц.",
        listOf(
            "Вверху — сколько должны вам и сколько должны вы, итог и просрочки.",
            "«+ Мне должны» / «+ Я должен»: выберите человека или компанию (или введите новые), за что, сумму, срок и процент в месяц.",
            "Карточка долга показывает остаток, набежавшие проценты и сколько будет к сроку.",
            "«Мне вернули» / «Я заплатил» — частичный или полный платёж; сначала гасятся проценты.",
        ),
        tips = listOf("Проценты простые: начисляются каждый день на остаток долга.", "«Напомнить» отправит вежливое сообщение в любой мессенджер."),
    ) {
        Phone("Финансы") {
            Chips(listOf("Обзор", "Операции", "Долги"), 2)
            Mark(1) { Two({ MCard("Мне должны", "45 000 ₽", modifier = it, color = Color(0xFF3F8A5A)) }, { MCard("Я должен", "12 000 ₽", modifier = it, color = Color(0xFFC0583F)) }) }
            Mark(2) { Chips(listOf("+ Мне должны", "+ Я должен"), 0) }
            MRow("Иван · ↙ ремонт машины · 3%/мес", "31 800 ₽", 3)
            MRow("ООО «Ромашка» · ↗ аренда", "12 000 ₽")
            MRow("Мне вернули 5 000 ₽", "", 4)
        }
    },
    Guide(
        Tab.HABITS, "Привычки",
        "Полезные привычки и те, что хотите бросить. Серии и проценты выполнения.",
        listOf(
            "Нажмите кружок дня, чтобы отметить выполнение.",
            "«Бросить» — отмечайте срывы; серия считает дни без срыва.",
            "Карточка привычки — месяц целиком и статистика.",
            "«+ Привычка» — шаблоны: вода, зарядка, чтение, шаги…",
        ),
    ) {
        Phone("Привычки") {
            MRow("💧 Вода 8 стаканов   ● ● ● ○ ○ ○ ○", "3/7", 1)
            MRow("🚭 Не курю — 12 дней", "", 2)
            MRow("📖 Чтение 20 мин   ● ● ○", "", 3)
            Fab("Привычка", 4)
        }
    },
    Guide(
        Tab.HEALTH, "Здоровье и питание",
        "Питание, вода, вес и данные Samsung Health: шаги, сон, пульс, тренировки.",
        listOf(
            "Стрелками или датой вверху выберите любой день — всё покажется за него.",
            "Кнопки приёмов пищи: поиск в своей базе, в интернете или по штрихкоду.",
            "«+ стакан» — учёт воды; «Записать» — вес.",
            "«Статистика» — средние за неделю и месяц, сравнение с прошлым периодом и выводы.",
            "«Профиль» — рост, вес, цель: по ним считаются нормы КБЖУ и воды.",
        ),
        tips = listOf("Подключите Samsung Health: Подключения → Samsung Health → «Подключить»."),
    ) {
        Phone("Здоровье") {
            Chips(listOf("День", "Питание", "Активность", "Статистика"), 0, 4)
            Mark(1) { MRow("‹   Сегодня, 22 ноября   ›") }
            MCard("Питание", "1 240 ккал", "осталось 860 · Б 70 Ж 40 У 150")
            Chips(listOf("🍳 Завтрак", "🍲 Обед +"), -1, 2)
            Two({ MCard("💧 Вода", "1 250 мл", mark = 3, modifier = it) }, { MCard("🚶 Шаги", "6 420", modifier = it) })
        }
    },
    Guide(
        Tab.DOCS, "Документы и подписки",
        "Сроки документов, их номера и фото; подписки и пробные периоды.",
        listOf(
            "«+ Документ» — тип, владелец, номер, срок действия, фото.",
            "Документы с истекающим сроком поднимаются наверх.",
            "Вкладка «Подписки» — сколько уходит в месяц и год.",
            "Защита отпечатком включается внизу списка.",
        ),
    ) {
        Phone("Документы") {
            Chips(listOf("Документы", "Подписки"), 0, 3)
            MRow("🛡 ОСАГО · •••• 4521", "12 дн", 2)
            MRow("🪪 Паспорт РФ", "до 2031")
            Fab("Документ", 1)
            MRow("🔒 Защита отпечатком", "", 4)
        }
    },
    Guide(
        Tab.MONEY, "Общие расходы",
        "Делите траты с друзьями в поездке или дома — как Kittysplit.",
        listOf(
            "Создайте «счёт» (поездку) и добавьте участников.",
            "Добавляйте траты: кто платил и на кого делить.",
            "Приложение посчитает, кто кому сколько должен, минимальным числом переводов.",
        ),
    ) {
        Phone("Расходы") {
            MCard("Поездка в Казань", "вам должны 3 200 ₽", "4 участника", mark = 1)
            MRow("Отель · платил Иван", "18 000 ₽", 2)
            MRow("Аня → Вам", "3 200 ₽", 3)
        }
    },
    Guide(
        Tab.ANALYSIS, "Анализ",
        "Сколько дней и часов вы уделяете каждому направлению жизни.",
        listOf(
            "Выберите период: неделя, месяц, год, свой.",
            "Направления определяются цветом события в календаре.",
            "Нажмите направление — разбивка по месяцам и список событий.",
        ),
    ) {
        Phone("Анализ") {
            Chips(listOf("Неделя", "Месяц", "Год"), 1, 1)
            MRow("🟢 Спорт · 12 дней", "18 ч", 3)
            MRow("🔵 Работа · 20 дней", "160 ч")
            MRow("Цвет = направление", "", 2)
        }
    },
    Guide(
        Tab.WISHES, "Желания",
        "Списки желаний к праздникам и поводам — как Подаркус.",
        listOf("Создайте список (день рождения, Новый год).", "Добавляйте желания со ссылкой и ценой.", "Отмечайте подаренное; делитесь списком."),
    ) {
        Phone("Желания") {
            MCard("🎂 День рождения", "через 24 дня", "6 желаний", mark = 1)
            MRow("Наушники · ozon.ru", "9 990 ₽", 2)
            MRow("✓ Книга", "подарено", 3)
        }
    },
    Guide(
        Tab.NOTES, "Заметки",
        "Заметки и чек-листы. Можно принимать текст из любого приложения.",
        listOf(
            "«+ Заметка» — текст или список с галочками.",
            "В Samsung Notes, Keep, браузере: «Поделиться» → «В Зелёный ритм».",
            "Импорт архива Google Keep (Takeout) — в меню модуля.",
        ),
    ) {
        Phone("Заметки") {
            MCard("Покупки", null, "☑ молоко  ☐ хлеб  ☐ яйца", mark = 1)
            MRow("↪ Поделиться → В Зелёный ритм", "", 2)
            MRow("⋮ Импорт из Google Keep", "", 3)
        }
    },
    Guide(
        Tab.ACCOUNTS, "Подключения и Telegram",
        "Откуда приложение берёт данные: Samsung Health, Google, Яндекс, Telegram, банк.",
        listOf(
            "Samsung Health → «Подключить» и разрешите доступ в Health Connect.",
            "Telegram: создайте бота у @BotFather, вставьте токен, нажмите «Старт» в боте.",
            "Боту можно писать: «задача …», «трата 350 кофе», любой текст — заметка.",
        ),
    ) {
        Phone("Подключения") {
            MRow("❤ Samsung Health · шаги, сон", "Подключить", 1)
            MRow("G Google · календарь", "✓")
            MRow("✈ Telegram · бот", "Подключить", 2)
            MRow("💬 «трата 350 кофе»", "→ Финансы", 3)
        }
    },
    Guide(
        Tab.SHOPPING, "Покупки",
        "Списки покупок: продукты, аптека, дом. Отмечайте прямо в магазине.",
        listOf(
            "Вверху — списки; «+ список» создаёт новый.",
            "Пишите через запятую: «молоко 2, хлеб, яйца 10 шт» — каждая позиция добавится отдельно.",
            "Нажмите позицию — она зачеркнётся и уйдёт вниз.",
            "Кнопка «Поделиться» отправит список в мессенджер.",
        ),
        tips = listOf("Текст из другого приложения: «Поделиться» → «В Зелёный ритм» → «В покупки»."),
    ) {
        Phone("Покупки") {
            Chips(listOf("🛒 Продукты · 3", "💊 Аптека"), 0, 1)
            MRow("молоко 2, хлеб…", "+", 2)
            MRow("☑ Хлеб", "", 3)
            MRow("☐ Молоко", "2")
        }
    },
    Guide(
        Tab.CARDS, "Бонусные карты",
        "Все карты магазинов в одном месте: на кассе — крупный штрихкод и яркий экран.",
        listOf(
            "«+ Карта» → «Сканировать штрихкод»: наведите камеру на карту или на экран другого приложения.",
            "«С фото карты»: выберите фото или скриншот — штрихкод найдётся сам, фото сохранится.",
            "Файлы .pkpass (Wallet, PassWallet) открываются прямо в приложении — «Открыть с помощью».",
            "Нажмите карту на кассе — экран станет ярким. Часто используемые поднимаются наверх.",
        ),
        tips = listOf("Google Кошелёк и «Кошелёк» не отдают карты другим приложениям — переносите сканированием или скриншотом."),
    ) {
        Phone("Карты") {
            Two({ MCard("Пятёрочка", null, "•• 4521", mark = 4, modifier = it, color = Color(0xFFE31E24)) }, { MCard("Лента", null, "•• 1180", modifier = it, color = Color(0xFF003C96)) })
            MRow("📷 Сканировать штрихкод", "", 1)
            MRow("🖼 С фото карты", "", 2)
            MRow("📥 Файл .pkpass", "", 3)
        }
    },
    Guide(
        Tab.GOALS, "Цели",
        "Цели на год по сферам жизни с шагами и прогрессом.",
        listOf("«+ Цель» — название, сфера, зачем это вам.", "Добавьте шаги — прогресс посчитается по выполненным.", "Отмечайте шаги прямо в карточке цели."),
    ) {
        Phone("Цели") {
            MCard("Прогресс года", "42%", mark = 2)
            MCard("💪 Пробежать 10 км", null, "☑ 3 км  ☐ 5 км  ☐ 10 км", mark = 3)
            Fab("Цель", 1)
        }
    },
    Guide(
        Tab.JOURNAL, "Дневник",
        "Короткая запись дня, три благодарности и лучший момент.",
        listOf("Каждый день — новый вопрос-подсказка.", "Заполните благодарности и лучший момент дня.", "Стрелками или датой откройте прошлые записи."),
    ) {
        Phone("Дневник") {
            MRow("‹  Сегодня  ›", "", 3)
            MCard("Что сегодня получилось лучше всего?", null, "…", mark = 1)
            MCard("🙏 Три благодарности", null, "1. … 2. … 3. …", mark = 2)
        }
    },
    Guide(
        Tab.CAR, "Авто",
        "Включается в «Модулях». Заправки, ТО, ремонт и траты на машину.",
        listOf("Добавьте машину и интервал ТО (км и месяцы).", "«+ Запись» — заправка с литрами и пробегом, ТО, ремонт, штраф.", "Расход л/100 км и траты за месяц считаются сами; о ТО напомню."),
    ) {
        Phone("Авто") {
            Two({ MCard("Пробег", "54 300 км", modifier = it) }, { MCard("Расход", "8,4 л", mark = 3, modifier = it) })
            MRow("⛽ Заправка · 40 л", "2 400 ₽", 2)
            MRow("🔧 Следующее ТО через 900 км", "", 3)
            Fab("Запись", 2)
        }
    },
    Guide(
        Tab.HOME_UTIL, "Дом",
        "Включается в «Модулях». Показания счётчиков и расход за месяц.",
        listOf("Добавьте счётчики: вода, электричество, газ.", "Нажмите счётчик — введите показание и тариф.", "С 20 по 25 число напомню передать показания."),
    ) {
        Phone("Дом") {
            MRow("🚰 Холодная вода · 123,4 м³", "", 1)
            MRow("⚡ Электричество · расход 180", "990 ₽", 2)
            MRow("Пора передать показания", "", 3)
        }
    },
    Guide(
        Tab.PETS, "Питомцы",
        "Включается в «Модулях». Прививки, обработки, ветеринар, вес.",
        listOf("«+ Питомец» — кличка, порода, дата рождения, чип.", "«+ Событие» — прививка или обработка: дата повтора ставится сама.", "Ближайшие дела — вверху и в утренней сводке."),
    ) {
        Phone("Питомцы") {
            MCard("Скоро", null, "💉 Рекс: прививка через 5 дн.", mark = 3)
            MRow("🐶 Рекс · 3 г. · 24 кг", "", 1)
            MRow("+ Событие", "", 2)
        }
    },
    Guide(
        null, "Уведомления",
        "Утром — план на день, перед событиями — напоминания, вечером — что не отмечено.",
        listOf(
            "Настройки → «Уведомления»: включите утреннюю сводку и выберите время.",
            "«Напоминать о событиях» — за 5, 10, 15, 30 минут или за час.",
            "«Вечерний итог» — неотмеченные привычки, задачи и платежи на завтра.",
            "Если уведомления не приходят: Настройки Android → Приложения → Зелёный ритм → Батарея → «Без ограничений».",
        ),
        screen = null,
    ) {
        Phone("Настройки") {
            MRow("Утренняя сводка · 08:00", "●", 1)
            MRow("Напоминать о событиях · за 15 мин", "●", 2)
            MRow("Вечерний итог · 21:00", "●", 3)
        }
    },
    Guide(
        null, "Профиль и обновления",
        "Фото и имя, обновления приложения, ссылка на скачивание.",
        listOf(
            "Меню → Профиль: нажмите на фото, чтобы сменить его.",
            "«Обновления» — проверить и установить новую версию; автообновление предложит её само.",
            "«Поделиться ссылкой» — отправить приложение на другой телефон.",
            "«О приложении» — что нового, история версий и ваша статистика.",
        ),
    ) {
        Phone("Профиль") {
            Mark(1) { Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)) }
            MRow("Доступна версия 34", "Установить", 2)
            MRow("Поделиться ссылкой", "", 3)
            MRow("О приложении", "", 4)
        }
    },
)

/** Модуль «Инструкции». */
@Composable
fun GuideTab(vm: MainViewModel, snackbar: SnackbarHostState) {
    var open by rememberSaveable { mutableStateOf(-1) }
    var q by rememberSaveable { mutableStateOf("") }
    val list = GUIDES.withIndex().filter { (_, g) ->
        q.isBlank() || (g.title + g.intro + g.steps.joinToString() + g.tips.joinToString()).contains(q.trim(), ignoreCase = true)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = { MenuButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Инструкции", style = MaterialTheme.typography.headlineSmall) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    q, { q = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск: долг, штрихкод, уведомления…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                )
            }
            items(list, key = { it.index }) { (i, g) ->
                val expanded = open == i || q.isNotBlank()
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().clickable { open = if (open == i) -1 else i }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (g.tab != null) ModuleBadge(g.tab, 34.dp)
                            else Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Text("✦") }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.title, style = MaterialTheme.typography.titleMedium)
                                Text(g.intro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) 4 else 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                        }
                        if (expanded) {
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                g.mock()
                                g.steps.forEachIndexed { n, s ->
                                    Row {
                                        Num(n + 1)
                                        Spacer(Modifier.width(10.dp))
                                        Text(s, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                g.tips.forEach { Text("💡 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                val t = g.tab
                                when {
                                    t != null && t != Tab.HOME && vm.isEnabled(t) -> OutlinedButton(onClick = { vm.tab = t }) { Text("Открыть «${t.title}»") }
                                    t != null && t != Tab.HOME -> OutlinedButton(onClick = { vm.setModuleEnabled(t, true); vm.tab = t }) { Text("Включить модуль «${t.title}»") }
                                    g.title == "Уведомления" -> OutlinedButton(onClick = { vm.push(Screen.Settings) }) { Text("Открыть настройки") }
                                    g.title == "Профиль и обновления" -> OutlinedButton(onClick = { vm.push(Screen.Profile) }) { Text("Открыть профиль") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
