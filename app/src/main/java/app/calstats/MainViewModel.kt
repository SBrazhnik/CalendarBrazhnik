package app.calstats

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import app.calstats.widget.CalendarWidget
import app.calstats.widget.TasksWidget
import app.calstats.health.HealthLink
import app.calstats.work.HealthSnapshot
import app.calstats.work.BankImport
import app.calstats.work.Issue
import app.calstats.work.NoteImport
import app.calstats.work.PALETTE
import app.calstats.work.Project
import app.calstats.work.Wish
import app.calstats.work.newId
import app.calstats.work.ME
import app.calstats.work.WorkLog
import app.calstats.work.Status
import app.calstats.work.WorkOps
import app.calstats.work.WorkStore
import app.calstats.work.Workspace
import java.time.LocalDate
import java.time.ZoneId

/** Режим нижней части модуля «Календарь». */
enum class CalView(val title: String, val days: Int) {
    LIST("Список", 0), DAY("День", 1), THREE("3 дня", 3), WEEK("Неделя", 7), SCHEDULE("Расписание", 0),
}

/** Снимок состояния модулей — для жеста «назад». */
data class NavSnap(val tab: Tab, val calView: CalView, val taskView: String, val projectId: String?, val sub: Map<String, Any?> = emptyMap())

/**
 * Главная и модули приложения. Новый модуль = новая строка здесь + его экран;
 * включение, порядок и место в нижней панели настраиваются в «Модулях».
 */
enum class Tab(val title: String, val description: String) {
    HOME("Главная", ""),
    CALENDAR("Календарь", "События Google Календаря, учёт алкоголя"),
    TASKS("Задачи", "Проекты, доска, спринты, учёт времени"),
    FINANCE("Финансы", "Счета, бюджеты, подписки, цели, импорт выписок"),
    HABITS("Привычки", "Ежедневные отметки, серии, учёт алкоголя"),
    HEALTH("Здоровье", "Питание, вода, вес, шаги и сон из Samsung Health"),
    DOCS("Документы", "Паспорта, права, полисы, гарантии, подписки и их сроки"),
    ACCOUNTS("Подключения", "Samsung Health, Google, Яндекс, Telegram и другие учётные записи"),
    SHOPPING("Покупки", "Списки покупок: продукты, аптека, дом — отмечайте в магазине"),
    CARDS("Карты", "Бонусные и скидочные карты магазинов: штрихкод на кассе"),
    GOALS("Цели", "Цели на год по сферам жизни: шаги и прогресс"),
    JOURNAL("Дневник", "Запись дня, благодарности и лучший момент"),
    CAR("Авто", "Заправки, ТО, расходы, расход топлива и пробег"),
    HOME_UTIL("Дом", "Показания счётчиков, расход и напоминание передать"),
    PETS("Питомцы", "Прививки, обработки, ветеринар и вес питомцев"),
    GUIDE("Инструкции", "Как пользоваться каждым модулем — по шагам с картинками"),
    MONEY("Расходы", "Общие чеки и кто кому должен"),
    ANALYSIS("Анализ", "Дни и часы по направлениям"),
    WISHES("Желания", "Списки желаний и подарков"),
    NOTES("Заметки", "Заметки, чек-листы, импорт из других приложений"),
    MEALS("Меню", "Меню на неделю, рецепты и список продуктов одним нажатием"),
    WORKOUTS("Тренировки", "Упражнения, подходы и веса, прогресс и рекорды"),
    TRIPS("Поездки", "Чек-лист вещей, брони, бюджет поездки, проверка документов"),
    READING("Чтение", "Книги и курсы: прогресс, цель на год, серия дней"),
    CHORES("Дела по дому", "Уборка и регулярные дела по графику, очередь в семье"),
    PLANTS("Растения", "Полив и подкормка по расписанию"),
    DEV("Доработка", "Идеи, ошибки и вопросы разработчику — с ответами. Свой пароль"),
    ;

    val isModule: Boolean get() = this != HOME

    companion object {
        val modules: List<Tab> get() = entries.filter { it.isModule }
    }
}

sealed interface Screen {
    data object Main : Screen
    data class Direction(val key: String) : Screen
    data class Edit(
        val eventId: Long? = null,
        val instanceBegin: Long? = null,
        val instanceEnd: Long? = null,
        val presetColor: Int? = null,
        val presetDate: LocalDate? = null,
        val presetStart: java.time.LocalDateTime? = null,
        val presetAllDay: Boolean = false,
        val presetTitle: String? = null,
    ) : Screen
    data object Colors : Screen
    data object Profile : Screen
    data object About : Screen
    data class DocEdit(val id: String?) : Screen
    data class IssueDetail(val id: String) : Screen
    data object People : Screen
    data class ProjectEdit(val id: String?) : Screen
    data class Book(val id: String) : Screen
    data class ExpenseEdit(val bookId: String, val expenseId: String?) : Screen
    data object Settings : Screen
    data object Search : Screen
    data object Trash : Screen
    data class Reader(val bookId: String) : Screen
    data object Modules : Screen
    data class NoteEdit(val id: String) : Screen
    data class WishListDetail(val id: String) : Screen
}

/** Тема оформления. */
enum class TileSize(val title: String) { SMALL("Маленькая"), MEDIUM("Средняя"), LARGE("Большая") }

enum class ThemeMode(val title: String, val description: String) {
    SYSTEM("Как в системе", "Днём светлая, ночью «Лес»"),
    LIGHT("Светлая", "Светло-серый фон, белые карточки, чернильный и лавандовый"),
    DARK("Тёмная", "Ночной синий, графитовые карточки, лавандовый акцент"),
    FOREST("Лес", "Тёмно-зелёная: лес, латунь и пергамент"),
}

/** Текст, присланный из другого приложения через «Поделиться». */
data class SharedText(val text: String, val subject: String?, val source: String)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CalendarRepository(app)
    private val store = DataStore(app)
    private val workStore = WorkStore(app)
    private val sobrietyStore = SobrietyStore(app)
    private val resolver = app.contentResolver

    val zone: ZoneId get() = ZoneId.systemDefault()

    /** Сколько лет истории загружаем для статистики «за всё время». */
    private val historyYears = 15L
    val loadStart: LocalDate = LocalDate.now().withDayOfYear(1).minusYears(historyYears)
    val loadEnd: LocalDate = LocalDate.now().withDayOfYear(1).plusYears(3)

    var hasPermission by mutableStateOf(checkPermission())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadedOnce by mutableStateOf(false)
        private set
    var calendars by mutableStateOf<List<CalendarInfo>>(emptyList())
        private set
    var instances by mutableStateOf<List<EventInstance>>(emptyList())
        private set
    var data by mutableStateOf(store.load())
        private set
    var period by mutableStateOf(Period.initial(LocalDate.now()))
    var today by mutableStateOf(LocalDate.now())
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set
    var message by mutableStateOf<String?>(null)

    val stack = mutableStateListOf<Screen>(Screen.Main)

    private val uiPrefs = app.getSharedPreferences("calstats_ui", android.content.Context.MODE_PRIVATE)

    /** История переходов между модулями и видами: «назад» возвращает к предыдущему. */
    private val navHistory = mutableListOf<NavSnap>()
    private var restoring = false
    private fun snap() = NavSnap(tabState, calViewState, taskViewState, projectState, subViews.toMap())

    /** Внутренние вкладки модулей (Обзор/Долги, Сегодня/История…) — тоже попадают в историю «назад». */
    val subViews = androidx.compose.runtime.mutableStateMapOf<String, Any?>()
    fun setSubView(key: String, v: Any?) {
        if (subViews[key] == v) return
        val before = snap()
        subViews[key] = v
        record(before)
    }
    private fun record(before: NavSnap) {
        if (restoring || before == snap()) return
        navHistory += before
        if (navHistory.size > 40) navHistory.removeAt(0)
    }

    // ---------- модули ----------

    private fun loadTabs(key: String, default: List<Tab>): List<Tab> =
        uiPrefs.getString(key, null)?.split(",")?.mapNotNull { n -> Tab.entries.firstOrNull { it.name == n } } ?: default

    /** Порядок модулей (новые модули добавляются в конец). */
    var moduleOrder by mutableStateOf(
        loadTabs("modules_order", Tab.modules).let { saved ->
            val list = saved.filter { it.isModule }.toMutableList()
            // Финансы встают сразу после Задач, остальные новые — в конец.
            if (Tab.FINANCE !in list) list.add((list.indexOf(Tab.TASKS) + 1).coerceIn(0, list.size), Tab.FINANCE)
            if (Tab.HABITS !in list) list.add((list.indexOf(Tab.FINANCE) + 1).coerceIn(0, list.size), Tab.HABITS)
            if (Tab.HEALTH !in list) list.add((list.indexOf(Tab.HABITS) + 1).coerceIn(0, list.size), Tab.HEALTH)
            if (Tab.DOCS !in list) list.add((list.indexOf(Tab.HEALTH) + 1).coerceIn(0, list.size), Tab.DOCS)
            list + Tab.modules.filter { it !in list }
        },
    )
        private set
    var disabledModules by mutableStateOf(
        loadTabs("modules_disabled", emptyList()).toSet().let { d ->
            // Авто, Дом и Питомцы нужны не всем — включаются в «Модулях».
            var r = d
            if (!uiPrefs.getBoolean("migr_life", false)) {
                r = r + listOf(Tab.CAR, Tab.HOME_UTIL, Tab.PETS)
                uiPrefs.edit().putBoolean("migr_life", true).putString("modules_disabled", r.joinToString(",") { it.name }).apply()
            }
            // Новые модули включаются по желанию в меню («Порядок») или в «Модулях».
            if (!uiPrefs.getBoolean("migr_extra6", false)) {
                r = r + listOf(Tab.MEALS, Tab.WORKOUTS, Tab.TRIPS, Tab.READING, Tab.CHORES, Tab.PLANTS)
                uiPrefs.edit().putBoolean("migr_extra6", true).putString("modules_disabled", r.joinToString(",") { it.name }).apply()
            }
            // «Доработка» — для владельца приложения, включается в «Модулях»
            if (!uiPrefs.getBoolean("migr_dev", false)) {
                r = r + Tab.DEV
                uiPrefs.edit().putBoolean("migr_dev", true).putString("modules_disabled", r.joinToString(",") { it.name }).apply()
            }
            r
        },
    )
        private set
    /** Модули, которые НЕ показываются в нижней панели (по умолчанию там первые четыре). */
    var unpinnedModules by mutableStateOf(
        loadTabs("modules_unpinned", listOf(Tab.WISHES, Tab.NOTES, Tab.MONEY, Tab.HABITS, Tab.HEALTH, Tab.DOCS)).toSet().let { saved ->
            // Один раз освобождаем место в панели под Финансы: Расходы открываются с Главной и из Финансов.
            var r = saved
            if (!uiPrefs.getBoolean("migr_finance", false)) {
                uiPrefs.edit().putBoolean("migr_finance", true).apply()
                r = r + Tab.MONEY
            }
            if (!uiPrefs.getBoolean("migr_habits", false)) {
                uiPrefs.edit().putBoolean("migr_habits", true).apply()
                r = r + Tab.HABITS
            }
            if (!uiPrefs.getBoolean("migr_health", false)) {
                uiPrefs.edit().putBoolean("migr_health", true).apply()
                r = r + Tab.HEALTH
            }
            if (!uiPrefs.getBoolean("migr_docs", false)) {
                uiPrefs.edit().putBoolean("migr_docs", true).apply()
                r = r + Tab.DOCS
            }
            r
        },
    )
        private set

    val enabledModules: List<Tab> get() = moduleOrder.filter { it !in disabledModules }

    /** Нижняя панель: Главная + до 4 закреплённых включённых модулей. */
    val bottomTabs: List<Tab> get() = listOf(Tab.HOME) + enabledModules.filter { it !in unpinnedModules }.take(4)

    fun isEnabled(t: Tab) = t == Tab.HOME || t !in disabledModules

    // ---------- Гибкая Главная ----------
    /** Размер плитки модуля на Главной. */
    var tileSizes by mutableStateOf(
        (uiPrefs.getString("tile_sizes", null)?.split(",")?.mapNotNull { e ->
            val (k, v) = e.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            val t = Tab.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
            val z = TileSize.entries.firstOrNull { it.name == v } ?: return@mapNotNull null
            t to z
        }?.toMap()) ?: run {
            // Перенос из прошлой версии: большие карточки остаются большими.
            val large = loadTabs("cards_large", listOf(Tab.CALENDAR, Tab.TASKS, Tab.FINANCE))
            Tab.modules.associateWith { if (it in large) TileSize.LARGE else TileSize.MEDIUM } + (Tab.ACCOUNTS to TileSize.SMALL)
        },
    )
        private set
    fun tileSize(t: Tab): TileSize = tileSizes[t] ?: TileSize.MEDIUM
    fun setTileSize(t: Tab, z: TileSize) {
        tileSizes = tileSizes + (t to z)
        uiPrefs.edit().putString("tile_sizes", tileSizes.entries.joinToString(",") { "${it.key.name}:${it.value.name}" }).apply()
    }

    /** Порядок плиток на Главной (не зависит от порядка в меню). */
    var homeOrder by mutableStateOf(loadTabs("home_order", emptyList()).filter { it.isModule }.let { it + Tab.modules.filter { t -> t !in it } })
        private set
    /** Модули, скрытые только с Главной (в меню остаются). */
    var homeHidden by mutableStateOf(loadTabs("home_hidden", emptyList()).toSet())
        private set
    val homeTiles: List<Tab> get() = homeOrder.filter { isEnabled(it) && it !in homeHidden }

    fun moveTile(t: Tab, delta: Int) {
        val vis = homeTiles
        val i = vis.indexOf(t)
        val j = i + delta
        if (i < 0 || j !in vis.indices) return
        val other = vis[j]
        val list = homeOrder.toMutableList()
        val a = list.indexOf(t)
        val b = list.indexOf(other)
        list[a] = other
        list[b] = t
        homeOrder = list
        uiPrefs.edit().putString("home_order", list.joinToString(",") { it.name }).apply()
    }

    /** Перетаскивание: поставить плитку [t] на место плитки [target]. */
    fun moveTileTo(t: Tab, target: Tab) {
        if (t == target) return
        val list = homeOrder.toMutableList()
        val from = list.indexOf(t)
        val to = list.indexOf(target)
        if (from < 0 || to < 0) return
        list.removeAt(from)
        list.add(list.indexOf(target) + if (from < to) 1 else 0, t)
        homeOrder = list
        uiPrefs.edit().putString("home_order", list.joinToString(",") { it.name }).apply()
    }

    fun setTileHidden(t: Tab, hidden: Boolean) {
        homeHidden = if (hidden) homeHidden + t else homeHidden - t
        uiPrefs.edit().putString("home_hidden", homeHidden.joinToString(",") { it.name }).apply()
    }

    var editHome by mutableStateOf(false)

    /** Что показывает плитка модуля (ключ варианта из TILE_VARIANTS). */
    var tileVariants by mutableStateOf(
        uiPrefs.getString("tile_var", "").orEmpty().split(",").mapNotNull { e -> e.split(":").takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap(),
    )
        private set
    fun tileVariant(t: Tab): String? = tileVariants[t.name]
    fun setTileVariant(t: Tab, key: String) {
        tileVariants = tileVariants + (t.name to key)
        uiPrefs.edit().putString("tile_var", tileVariants.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }

    /** Какие блоки показывать в большой карточке «Финансы». */
    var finSections by mutableStateOf(uiPrefs.getString("fin_sections", null)?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: setOf("spent", "budget", "payments", "debts"))
        private set
    fun toggleFinSection(k: String) {
        finSections = if (k in finSections) finSections - k else finSections + k
        uiPrefs.edit().putString("fin_sections", finSections.joinToString(",")).apply()
    }

    /** Плитка, для которой открыто окно настройки. */
    var tileSettings by mutableStateOf<Tab?>(null)
    /** Окно «Событие текстом». */
    var quickEvent by mutableStateOf(false)
    /** Быстрая запись: null — закрыта, false — текстом, true — сразу голосом. */
    var quickInput by mutableStateOf<Boolean?>(null)
    /** Операция к подтверждению (например, из QR-кода чека). */
    var pendingTx by mutableStateOf<app.calstats.work.Transaction?>(null)

    /** Сканировать QR-код кассового чека → трата на подтверждение. */
    fun scanReceipt(ctx: android.content.Context) {
        val opts = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).build()
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx, opts).startScan()
            .addOnSuccessListener { b ->
                val r = app.calstats.work.Receipt.parse(b.rawValue.orEmpty())
                if (r == null) { message = "Это не QR-код кассового чека"; return@addOnSuccessListener }
                if (work.transactions.any { it.importKey == r.key }) { message = "Этот чек уже записан"; return@addOnSuccessListener }
                if (!isEnabled(Tab.FINANCE)) setModuleEnabled(Tab.FINANCE, true)
                var w = app.calstats.work.FinanceOps.ensureDefaults(work)
                if (w.accounts.none { !it.archived }) w = app.calstats.work.FinanceOps.upsertAccount(w, app.calstats.work.Account(app.calstats.work.newId(), "Карта"))
                if (w !== work) updateWork { w }
                pendingTx = app.calstats.work.Transaction(
                    app.calstats.work.newId(), if (r.refund) app.calstats.work.TxType.INCOME else app.calstats.work.TxType.EXPENSE, r.amount,
                    w.accounts.first { !it.archived }.id, date = r.date.toString(), note = if (r.refund) "Возврат по чеку" else "Чек",
                    source = "QR чека", importKey = r.key,
                )
            }
            .addOnFailureListener { message = "Сканер недоступен: нужны сервисы Google Play" }
    }
    /** Вечерний «Итог дня». */
    var checkIn by mutableStateOf(false)
    /** Утренний «План дня». */
    var morning by mutableStateOf(false)
    var loanCalc by mutableStateOf(false)
    /** Карта, пришедшая из файла .pkpass или «Поделиться», — откроется в модуле «Карты». */
    var pendingCard by mutableStateOf<app.calstats.work.LoyaltyCard?>(null)
    /** Открыть карту на кассе (из поиска, напоминания у магазина). */
    var showCardId by mutableStateOf<String?>(null)
    var openDebt by mutableStateOf<String?>(null)
    var newDebt by mutableStateOf(false)
    /** Окно «Что нового» уже проверено в этом запуске. */
    var whatsNewChecked by mutableStateOf(false)

    // ---------- Финансы: уведомления банков, день зарплаты ----------
    var bankPending by mutableStateOf(BankQueue.list(app))
        private set
    var bankHintHidden by mutableStateOf(uiPrefs.getBoolean("bank_hint_hidden", false))
        private set
    fun hideBankHint() { bankHintHidden = true; uiPrefs.edit().putBoolean("bank_hint_hidden", true).apply() }
    fun refreshBank() { bankPending = BankQueue.list(getApplication()) }
    fun dropBank(ids: Set<String>) { BankQueue.remove(getApplication(), ids); refreshBank() }
    // ---------- Банки: куда записывать операции из уведомлений ----------
    private val bankPrefs = app.getSharedPreferences("bank_map", android.content.Context.MODE_PRIVATE)
    var bankAuto by mutableStateOf(bankPrefs.getBoolean("auto", false))
        private set
    fun changeBankAuto(on: Boolean) { bankAuto = on; bankPrefs.edit().putBoolean("auto", on).apply(); if (on) autoAcceptBank() }
    /** Ключ: «card:1234» или «bank:Сбер» → id счёта. */
    fun bankAccount(key: String): String? = bankPrefs.getString(key, null)
    fun setBankAccount(key: String, accId: String?) { bankPrefs.edit().apply { if (accId == null) remove(key) else putString(key, accId) }.apply(); bankMapTick++ }
    var bankMapTick by mutableStateOf(0)
        private set
    /** Сначала по номеру карты, потом по банку, иначе первый счёт. */
    fun accountFor(t: app.calstats.work.PendingTx, w: app.calstats.work.Workspace): app.calstats.work.Account? {
        val active = w.accounts.filter { !it.archived }
        val id = (if (t.card.isNotBlank()) bankAccount("card:${t.card}") else null) ?: bankAccount("bank:${t.bank}")
        return active.firstOrNull { it.id == id } ?: active.firstOrNull()
    }
    private fun autoAcceptBank() { if (bankAuto && bankPending.isNotEmpty() && work.accounts.any { !it.archived }) acceptBank(bankPending.map { it.id }.toSet(), quiet = true) }

    fun acceptBank(ids: Set<String>, quiet: Boolean = false) {
        val list = bankPending.filter { it.id in ids }
        updateWork { w0 ->
            val w = app.calstats.work.FinanceOps.ensureDefaults(w0)
            if (w.accounts.none { !it.archived }) return@updateWork w0
            val txs = list.groupBy { accountFor(it, w)!!.id }.flatMap { (accId, items) ->
                val rows = items.map { BankImport.Row(java.time.LocalDate.parse(it.date), if (it.income) it.amount else -it.amount, it.merchant.ifBlank { it.bank }, "") }
                BankImport.toTransactions(rows, accId, w.finCategories, "Уведомление банка")
            }
            w.copy(transactions = w.transactions + txs)
        }
        if (work.accounts.none { !it.archived }) message = "Сначала добавьте счёт в «Финансах»"
        else { dropBank(ids); message = (if (quiet) "Из банков добавлено автоматически: " else "Добавлено операций: ") + list.size }
    }
    var payday by mutableStateOf(uiPrefs.getInt("payday", 0).takeIf { it > 0 })
        private set
    fun changePayday(d: Int?) { payday = d; uiPrefs.edit().putInt("payday", d ?: 0).apply() }

    // ---------- Даты из модулей → календарь ----------
    private var exportJob: kotlinx.coroutines.Job? = null
    fun exportDates(w: app.calstats.work.Workspace = work, now: Boolean = false) {
        exportJob?.cancel()
        exportJob = viewModelScope.launch(Dispatchers.IO) {
            if (!now) kotlinx.coroutines.delay(2500)
            val n = runCatching { CalendarExport.sync(getApplication(), w, today) }.getOrDefault(0)
            if (now) withContext(Dispatchers.Main) { message = if (n == 0) "Календарь уже в актуальном состоянии" else "Обновлено в календаре: $n" }
        }
    }

    // ---------- Фокус (помидор) ----------
    data class Focus(val issueId: String?, val title: String, val minutes: Int, val endAt: Long, val pausedLeft: Long? = null, val breakMode: Boolean = false)
    var focus by mutableStateOf<Focus?>(null)

    fun startFocus(issueId: String?, title: String, minutes: Int = 25) {
        val f = Focus(issueId, title, minutes, System.currentTimeMillis() + minutes * 60_000L)
        focus = f
        Notify.focusAlarm(getApplication(), f.endAt, title)
    }

    fun pauseFocus() {
        val f = focus ?: return
        if (f.pausedLeft != null) {
            val end = System.currentTimeMillis() + f.pausedLeft
            focus = f.copy(endAt = end, pausedLeft = null)
            Notify.focusAlarm(getApplication(), end, f.title)
        } else {
            focus = f.copy(pausedLeft = (f.endAt - System.currentTimeMillis()).coerceAtLeast(0))
            Notify.focusAlarm(getApplication(), null, f.title)
        }
    }

    /** Завершить: записать потраченное время в задачу (кроме перерыва). */
    fun stopFocus(completed: Boolean) {
        val f = focus ?: return
        focus = null
        Notify.focusAlarm(getApplication(), null, f.title)
        if (f.breakMode) return
        val left = f.pausedLeft ?: (f.endAt - System.currentTimeMillis()).coerceAtLeast(0)
        val spent = if (completed) f.minutes else ((f.minutes * 60_000L - left) / 60_000).toInt()
        if (spent > 0 && f.issueId != null) {
            updateWork { WorkOps.addWorklog(it, f.issueId, WorkLog(app.calstats.work.newId(), ME, today.toString(), spent, "Фокус")) }
            message = "Записано ${spent} мин в задачу"
        }
        if (completed) focus = Focus(null, "Перерыв", 5, System.currentTimeMillis() + 5 * 60_000L, breakMode = true)
    }

    fun postponeTasks() {
        var n = 0
        updateWork { val (w, c) = WorkOps.postpone(it, today); n = c; w }
        message = if (n == 0) "Нечего переносить" else "Перенесено на завтра: $n"
    }

    // ---------- Дни рождения из контактов ----------
    var birthdays by mutableStateOf<List<Birthday>>(emptyList())
        private set
    fun loadBirthdays() {
        viewModelScope.launch { birthdays = withContext(Dispatchers.IO) { Birthdays.load(getApplication()) } }
    }
    fun birthdaysOn(d: LocalDate): List<String> =
        birthdays.filter { it.day == java.time.MonthDay.from(d) }.map { Birthdays.text(it, d) }

    // ---------- Профиль и обновления ----------
    var avatarVersion by mutableStateOf(0)
    var autoUpdate by mutableStateOf(uiPrefs.getBoolean("auto_update", true))
        private set
    fun changeAutoUpdate(on: Boolean) {
        autoUpdate = on
        uiPrefs.edit().putBoolean("auto_update", on).apply()
    }
    var update by mutableStateOf<Updater.Release?>(null)
        private set
    var updateDismissed by mutableStateOf(false)
    var updateProgress by mutableStateOf<Int?>(null)
        private set
    var updateChecking by mutableStateOf(false)
        private set
    private var lastUpdateCheck = 0L

    /** Проверить новую версию (автоматически — не чаще раза в 6 часов). */
    fun checkUpdate(manual: Boolean = false) {
        if (!manual && (!autoUpdate || System.currentTimeMillis() - lastUpdateCheck < 6 * 3600_000L)) return
        lastUpdateCheck = System.currentTimeMillis()
        updateChecking = true
        viewModelScope.launch {
            val r = runCatching { Updater.check(getApplication()) }
            updateChecking = false
            r.onSuccess {
                update = it
                if (it != null) updateDismissed = false
                if (manual) message = if (it == null) "У вас последняя версия" else "Есть новая версия ${it.version}"
            }.onFailure { if (manual) message = "Не удалось проверить обновления" }
        }
    }

    fun installUpdate(ctx: android.content.Context) {
        val r = update ?: return
        if (updateProgress != null) return
        updateProgress = 0
        viewModelScope.launch {
            runCatching { Updater.download(getApplication(), r) { updateProgress = it } }
                .onSuccess { f -> updateProgress = null; if (!Updater.install(ctx, f)) message = "Разрешите установку и нажмите «Установить» ещё раз" }
                .onFailure { updateProgress = null; message = "Не удалось скачать обновление" }
        }
    }

    // ---------- Telegram ----------
    val telegram = TelegramBot(app)
    var telegramState by mutableStateOf(telegram.state())
        private set
    fun refreshTelegram() { telegramState = telegram.state() }

    /** Забрать сообщения, присланные боту: «задача …», «заметка …», «трата 350 кофе», просто текст — в заметки. */
    /** Данные для бота, пока приложение открыто. */
    val botAccess = object : BotBrain.Access {
        override suspend fun get() = withContext(Dispatchers.Main) { work }
        override suspend fun update(f: (Workspace) -> Workspace) = withContext(Dispatchers.Main) { updateWork(f) }
    }

    /** Без «бота на связи» сообщения забираются при открытии приложения — и бот сразу отвечает. */
    fun pullTelegram() {
        TelegramService.access = botAccess
        if (TelegramService.enabled(getApplication())) { TelegramService.start(getApplication()); return }
        if (!telegramState.connected || !telegramState.inbox) return
        viewModelScope.launch {
            val msgs = runCatching { telegram.pull() }.getOrDefault(emptyList())
            if (msgs.isEmpty()) return@launch
            msgs.forEach { m ->
                val reply = runCatching { BotBrain.answer(getApplication(), m, botAccess) }.getOrElse { "Ошибка: ${it.message}" }
                telegram.send(reply, keyboard = true)
            }
            message = "Из Telegram обработано сообщений: ${msgs.size}"
        }
    }

    private fun saveModules() {
        uiPrefs.edit()
            .putString("modules_order", moduleOrder.joinToString(",") { it.name })
            .putString("modules_disabled", disabledModules.joinToString(",") { it.name })
            .apply()
    }

    fun setModuleEnabled(t: Tab, on: Boolean) {
        disabledModules = if (on) disabledModules - t else disabledModules + t
        if (!on && tabState == t) tabState = Tab.HOME
        saveModules()
    }

    fun setModulePinned(t: Tab, pinned: Boolean) {
        unpinnedModules = if (pinned) unpinnedModules - t else unpinnedModules + t
        saveModules()
    }

    fun moveModule(t: Tab, delta: Int) {
        val list = moduleOrder.toMutableList()
        val i = list.indexOf(t)
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return
        list.removeAt(i)
        list.add(j, t)
        moduleOrder = list
        saveModules()
    }

    /** Сдвиг среди модулей меню (пропуская выключенные и служебные), чтобы каждое нажатие было заметно. */
    fun moveInMenu(t: Tab, delta: Int, visible: List<Tab>) {
        val i = visible.indexOf(t)
        val target = visible.getOrNull(i + delta) ?: return
        val list = moduleOrder.toMutableList()
        val a = list.indexOf(t); val b = list.indexOf(target)
        if (a < 0 || b < 0) return
        list[a] = target; list[b] = t
        moduleOrder = list
        saveModules()
    }

    private var tabState by mutableStateOf(Tab.HOME)
    /** Текущий модуль. */
    var tab: Tab
        get() = tabState
        set(v) {
            val before = snap()
            tabState = v
            record(before)
        }

    private var calViewState by mutableStateOf(
        runCatching { CalView.valueOf(uiPrefs.getString("cal_view", null) ?: "LIST") }.getOrDefault(CalView.LIST),
    )
    var calView: CalView
        get() = calViewState
        set(v) {
            val before = snap()
            calViewState = v
            uiPrefs.edit().putString("cal_view", v.name).apply()
            record(before)
        }

    /** «Назад» на главном экране: true — вернулись к предыдущему виду, false — истории нет. */
    fun navigateBack(): Boolean {
        val last = navHistory.removeLastOrNull()
        if (last == null) {
            // Истории нет (открыли из виджета/уведомления) — сначала на Главную, потом уже выход.
            if (tabState == Tab.HOME) return false
            restoring = true; tabState = Tab.HOME; restoring = false
            return true
        }
        restoring = true
        tabState = last.tab
        calViewState = last.calView
        taskViewState = last.taskView
        projectState = last.projectId
        subViews.clear(); subViews.putAll(last.sub)
        restoring = false
        return true
    }

    /** Сворачивание месяца в модуле «Календарь». */
    var monthCollapsed by mutableStateOf(uiPrefs.getBoolean("month_collapsed", false))
        private set
    fun changeMonthCollapsed(v: Boolean) {
        if (monthCollapsed == v) return
        monthCollapsed = v
        uiPrefs.edit().putBoolean("month_collapsed", v).apply()
    }
    var selectedDate by mutableStateOf(LocalDate.now())
    var work by mutableStateOf(workStore.load())
    @Volatile private var workStamp = workStore.stamp()
        private set
    /** Выбранный проект во вкладке «Задачи» (null — все проекты). */
    private var projectState by mutableStateOf<String?>(uiPrefs.getString("project", null))
    val currentProjectId: String? get() = projectState
    fun selectProject(id: String?) {
        val before = snap()
        projectState = id
        uiPrefs.edit().putString("project", id).apply()
        record(before)
    }
    private var taskViewState by mutableStateOf(uiPrefs.getString("task_view", "BOARD") ?: "BOARD")
    val taskView: String get() = taskViewState
    fun selectTaskView(v: String) {
        val before = snap()
        taskViewState = v
        uiPrefs.edit().putString("task_view", v).apply()
        record(before)
    }
    var onlyMine by mutableStateOf(false)
    var drinks by mutableStateOf(sobrietyStore.load())
        private set
    var reminderEnabled by mutableStateOf(sobrietyStore.reminderEnabled)
        private set
    var reminderTime by mutableStateOf(sobrietyStore.reminderTime)
        private set

    private var observer: ContentObserver? = null
    private var reloadJob: Job? = null
    private var lastLoadFinished = 0L
    private var lastSyncRequest = 0L

    // ---------- навигация ----------

    fun push(s: Screen) {
        stack.add(s)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    // ---------- загрузка ----------

    private fun checkPermission(): Boolean {
        val ctx = getApplication<Application>()
        return listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ---------- Пароль на вход ----------
    var appLocked by mutableStateOf(AppLock.enabled(getApplication()))
    var appLockOn by mutableStateOf(AppLock.enabled(getApplication()))

    // ---------- Документы: защита отпечатком / PIN ----------
    var docsLock by mutableStateOf(uiPrefs.getBoolean("docs_lock", false))
        private set
    /** Модуль разблокирован до ухода приложения в фон. */
    var docsUnlocked by mutableStateOf(false)
    /** На время выбора фото из галереи не закрываем модуль. */
    var keepDocsUnlocked = false

    fun changeDocsLock(on: Boolean) {
        docsLock = on
        docsUnlocked = true
        uiPrefs.edit().putBoolean("docs_lock", on).apply()
    }

    // ---------- Здоровье (Health Connect) ----------
    var healthStatus by mutableStateOf(HealthLink.Status.UNAVAILABLE)
        private set
    var healthGranted by mutableStateOf(0)
        private set
    var health by mutableStateOf<HealthSnapshot?>(null)
        private set
    var healthLoading by mutableStateOf(false)
        private set
    private var healthLoadedAt = 0L

    fun refreshHealth(force: Boolean = false) {
        if (!isEnabled(Tab.HEALTH)) return
        if (!force && System.currentTimeMillis() - healthLoadedAt < 60_000) return
        healthLoadedAt = System.currentTimeMillis()
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            healthStatus = runCatching { HealthLink.status(ctx) }.getOrDefault(HealthLink.Status.UNAVAILABLE)
            if (healthStatus != HealthLink.Status.AVAILABLE) return@launch
            val g = HealthLink.granted(ctx).count { it in HealthLink.permissions }
            healthGranted = g
            if (g == 0) return@launch
            healthLoading = true
            health = runCatching { withContext(Dispatchers.IO) { HealthLink.load(ctx, 60) } }.getOrNull() ?: health
            health?.let { s ->
                val auto = app.calstats.work.HabitOps.applyAuto(work, s)
                if (auto != work) updateWork { app.calstats.work.HabitOps.applyAuto(it, s) }
            }
            healthLoading = false
        }
    }

    fun onResume() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { Notify.scheduleEvents(getApplication()) } }
        loadBirthdays()
        refreshBank()
        autoAcceptBank()
        exportDates()
        Usage.open(getApplication())
        refreshHealth()
        checkUpdate()
        pullTelegram()
        drinks = sobrietyStore.load()
        // Готовая запись совещания добавляется в заметку через модель, пока приложение открыто
        RecorderService.onSaved = { note, f ->
            updateWork { w -> w.copy(notes = w.notes.map { if (it.id == note) it.copy(files = it.files + f, updated = System.currentTimeMillis()) else it }) }
            message = "🎙 Запись сохранена в заметку"
        }
        // Перечитываем данные, только если их поменяли извне (виджет, уведомление), и не в главном потоке.
        viewModelScope.launch(Dispatchers.IO) {
            if (workStore.stamp() != workStamp && !saveLock.isLocked) {
                val w = workStore.load(); val st = workStore.stamp()
                withContext(Dispatchers.Main) { work = w; workStamp = st }
            }
        }
        if (Backup.autoDue(getApplication())) backupToFolder()
        DrinkReminder.schedule(getApplication())
        DocsReminder.schedule(getApplication())
        viewModelScope.launch(Dispatchers.IO) { runCatching { Notify.scheduleAll(getApplication()) } }
        hasPermission = checkPermission()
        now = System.currentTimeMillis()
        today = LocalDate.now()
        if (hasPermission) {
            registerObserver()
            reload()
            // Подтягиваем изменения из Google (в т.ч. удаления) при каждом открытии, не чаще раза в минуту.
            if (System.currentTimeMillis() - lastSyncRequest > 60_000) {
                lastSyncRequest = System.currentTimeMillis()
                viewModelScope.launch {
                    delay(1500)
                    repo.requestSync(calendars)
                }
            }
        }
    }

    private fun registerObserver() {
        if (observer != null) return
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // Изменения пришли из Google (синхронизация) или из другого приложения.
                // Не пропускаем ни одного изменения: перезагрузка с задержкой, повторные вызовы сливаются в один.
                reload(800)
            }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, o)
        observer = o
    }

    override fun onCleared() {
        observer?.let { resolver.unregisterContentObserver(it) }
        RecorderService.onSaved = null
        if (TelegramService.access === botAccess) TelegramService.access = null
        // Не теряем последнее изменение, если система закрывает экран.
        runCatching { kotlinx.coroutines.runBlocking { saveLock.lock(); try { workStore.save(work) } finally { saveLock.unlock() } } }
        super.onCleared()
    }

    fun reload(delayMs: Long = 0) {
        if (!hasPermission) return
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            loading = true
            try {
                val z = zone
                val from = loadStart.atStartOfDay(z).toInstant().toEpochMilli()
                val to = loadEnd.atStartOfDay(z).toInstant().toEpochMilli()
                val result = withContext(Dispatchers.IO) { repo.calendars() to repo.instances(from, to) }
                calendars = result.first
                instances = result.second
                now = System.currentTimeMillis()
                today = LocalDate.now()
                loadedOnce = true
                CalendarWidget.refresh(getApplication())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Не удалось прочитать календарь: ${e.message}"
            } finally {
                loading = false
                lastLoadFinished = System.currentTimeMillis()
            }
        }
    }

    fun syncNow() {
        repo.requestSync(calendars)
        message = "Запрошена синхронизация с Google"
        reload()
        reload(5000)
    }

    // ---------- статистика ----------

    fun includedCalendarIds(): Set<Long> =
        calendars.filter { it.key !in data.excludedCalendars }.map { it.id }.toSet()

    fun groups(): List<DirectionGroup> = Stats.buildGroups(instances, data, includedCalendarIds())

    fun periodWindow(p: Period = period): Window =
        Stats.window(p.start(loadStart), p.endExclusive(loadEnd), now, data.includeFuture, zone)

    fun calendarById(id: Long): CalendarInfo? = calendars.firstOrNull { it.id == id }

    fun directionName(color: Int): String? =
        data.custom.firstOrNull { it.eventColor == color }?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: data.directions[color]?.name?.trim()?.takeIf { it.isNotEmpty() }

    /** Цвет для показа: свой цвет направления, если задан. */
    fun directionDisplay(color: Int): Int =
        data.custom.firstOrNull { it.eventColor == color }?.display ?: data.directions[color]?.display ?: color

    fun upsertCustomDirection(c: CustomDirection) =
        update(data.copy(custom = if (data.custom.any { it.id == c.id }) data.custom.map { if (it.id == c.id) c else it } else data.custom + c))

    fun deleteCustomDirection(id: String) = update(data.copy(custom = data.custom.filter { it.id != id }))

    fun setDirectionDisplay(color: Int, display: Int?) {
        val cur = data.directions[color] ?: DirectionSetting(color, "")
        update(data.copy(directions = data.directions + (color to cur.copy(display = display))))
    }

    // ---------- направления и настройки ----------

    private fun update(d: AppData) {
        data = d
        store.save(d)
    }

    fun setDirectionName(color: Int, name: String) {
        val cur = data.directions[color] ?: DirectionSetting(color, "")
        update(data.copy(directions = data.directions + (color to cur.copy(name = name))))
    }

    fun setDirectionHidden(color: Int, hidden: Boolean) {
        val cur = data.directions[color] ?: DirectionSetting(color, "")
        update(data.copy(directions = data.directions + (color to cur.copy(hidden = hidden))))
    }

    fun setCalendarIncluded(cal: CalendarInfo, included: Boolean) {
        val set = if (included) data.excludedCalendars - cal.key else data.excludedCalendars + cal.key
        update(data.copy(excludedCalendars = set))
    }

    fun setIncludeFuture(v: Boolean) = update(data.copy(includeFuture = v))

    // ---------- события ----------

    suspend fun loadEvent(id: Long): EventDetails? = withContext(Dispatchers.IO) { repo.event(id) }

    suspend fun eventColors(cal: CalendarInfo): List<EventColorOption> =
        withContext(Dispatchers.IO) { repo.eventColors(cal.accountName, cal.accountType) }

    fun defaultCalendar(): CalendarInfo? {
        val writable = calendars.filter { it.writable && it.visible }.ifEmpty { calendars.filter { it.writable } }
        return writable.firstOrNull { it.key == data.lastCalendarKey }
            ?: writable.firstOrNull { it.primary && it.accountType == "com.google" }
            ?: writable.firstOrNull { it.accountType == "com.google" }
            ?: writable.firstOrNull()
    }

    fun saveEvent(
        existingId: Long?, calendar: CalendarInfo, title: String, description: String,
        begin: Long, end: Long, allDay: Boolean, colorKey: String?,
        rrule: String?, reminders: List<Int>,
    ) {
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    if (existingId == null) {
                        repo.insert(calendar.id, title, description, begin, end, allDay, colorKey, rrule, reminders) != null
                    } else {
                        repo.update(existingId, title, description, begin, end, allDay, colorKey, rrule, reminders)
                    }
                }
                if (ok) {
                    update(data.copy(lastCalendarKey = calendar.key))
                    repo.requestSync(listOf(calendar))
                    message = if (existingId == null) "Событие добавлено" else "Событие сохранено"
                    pop()
                    reload()
                } else {
                    message = "Не удалось сохранить событие"
                }
            } catch (e: Exception) {
                message = "Ошибка: ${e.message}"
            }
        }
    }

    /** Перетаскивание события в сетке дня/недели: сдвиг на deltaMin минут (только неповторяющиеся). */
    fun moveEvent(e: EventInstance, deltaMin: Int) {
        if (deltaMin == 0) return
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { repo.event(e.eventId) }
            if (d == null) { message = "Событие не найдено"; return@launch }
            if (d.recurring) { message = "Повторяющееся событие перенесите в редакторе"; return@launch }
            val shift = deltaMin * 60_000L
            val ok = withContext(Dispatchers.IO) {
                repo.update(d.id, d.title, d.description, d.dtStart + shift, (d.dtEnd ?: (d.dtStart + 3600_000L)) + shift, d.allDay, d.colorKey, null, d.reminders)
            }
            if (ok) {
                val t = java.time.Instant.ofEpochMilli(d.dtStart + shift).atZone(zone)
                message = "«${d.title}» → ${Fmt.dayTitle(t.toLocalDate(), today).lowercase()}, ${Fmt.time(t.toLocalTime())}"
                calendars.firstOrNull { it.id == d.calendarId }?.let { repo.requestSync(listOf(it)) }
                reload()
            } else message = "Не удалось перенести (календарь только для чтения?)"
        }
    }

    /** instanceBegin != null — удалить только это повторение серии. */
    fun deleteEvent(id: Long, calendar: CalendarInfo?, instanceBegin: Long? = null) {
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    if (instanceBegin != null) repo.deleteInstance(id, instanceBegin) else repo.delete(id)
                }
                message = if (ok) "Событие удалено" else "Не удалось удалить событие"
                if (ok) {
                    calendar?.let { repo.requestSync(listOf(it)) }
                    pop()
                    reload()
                }
            } catch (e: Exception) {
                message = "Ошибка: ${e.message}"
            }
        }
    }

    // ---------- календарь ----------

    /** Видимые календари — для вкладки «Календарь» (статистика может считать не все). */
    fun visibleCalendarIds(): Set<Long> = calendars.filter { it.visible }.map { it.id }.toSet()

    /** События по дням (многодневные — в каждом дне). */
    fun eventsByDay(): Map<LocalDate, List<EventInstance>> {
        val ids = visibleCalendarIds()
        val z = zone
        val map = HashMap<LocalDate, MutableList<EventInstance>>()
        for (i in instances) {
            if (i.calendarId !in ids) continue
            var d = Stats.firstDay(i, z)
            val last = Stats.lastDay(i, z)
            var guard = 0
            while (!d.isAfter(last) && guard < 62) {
                map.getOrPut(d) { mutableListOf() } += i
                d = d.plusDays(1)
                guard++
            }
        }
        return map
    }

    // ---------- задачи, проекты, люди, расходы ----------

    /** Любое изменение рабочего пространства: сохраняем в файл и обновляем виджеты. */
    fun undoDelete(e: TrashEntry) {
        updateWork { Trash.restore(it, e) }
        undo = null
        viewModelScope.launch(Dispatchers.IO) { Trash.remove(getApplication(), e.id) }
    }

    private val saveLock = kotlinx.coroutines.sync.Mutex()
    private var sideJob: kotlinx.coroutines.Job? = null

    /** Последнее удаление — для кнопки «Отменить». */
    var undo by mutableStateOf<TrashEntry?>(null)

    fun updateWork(transform: (Workspace) -> Workspace) {
        val old = work
        val w = transform(old)
        work = w
        if (Trash.count(w) < Trash.count(old)) {
            Trash.diff(old, w)?.takeIf { it.count <= 300 }?.let { e ->
                undo = e
                viewModelScope.launch(Dispatchers.IO) { Trash.add(getApplication(), e) }
            }
        }
        // Запись по очереди и только самой свежей версии: быстрые нажатия (вода, привычки)
        // больше не пишут файл параллельно и не могут затереть новое старым.
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.lock()
            try { if (work === w) runCatching { workStore.save(w); workStamp = workStore.stamp() } } finally { saveLock.unlock() }
        }
        // Виджеты, напоминания и даты в календаре — один раз после серии изменений.
        sideJob?.cancel()
        sideJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(700)
            val cur = work
            runCatching { TasksWidget.refresh(getApplication()) }
            runCatching { app.calstats.widget.TodayWidget.refresh(getApplication()) }
            runCatching { app.calstats.widget.CardsWidget.refresh(getApplication()) }
            runCatching { Notify.scheduleHabits(getApplication(), cur) }
            runCatching { Notify.scheduleMeds(getApplication(), cur) }
            runCatching { Notify.scheduleNotes(getApplication(), cur) }
            exportDates(cur)
        }
    }



    /** Пришло из «Поделиться» — ждёт выбора, куда сохранить. */
    var incoming by mutableStateOf<SharedText?>(null)

    fun saveIncomingAsShopping() {
        val sh = incoming ?: return
        incoming = null
        updateWork { w0 ->
            var w = w0
            val list = w.shopLists.firstOrNull { !it.archived } ?: app.calstats.work.ShopList(newId(), "Продукты").also { w = w.copy(shopLists = w.shopLists + it) }
            val items = app.calstats.work.Shop.parse(sh.text).map { (n, q) -> app.calstats.work.ShopItem(newId(), list.id, n, q) }
            w.copy(shopItems = w.shopItems + items)
        }
        tab = Tab.SHOPPING
    }

    fun saveIncomingAsNote(navigate: Boolean = true) {
        val sh = incoming ?: return
        incoming = null
        val note = NoteImport.fromSharedText(sh.text, sh.subject, sh.source)
        updateWork { WorkOps.upsertNote(it, note) }
        if (!navigate) return
        tab = Tab.NOTES
        push(Screen.NoteEdit(note.id))
    }

    fun saveIncomingAsWish(listId: String) {
        val sh = incoming ?: return
        incoming = null
        val link = Regex("""https?://\S+""").find(sh.text)?.value.orEmpty()
        val rest = sh.text.replace(link, "").trim().lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val title = (sh.subject?.trim()?.takeIf { it.isNotEmpty() } ?: rest).ifEmpty { "Желание" }.take(120)
        updateWork { WorkOps.upsertWish(it, Wish(newId(), listId, title, link = link)) }
        message = "Добавлено в список желаний"
        tab = Tab.WISHES
        push(Screen.WishListDetail(listId))
    }

    fun saveIncomingAsTask(navigate: Boolean = true) {
        val sh = incoming ?: return
        incoming = null
        var w = work
        if (w.projects.isEmpty()) {
            w = WorkOps.upsertProject(w, Project(newId(), "ЛИЧ", "Личное", PALETTE[0]))
        }
        val project = w.project(currentProjectId) ?: w.projects.first { !it.archived }
        val lines = sh.text.trim().lines()
        val title = (sh.subject?.trim()?.takeIf { it.isNotEmpty() } ?: lines.first()).take(120)
        val (w2, issue) = WorkOps.createIssue(
            w,
            Issue(newId(), project.id, 0, title, description = sh.text.trim(), assigneeId = ME),
        )
        work = w2
        updateWork { w2 }
        if (!navigate) return
        tab = Tab.TASKS
        push(Screen.IssueDetail(issue.id))
    }

    fun reloadWork() {
        work = workStore.load()
    }

    /** Мои незакрытые задачи со сроком — для календаря. */
    fun myIssuesByDue(): Map<LocalDate, List<Issue>> =
        work.issues.filter { it.assigneeId == ME && it.dueDate != null }.groupBy { it.dueDate!! }

    fun toggleIssueDone(i: Issue) = updateWork {
        WorkOps.setStatus(
            it, i.id,
            if (i.status == Status.DONE) Status.TODO else Status.DONE,
        )
    }

    // ---------- учёт алкоголя ----------

    /** null — снять отметку. */
    fun setDrink(date: LocalDate, drank: Boolean?) {
        drinks = sobrietyStore.set(date, drank)
        CalendarWidget.refresh(getApplication())
        if (date == today.minusDays(1)) DrinkReminder.cancel(getApplication())
    }

    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(uiPrefs.getString("theme", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
    )
        private set
    fun changeTheme(t: ThemeMode) {
        themeMode = t
        uiPrefs.edit().putString("theme", t.name).apply()
    }

    /** Показывать «Без алкоголя» среди привычек. */
    var alcoholHabitShown by mutableStateOf(uiPrefs.getBoolean("alcohol_habit", true))
        private set
    fun showAlcoholHabit(v: Boolean) {
        alcoholHabitShown = v
        uiPrefs.edit().putBoolean("alcohol_habit", v).apply()
    }

    fun changeReminderEnabled(v: Boolean) {
        sobrietyStore.reminderEnabled = v
        reminderEnabled = v
        DrinkReminder.schedule(getApplication())
    }

    fun changeReminderTime(t: java.time.LocalTime) {
        sobrietyStore.reminderTime = t
        reminderTime = t
        DrinkReminder.schedule(getApplication())
    }

    var notificationAsked: Boolean
        get() = sobrietyStore.notificationAsked
        set(v) { sobrietyStore.notificationAsked = v }

    fun testReminder() {
        val app = getApplication<Application>()
        if (!DrinkReminder.canNotify(app)) {
            message = "Уведомления выключены — разрешите их в настройках Android"
            return
        }
        DrinkReminder.showQuestion(app, today.minusDays(1), force = true)
    }

    // ---------- резервная копия ----------

    fun backupFileName(): String = Backup.fileName()
    fun csvFileName(): String = "napravleniya-sobytiya-${LocalDate.now()}.csv"

    private fun backupJson(): JSONObject {
        val json = DataStore.toJson(data)
        json.put("work", JSONObject(WorkStore.json.encodeToString(Workspace.serializer(), work)))
        json.put("sobriety", sobrietyStore.backupJson())
        json.put("prefs", Backup.prefsJson(getApplication()))
        json.put("made", java.time.LocalDateTime.now().toString())
        return json
    }

    var backupFolder by mutableStateOf(Backup.folderName(getApplication()))
        private set
    var backupLast by mutableStateOf(Backup.lastAuto(getApplication()))
        private set
    var backupError by mutableStateOf(Backup.lastError(getApplication()))
        private set

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = backupJson()
                val n = withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { Backup.writeZip(getApplication(), json, it) }
                        ?: error("файл недоступен")
                }
                message = "Копия сохранена: все модули и $n файлов (фото документов, карт, аватар)"
            } catch (e: Exception) {
                message = "Не удалось сохранить копию: ${e.message}"
            }
        }
    }

    fun setBackupFolder(tree: Uri?) {
        try {
            Backup.setFolder(getApplication(), tree)
            backupFolder = Backup.folderName(getApplication())
            if (tree != null) backupToFolder(silent = false)
        } catch (e: Exception) {
            message = "Эта папка не даёт постоянного доступа: ${e.message}. Выберите папку Яндекс Диска или память телефона"
        }
    }

    fun backupToFolder(silent: Boolean = true) {
        viewModelScope.launch {
            try {
                val json = backupJson()
                val name = withContext(Dispatchers.IO) { Backup.writeToFolder(getApplication(), json) }
                if (!silent) message = "Копия $name сохранена в «$backupFolder»"
            } catch (e: Exception) {
                if (!silent) message = "Не удалось сохранить в папку: ${e.message}"
            }
            backupLast = Backup.lastAuto(getApplication())
            backupError = Backup.lastError(getApplication())
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { Backup.read(getApplication(), it) }
                        ?: error("файл недоступен")
                }
                if (json.optString("app") != DataStore.APP_MARK) {
                    message = "Это не файл резервной копии этого приложения"
                    return@launch
                }
                val restored = DataStore.fromJson(json)
                update(restored)
                val restoredWork = json.optJSONObject("work")?.let {
                    WorkStore.json.decodeFromString(Workspace.serializer(), it.toString())
                }
                if (restoredWork != null) updateWork { restoredWork }
                json.optJSONObject("sobriety")?.let {
                    sobrietyStore.restore(it)
                    drinks = sobrietyStore.load()
                    reminderEnabled = sobrietyStore.reminderEnabled
                    reminderTime = sobrietyStore.reminderTime
                    DrinkReminder.schedule(getApplication())
                }
                val prefs = json.optJSONObject("prefs")
                prefs?.let { Backup.restorePrefs(getApplication(), it) }
                message = "Восстановлено: направлений ${restored.directions.values.count { it.name.isNotBlank() }}" +
                    (restoredWork?.let { ", задач ${it.issues.size}, расходов ${it.expenses.size}, карт ${it.cards.size}" } ?: "") +
                    (if (prefs != null) ". Перезапустите приложение, чтобы применились настройки" else "")
            } catch (e: Exception) {
                message = "Не удалось восстановить: ${e.message}"
            }
        }
    }

    /** Все события (с направлениями) в таблицу CSV — открывается в Excel и Google Таблицах. */
    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val z = zone
                val included = includedCalendarIds()
                val cals = calendars.associateBy { it.id }
                val rows = instances.filter { it.calendarId in included }
                val sb = StringBuilder("﻿")
                sb.append("Дата;Начало;Окончание;Весь день;Часы;Направление;Название;Календарь\n")
                for (i in rows) {
                    val start = java.time.Instant.ofEpochMilli(i.begin).atZone(if (i.allDay) java.time.ZoneOffset.UTC else z)
                    val end = java.time.Instant.ofEpochMilli(i.end).atZone(if (i.allDay) java.time.ZoneOffset.UTC else z)
                    val hours = if (i.allDay) "" else Fmt.hours((i.end - i.begin) / 60_000L)
                    val cells = listOf(
                        start.toLocalDate().toString(),
                        if (i.allDay) "" else Fmt.time(start.toLocalTime()),
                        if (i.allDay) "" else Fmt.time(end.toLocalTime()),
                        if (i.allDay) "да" else "",
                        hours,
                        directionName(i.color) ?: DataStore.colorToHex(i.color),
                        i.title,
                        cals[i.calendarId]?.name ?: "",
                    )
                    sb.append(cells.joinToString(";") { "\"" + it.replace("\"", "\"\"") + "\"" }).append('\n')
                }
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
                        ?: error("файл недоступен")
                }
                message = "Выгружено событий: ${rows.size}"
            } catch (e: Exception) {
                message = "Не удалось выгрузить: ${e.message}"
            }
        }
    }
}
