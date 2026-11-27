package com.example.remindernote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Хранение карточек в SharedPreferences (JSON). */
object ReminderRepository {
    private const val PREFS = "reminders"
    private const val KEY_ITEMS = "items"
    private const val KEY_NEXT_ID = "next_id"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(ctx: Context): List<Reminder> {
        val raw = prefs(ctx).getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(ctx: Context, id: Long): Reminder? = getAll(ctx).firstOrNull { it.id == id }

    fun nextId(ctx: Context): Long {
        val p = prefs(ctx)
        val id = p.getLong(KEY_NEXT_ID, 1L)
        p.edit().putLong(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    fun save(ctx: Context, reminder: Reminder) {
        val list = getAll(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == reminder.id }
        if (idx >= 0) list[idx] = reminder else list.add(reminder)
        write(ctx, list)
    }

    fun delete(ctx: Context, id: Long) {
        write(ctx, getAll(ctx).filter { it.id != id })
    }

    /** Пересчитывает статусы относительно [today]; возвращает число изменённых карточек. */
    fun recalcAll(ctx: Context, today: LocalDate): Int {
        val old = getAll(ctx)
        val updated = old.map { it.recalcStatus(today) }
        val changed = old.indices.count { old[it] != updated[it] }
        if (changed > 0) write(ctx, updated)
        return changed
    }

    private fun write(ctx: Context, list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs(ctx).edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private fun toJson(r: Reminder) = JSONObject().apply {
        put("id", r.id)
        put("icon", r.icon)
        put("title", r.title)
        put("repeat", r.repeat)
        put("period", r.period.name)
        put("execDay", r.execDay)
        put("execDate", r.execDate?.toString() ?: JSONObject.NULL)
        put("endDate", r.endDate?.toString() ?: JSONObject.NULL)
        put("remindDays", r.remindDays)
        put("status", r.status.name)
        put("statusDate", r.statusDate?.toString() ?: JSONObject.NULL)
        put("currentExec", r.currentExec?.toString() ?: JSONObject.NULL)
        put("description", r.description)
    }

    private fun fromJson(o: JSONObject) = Reminder(
        id = o.getLong("id"),
        icon = o.getString("icon"),
        title = o.getString("title"),
        repeat = o.getBoolean("repeat"),
        period = RepeatPeriod.valueOf(o.getString("period")),
        execDay = o.getInt("execDay"),
        execDate = if (o.isNull("execDate")) null else LocalDate.parse(o.getString("execDate")),
        endDate = if (o.isNull("endDate")) null else LocalDate.parse(o.getString("endDate")),
        remindDays = o.getInt("remindDays"),
        // Старые записи без статуса считаются «Действует».
        status = Status.valueOf(o.optString("status", Status.ACTIVE.name)),
        statusDate = if (o.isNull("statusDate")) null else LocalDate.parse(o.getString("statusDate")),
        currentExec = if (o.isNull("currentExec")) null else LocalDate.parse(o.getString("currentExec")),
        description = o.optString("description", "")
    )
}
