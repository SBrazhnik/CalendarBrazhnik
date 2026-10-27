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
import app.calstats.work.Issue
import app.calstats.work.ME
import app.calstats.work.Status
import app.calstats.work.WorkOps
import app.calstats.work.WorkStore
import app.calstats.work.Workspace
import java.time.LocalDate
import java.time.ZoneId

enum class CalView(val title: String, val days: Int) {
    SCHEDULE("Расписание", 0), DAY("День", 1), THREE("3 дня", 3), WEEK("Неделя", 7), MONTH("Месяц", 0),
}

enum class Tab(val title: String) { CALENDAR("Календарь"), TASKS("Задачи"), MONEY("Расходы"), ANALYSIS("Анализ") }

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
    data class IssueDetail(val id: String) : Screen
    data object People : Screen
    data class ProjectEdit(val id: String?) : Screen
    data class Book(val id: String) : Screen
    data class ExpenseEdit(val bookId: String, val expenseId: String?) : Screen
    data object Settings : Screen
}

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

    var tab by mutableStateOf(Tab.CALENDAR)
    private val uiPrefs = app.getSharedPreferences("calstats_ui", android.content.Context.MODE_PRIVATE)
    private var calViewState by mutableStateOf(
        runCatching { CalView.valueOf(uiPrefs.getString("cal_view", null) ?: "MONTH") }.getOrDefault(CalView.MONTH),
    )
    var calView: CalView
        get() = calViewState
        set(v) {
            calViewState = v
            uiPrefs.edit().putString("cal_view", v.name).apply()
        }
    var selectedDate by mutableStateOf(LocalDate.now())
    var work by mutableStateOf(workStore.load())
        private set
    /** Выбранный проект во вкладке «Задачи» (null — все проекты). */
    var currentProjectId by mutableStateOf<String?>(uiPrefs.getString("project", null))
        private set
    fun selectProject(id: String?) {
        currentProjectId = id
        uiPrefs.edit().putString("project", id).apply()
    }
    var taskView by mutableStateOf(uiPrefs.getString("task_view", "BOARD") ?: "BOARD")
        private set
    fun selectTaskView(v: String) {
        taskView = v
        uiPrefs.edit().putString("task_view", v).apply()
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

    fun onResume() {
        drinks = sobrietyStore.load()
        work = workStore.load()
        DrinkReminder.schedule(getApplication())
        hasPermission = checkPermission()
        now = System.currentTimeMillis()
        today = LocalDate.now()
        if (hasPermission) {
            registerObserver()
            reload()
        }
    }

    private fun registerObserver() {
        if (observer != null) return
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // Изменения пришли из Google (синхронизация) или из другого приложения.
                if (System.currentTimeMillis() - lastLoadFinished > 1500) reload(1000)
            }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, o)
        observer = o
    }

    override fun onCleared() {
        observer?.let { resolver.unregisterContentObserver(it) }
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
        data.directions[color]?.name?.trim()?.takeIf { it.isNotEmpty() }

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
    fun updateWork(transform: (Workspace) -> Workspace) {
        val w = transform(work)
        work = w
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { workStore.save(w) }
            TasksWidget.refresh(getApplication())
        }
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

    fun backupFileName(): String = "napravleniya-backup-${LocalDate.now()}.json"
    fun csvFileName(): String = "napravleniya-sobytiya-${LocalDate.now()}.csv"

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = DataStore.toJson(data)
                json.put("work", JSONObject(WorkStore.json.encodeToString(Workspace.serializer(), work)))
                json.put("sobriety", sobrietyStore.backupJson())
                val text = json.toString(2)
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("файл недоступен")
                }
                message = "Резервная копия сохранена"
            } catch (e: Exception) {
                message = "Не удалось сохранить копию: ${e.message}"
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("файл недоступен")
                }
                val json = JSONObject(text)
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
                message = "Восстановлено: направлений ${restored.directions.values.count { it.name.isNotBlank() }}" +
                    (restoredWork?.let { ", задач ${it.issues.size}, расходов ${it.expenses.size}" } ?: "")
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
