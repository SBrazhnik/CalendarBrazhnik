package app.calstats.work

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Модуль «Документы»: паспорта, права, полисы, гарантии — номера, фото, сроки действия и напоминания.
 * Подписки — общие с модулем «Финансы» (Workspace.recurrings с subscription = true).
 */

enum class DocKind(val title: String, val emoji: String, val expires: Boolean, val remindDays: Int) {
    PASSPORT("Паспорт РФ", "🪪", true, 60),
    INTL_PASSPORT("Загранпаспорт", "✈", true, 180),
    DRIVER("Водительское удостоверение", "🚗", true, 60),
    STS("СТС / ПТС", "📄", false, 0),
    OSAGO("ОСАГО", "🛡", true, 30),
    KASKO("КАСКО", "🛡", true, 30),
    TECH("Диагностическая карта", "🔧", true, 30),
    OMS("Полис ОМС", "🏥", false, 0),
    DMS("ДМС", "🩺", true, 30),
    SNILS("СНИЛС", "📇", false, 0),
    INN("ИНН", "📇", false, 0),
    VISA("Виза", "🌍", true, 30),
    BIRTH("Свидетельство о рождении", "👶", false, 0),
    INSURANCE("Страховка", "☂", true, 30),
    WARRANTY("Гарантия / чек", "🧾", true, 14),
    CONTRACT("Договор", "✍", true, 30),
    OTHER("Другое", "📁", true, 30),
}

@Serializable
data class Document(
    val id: String,
    val kind: DocKind = DocKind.OTHER,
    val title: String = "",
    val owner: String = "",
    val number: String = "",
    val issuer: String = "",
    val issued: String? = null,
    val expires: String? = null,
    val remindDays: Int = 30,
    val note: String = "",
    /** Имена файлов фото во внутренней папке приложения (files/docs). */
    val photos: List<String> = emptyList(),
    val archived: Boolean = false,
    val created: Long = System.currentTimeMillis(),
) {
    val name: String get() = title.ifBlank { kind.title }
    val expiry: LocalDate? get() = expires?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

enum class DocState { OK, SOON, EXPIRED, NONE }

object DocsMath {
    fun daysLeft(d: Document, today: LocalDate): Long? = d.expiry?.let { ChronoUnit.DAYS.between(today, it) }

    fun state(d: Document, today: LocalDate): DocState {
        val left = daysLeft(d, today) ?: return DocState.NONE
        return when {
            left < 0 -> DocState.EXPIRED
            left <= d.remindDays -> DocState.SOON
            else -> DocState.OK
        }
    }

    /** Требуют внимания: истекли или скоро истекут — сначала самые срочные. */
    fun attention(docs: List<Document>, today: LocalDate): List<Document> =
        docs.filter { !it.archived && state(it, today).let { s -> s == DocState.SOON || s == DocState.EXPIRED } }
            .sortedBy { it.expiry }

    /** Дни до срока, в которые присылать напоминание. */
    fun remindToday(d: Document, today: LocalDate): Boolean {
        if (d.archived) return false
        val left = daysLeft(d, today) ?: return false
        return left == d.remindDays.toLong() || left == 7L || left == 1L || left == 0L
    }

    /** Паспорт РФ меняют в 20 и 45 лет — срок считается от даты рождения. */
    fun passportExpiry(birth: LocalDate, today: LocalDate): LocalDate? = listOf(20L, 45L)
        .map { birth.plusYears(it) }
        .firstOrNull { !it.isBefore(today.minusYears(1)) }

    /** Маска номера: видны только последние 4 символа. */
    fun masked(number: String): String {
        val n = number.trim()
        if (n.length <= 4) return n
        return "•••• " + n.takeLast(4)
    }

    /** Подписки: активные, сначала ближайшие списания. */
    fun subscriptions(w: Workspace): List<Recurring> = w.recurrings.filter { it.subscription }
        .sortedWith(compareBy({ !it.active }, { it.next }))

    fun trialLeft(r: Recurring, today: LocalDate): Long? =
        r.trialUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { ChronoUnit.DAYS.between(today, it) }?.takeIf { it >= 0 }
}

object DocsOps {
    fun upsert(w: Workspace, d: Document) =
        if (w.documents.any { it.id == d.id }) w.copy(documents = w.documents.map { if (it.id == d.id) d else it })
        else w.copy(documents = w.documents + d)

    fun delete(w: Workspace, id: String) = w.copy(documents = w.documents.filter { it.id != id })
}

/** Популярные в России подписки — для быстрого добавления (цены пользователь вводит сам). */
val SUB_CATALOG = listOf(
    "Яндекс Плюс", "Кинопоиск", "Okko", "Иви", "Wink", "Start", "Premier", "VK Музыка", "Звук",
    "Telegram Premium", "СберПрайм", "Ozon Premium", "Яндекс 360", "VK Combo", "МТС Premium",
    "iCloud+", "Google One", "Литрес", "Букмейт", "Мобильная связь", "Домашний интернет",
)
