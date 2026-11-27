package com.example.remindernote

import java.time.LocalDate
import java.time.YearMonth

enum class RepeatPeriod { MONTH, YEAR }

/** Действует / Исполнено / Просрочено / Завершено. */
enum class Status { ACTIVE, DONE, OVERDUE, COMPLETED }

/**
 * Сегодня (сегодня), Неделя (до 7 дней, включая сегодня), Месяц (до месяца, включая предыдущие),
 * Год (только то, что дальше месяца).
 */
enum class TabBucket { TODAY, WEEK, MONTH, YEAR }

data class Reminder(
    val id: Long,
    val icon: String,
    val title: String,
    val repeat: Boolean,
    val period: RepeatPeriod,
    /** Число месяца (1..31) для повтора «месяц». */
    val execDay: Int,
    /** Дата исполнения: для разового события и для повтора «год» (год игнорируется). */
    val execDate: LocalDate?,
    val endDate: LocalDate?,
    val remindDays: Int,
    val status: Status = Status.ACTIVE,
    /** Дата последней смены статуса. */
    val statusDate: LocalDate? = null,
    /** Для повторяемых: дата исполнения в текущем периоде (хранится, сама не «едет» вперёд). */
    val currentExec: LocalDate? = null,
    /** Краткое содержание события (1–2 строки). */
    val description: String = "",
    /** Дата последнего действия «Исполнить». */
    val doneDate: LocalDate? = null,
    /** Дата действия «Завершить». */
    val completedDate: LocalDate? = null
)

data class Schedule(
    val nextExec: LocalDate?,
    val remindDate: LocalDate?,
    /** Ближайшая из дат (напоминание / исполнение / завершение), не раньше сегодня. */
    val keyDate: LocalDate?,
    val bucket: TabBucket?
)

data class ReminderItem(val reminder: Reminder, val schedule: Schedule)

private fun clamp(ym: YearMonth, day: Int): LocalDate = ym.atDay(minOf(day, ym.lengthOfMonth()))

private fun nextMonthly(today: LocalDate, day: Int): LocalDate {
    val ym = YearMonth.from(today)
    val d = clamp(ym, day)
    return if (d.isBefore(today)) clamp(ym.plusMonths(1), day) else d
}

private fun nextYearly(today: LocalDate, base: LocalDate): LocalDate {
    val d = clamp(YearMonth.of(today.year, base.month), base.dayOfMonth)
    return if (d.isBefore(today)) clamp(YearMonth.of(today.year + 1, base.month), base.dayOfMonth) else d
}

/** Дата исполнения в текущем периоде (для разовой — просто дата исполнения). */
fun Reminder.effectiveExec(today: LocalDate): LocalDate? = when {
    !repeat -> execDate
    currentExec != null -> currentExec
    period == RepeatPeriod.MONTH -> nextMonthly(today, execDay)
    else -> execDate?.let { nextYearly(today, it) }
}

fun Reminder.schedule(today: LocalDate): Schedule {
    val next = effectiveExec(today)
    val remind = next?.minusDays(remindDays.toLong())
    val key = listOfNotNull(remind, next, endDate)
        .filter { !it.isBefore(today) }
        .minByOrNull { it.toEpochDay() }

    val bucket = when {
        key == null -> null
        key == today -> TabBucket.TODAY
        !key.isAfter(today.plusWeeks(1)) -> TabBucket.WEEK
        !key.isAfter(today.plusMonths(1)) -> TabBucket.MONTH
        else -> TabBucket.YEAR // дальше месяца
    }
    return Schedule(next, remind, key, bucket)
}

/** Порядок на главном экране: по возрастанию ближайшей даты; карточки без будущих дат — в конце. */
val ReminderItemComparator: Comparator<ReminderItem> =
    compareBy<ReminderItem> { it.schedule.keyDate?.toEpochDay() ?: Long.MAX_VALUE }
        .thenBy { it.schedule.nextExec?.toEpochDay() ?: Long.MAX_VALUE }
        .thenBy { it.reminder.title.lowercase() }

// ---------- Статусы ----------

/** Следующая дата исполнения после [cur] (на период вперёд), не раньше [notBefore]. */
private fun Reminder.nextOccurrenceAfter(cur: LocalDate, notBefore: LocalDate): LocalDate? {
    val base = execDate
    var d = cur
    do {
        d = when (period) {
            RepeatPeriod.MONTH -> clamp(YearMonth.from(d).plusMonths(1), execDay)
            RepeatPeriod.YEAR -> {
                if (base == null) return null
                clamp(YearMonth.of(d.year + 1, base.month), base.dayOfMonth)
            }
        }
    } while (d.isBefore(notBefore))
    return d
}

/**
 * Правила статусов:
 *  - «Действует» / «Просрочено»: сегодня <= даты исполнения — «Действует», сегодня > — «Просрочено»;
 *  - «Исполнить» ставит «Исполнено». У повторяемого событие на следующие сутки получает дату исполнения
 *    следующего периода (напоминание считается от неё) и снова «Действует»; если следующий период
 *    позже даты завершения — «Завершено»;
 *  - «Завершить» ставит «Завершено».
 * В архив карточка попадает отдельно, см. [isArchived].
 */
fun Reminder.recalcStatus(today: LocalDate): Reminder {
    var r = this

    // 1. Повторяемое «Исполнено»: на следующие сутки переходим на следующий период.
    if (r.repeat && r.status == Status.DONE) {
        val doneOn = r.statusDate
        if (doneOn != null && doneOn.isBefore(today)) {
            val cur = r.effectiveExec(today)
            val next = cur?.let { r.nextOccurrenceAfter(it, doneOn) }
            val end = r.endDate
            val changeDay = doneOn.plusDays(1)
            r = if (next == null || (end != null && next.isAfter(end))) {
                r.copy(status = Status.COMPLETED, statusDate = changeDay, completedDate = changeDay)
            } else {
                // Для «года» дата исполнения тоже переходит на новый год, чтобы форма показывала актуальную дату.
                r.copy(
                    currentExec = next,
                    execDate = if (r.period == RepeatPeriod.YEAR) next else r.execDate,
                    status = Status.ACTIVE,
                    statusDate = changeDay
                )
            }
        }
    }

    // 2. «Действует» <-> «Просрочено» по дате исполнения.
    if (r.status == Status.ACTIVE || r.status == Status.OVERDUE) {
        val exec = r.effectiveExec(today)
        val overdue = exec != null && today.isAfter(exec)
        if (r.status == Status.ACTIVE && overdue && exec != null) {
            r = r.copy(status = Status.OVERDUE, statusDate = exec.plusDays(1))
        } else if (r.status == Status.OVERDUE && !overdue) {
            r = r.copy(status = Status.ACTIVE, statusDate = today)
        }
    }

    // 3. Старые повторяемые карточки без сохранённой даты периода.
    if (r.repeat && r.currentExec == null) r = r.copy(currentExec = r.effectiveExec(today))
    return r
}

/** При сохранении формы: пересчитать дату периода, если параметры повтора изменились (или карточка новая). */
fun Reminder.withFreshExec(previous: Reminder?, today: LocalDate): Reminder {
    if (!repeat) return copy(currentExec = null)
    val kept = previous
        ?.takeIf { it.repeat && it.period == period && it.execDay == execDay && it.execDate == execDate }
        ?.currentExec
    if (kept != null) return copy(currentExec = kept)
    val fresh = when (period) {
        RepeatPeriod.MONTH -> nextMonthly(today, execDay)
        RepeatPeriod.YEAR -> execDate?.let { nextYearly(today, it) }
    }
    return copy(currentExec = fresh)
}

/**
 * В архиве: «Завершено», а у неповторяемого ещё и «Исполнено» — со следующих суток после даты смены статуса.
 * Повторяемое «Исполнено» остаётся в списке и переходит на следующий период.
 */
fun Reminder.isArchived(today: LocalDate): Boolean {
    val finished = status == Status.COMPLETED || (status == Status.DONE && !repeat)
    return finished && (statusDate?.isBefore(today) ?: true)
}

/** Список для экрана: основной или архив. */
fun List<Reminder>.toItems(today: LocalDate, tabPosition: Int, archive: Boolean): List<ReminderItem> =
    asSequence()
        .filter { it.isArchived(today) == archive }
        .map { ReminderItem(it, it.schedule(today)) }
        .filter { item ->
            val bucket = item.schedule.bucket
            when {
                tabPosition == 0 -> true // «Все»
                bucket == null -> false
                tabPosition == 4 -> bucket == TabBucket.YEAR // «Год» — только то, что дальше месяца
                else -> bucket.ordinal <= tabPosition - 1 // Сегодня / Неделя / Месяц накопительные
            }
        }
        .sortedWith(ReminderItemComparator)
        .toList()
