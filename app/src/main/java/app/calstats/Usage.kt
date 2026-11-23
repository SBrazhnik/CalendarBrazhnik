package app.calstats

import android.content.Context
import java.time.LocalDate

/** Статистика использования — хранится только на телефоне. */
object Usage {
    private fun p(ctx: Context) = ctx.getSharedPreferences("usage", Context.MODE_PRIVATE)

    fun open(ctx: Context) {
        val p = p(ctx)
        val today = LocalDate.now().toString()
        val e = p.edit().putInt("opens", p.getInt("opens", 0) + 1)
        if (p.getString("first", null) == null) e.putString("first", today)
        val last = p.getString("last_day", null)
        if (last != today) {
            e.putInt("days", p.getInt("days", 0) + 1)
            val streak = if (last == LocalDate.now().minusDays(1).toString()) p.getInt("streak", 0) + 1 else 1
            e.putInt("streak", streak).putInt("best_streak", maxOf(streak, p.getInt("best_streak", 0)))
            e.putString("last_day", today)
            // последние 60 дней использования — для графика
            val hist = (p.getString("hist", "") ?: "").split(",").filter { it.isNotBlank() }.takeLast(59) + today
            e.putString("hist", hist.joinToString(","))
        }
        e.apply()
    }

    fun module(ctx: Context, t: Tab) {
        val p = p(ctx)
        p.edit().putInt("m_" + t.name, p.getInt("m_" + t.name, 0) + 1).apply()
    }

    data class Stats(
        val first: LocalDate?,
        val days: Int,
        val opens: Int,
        val streak: Int,
        val bestStreak: Int,
        val modules: List<Pair<Tab, Int>>,
        val history: Set<LocalDate>,
    )

    fun stats(ctx: Context): Stats {
        val p = p(ctx)
        return Stats(
            p.getString("first", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            p.getInt("days", 0),
            p.getInt("opens", 0),
            p.getInt("streak", 0),
            p.getInt("best_streak", 0),
            Tab.entries.map { it to p.getInt("m_" + it.name, 0) }.filter { it.second > 0 }.sortedByDescending { it.second },
            (p.getString("hist", "") ?: "").split(",").mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(),
        )
    }
}

/** История изменений приложения. Новые версии — сверху. */
object Changelog {
    data class Entry(val build: Int, val date: String, val title: String, val items: List<String>)

    val entries = listOf(
        Entry(
            45, "23.11.2026", "Новые шрифты, настройки, полная копия",
            listOf(
                "Шрифты Inter и Manrope — строже и читабельнее",
                "Настройки: тема сверху карточками, ниже разделы плитками",
                "Резервная копия в .zip со всем содержимым: модули, карты, фото документов, аватар, настройки — в Google Диск или Яндекс Диск",
                "Автокопия раз в день в выбранную папку, хранятся последние 7",
                "Меню: кнопка «Порядок» — модули можно двигать выше/ниже и скрывать",
                "Анализ: раздел «Модули» — задачи, финансы, привычки, здоровье, дневник, авто за период",
            ),
        ),
        Entry(
            43, "23.11.2026", "Направления, даты в календаре, карты",
            listOf(
                "Календарь: свои направления с любым цветом из палитры — по цвету Google или словам в названии",
                "Сроки долгов, документов, платежи, прививки, ТО, цели и задачи попадают в календарь с напоминанием и выбранным направлением",
                "Новый модуль «Карты»: бонусные и скидочные карты — сканирование, фото, файлы .pkpass, крупный штрихкод на кассе",
                "На Главной и в обзоре Финансов — долги; настройка плиток: размер и что показывать",
            ),
        ),
        Entry(
            40, "22.11.2026", "Мой день, новые модули и многое другое",
            listOf(
                "Фото профиля: поворот, отражение, увеличение; аватары из эмодзи, как в Telegram",
                "Главная: «Мой день» одним списком, итог недели, виджет «Сегодня» (привычки и вода)",
                "Событие текстом: «завтра в 15 к врачу»; дни рождения из контактов",
                "Задачи: повторы, таймер фокуса, матрица Эйзенхауэра, перенос на завтра",
                "Финансы: операции из уведомлений банков, графики, прогноз до зарплаты, кредитный калькулятор",
                "Привычки: напоминания, «N раз в неделю», заметки, автоотметка по шагам и сну",
                "Здоровье: настроение, голодание, лекарства, цикл, рецепты",
                "Документы: распознавание номера и дат с фото, гарантия по QR-коду чека",
                "Новые модули: Покупки, Цели, Дневник, Авто, Дом, Питомцы",
            ),
        ),
        Entry(
            32, "22.11.2026", "Долги, уведомления, инструкции",
            listOf(
                "Главная: плитки можно зажать и перетащить на новое место",
                "Финансы: общая сумма накоплений и капитал",
                "Финансы → Долги: люди и компании, кто кому и за что должен, займы под процент в месяц, платежи и напоминания",
                "Уведомления: утренняя сводка дня, напоминания перед событиями календаря, вечерний итог",
                "Здоровье: история по любой дате, статистика за неделю и месяц, сравнение с прошлым периодом и выводы",
                "Новый модуль «Инструкции» — как пользоваться каждым модулем",
                "«О приложении»: что нового, история версий, статистика использования",
            ),
        ),
        Entry(
            31, "22.11.2026", "Новое меню и оформление",
            listOf(
                "Боковое меню со всеми модулями вместо нижней панели",
                "Шрифты и стиль как у corpdance.ru; темы: светлая, тёмная, «Лес»",
                "Гибкая Главная: плитки S/M/L, порядок, скрытие",
                "Профиль, проверка и установка обновлений, ссылка на скачивание",
                "Личный Telegram-бот: сводка, напоминания, заметки/задачи/траты сообщением",
                "Модуль «Подключения»: Samsung Health, Google, Яндекс, Telegram и другие",
            ),
        ),
        Entry(29, "17.11.2026", "Лёгкий интерфейс", listOf("Плоские белые карточки", "Большие и средние карточки на Главной")),
        Entry(
            28, "16.11.2026", "Документы и подписки",
            listOf("Паспорта, права, полисы, гарантии: сроки, фото, напоминания", "Подписки и пробные периоды", "Защита отпечатком"),
        ),
        Entry(
            26, "16.11.2026", "Здоровье и питание",
            listOf("Дневник питания, вода, вес, нормы КБЖУ", "Шаги, сон, пульс из Samsung Health", "Поиск продуктов и штрихкоды"),
        ),
        Entry(23, "15.11.2026", "Зелёный ритм", listOf("Новое название и логотип", "Три темы оформления", "Модули «Финансы» и «Привычки»")),
        Entry(
            1, "10.2026", "Первые версии",
            listOf("Календарь с синхронизацией Google", "Анализ направлений", "Задачи, общие расходы, желания, заметки", "Учёт алкоголя и виджеты"),
        ),
    )

    private fun p(ctx: Context) = ctx.getSharedPreferences("usage", Context.MODE_PRIVATE)

    /** Записи, которые пользователь ещё не видел (после обновления). */
    fun unseen(ctx: Context): List<Entry> {
        val cur = Updater.currentVersion(ctx).toInt()
        val seen = p(ctx).getInt("seen_build", 0)
        if (seen == 0) {
            // Первая установка этой функции — показываем только последнюю версию.
            return entries.take(1)
        }
        if (cur <= seen) return emptyList()
        return entries.filter { it.build > seen }.ifEmpty { entries.take(1) }
    }

    fun markSeen(ctx: Context) {
        p(ctx).edit().putInt("seen_build", Updater.currentVersion(ctx).toInt()).apply()
    }
}
