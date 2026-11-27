package com.example.remindernote

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.remindernote.databinding.ItemReminderBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
val DAY_MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

fun bucketLabel(ctx: Context, bucket: TabBucket?): String = ctx.getString(
    when (bucket) {
        TabBucket.TODAY -> R.string.tab_today
        TabBucket.WEEK -> R.string.tab_week
        TabBucket.MONTH -> R.string.tab_month
        TabBucket.YEAR -> R.string.tab_year
        null -> R.string.tab_all
    }
)

fun statusLabel(ctx: Context, status: Status): String = ctx.getString(
    when (status) {
        Status.ACTIVE -> R.string.status_active
        Status.DONE -> R.string.status_done
        Status.OVERDUE -> R.string.status_overdue
        Status.COMPLETED -> R.string.status_completed
    }
)

class ReminderAdapter(
    private val onClick: (Reminder) -> Unit
) : ListAdapter<ReminderItem, ReminderAdapter.Holder>(Diff) {

    class Holder(val b: ItemReminderBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val r = item.reminder
        val s = item.schedule
        val ctx = holder.itemView.context
        val today = LocalDate.now()

        holder.b.icon.text = r.icon
        holder.b.title.text = r.title
        holder.b.description.text = r.description
        holder.b.description.visibility =
            if (r.description.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

        val repeatText = when {
            !r.repeat -> null
            r.period == RepeatPeriod.MONTH -> ctx.getString(R.string.repeat_month)
            else -> ctx.getString(R.string.repeat_year)
        }
        val execText = when {
            s.nextExec != null -> ctx.getString(R.string.exec_line, s.nextExec.format(DATE_FORMAT))
            r.repeat -> ctx.getString(R.string.exec_line_ended)
            else -> ctx.getString(R.string.exec_line_none)
        }
        holder.b.details.text = listOfNotNull(execText, repeatText).joinToString(" · ")

        val extra = listOfNotNull(
            s.remindDate?.takeIf { r.remindDays > 0 }
                ?.let { ctx.getString(R.string.remind_line, it.format(DATE_FORMAT)) },
            r.endDate?.let { ctx.getString(R.string.end_line, it.format(DATE_FORMAT)) },
            r.doneDate?.let { ctx.getString(R.string.done_line, it.format(DATE_FORMAT)) },
            r.completedDate?.let { ctx.getString(R.string.completed_line, it.format(DATE_FORMAT)) }
        )
        holder.b.extra.text = extra.joinToString(" · ")
        holder.b.extra.visibility = if (extra.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE

        val next = s.nextExec
        val finished = r.status == Status.DONE || r.status == Status.COMPLETED
        holder.b.badge.text = when {
            r.isArchived(today) -> "" // в архиве badge не заполняем
            next == null -> ""
            else -> {
                val d = ChronoUnit.DAYS.between(today, next)
                when {
                    d == 0L -> ctx.getString(R.string.badge_today)
                    d > 0 -> ctx.getString(R.string.badge_in_days, d.toInt())
                    finished -> "" // исполненное / завершённое не может быть «просрочено»
                    else -> ctx.getString(R.string.badge_overdue)
                }
            }
        }

        // Статус показываем под количеством дней, если он не «Действует».
        if (r.status == Status.ACTIVE) {
            holder.b.status.visibility = android.view.View.GONE
        } else {
            holder.b.status.text = statusLabel(ctx, r.status)
            holder.b.status.visibility = android.view.View.VISIBLE
        }

        holder.itemView.setOnClickListener { onClick(r) }
    }

    private object Diff : DiffUtil.ItemCallback<ReminderItem>() {
        override fun areItemsTheSame(a: ReminderItem, b: ReminderItem) = a.reminder.id == b.reminder.id
        override fun areContentsTheSame(a: ReminderItem, b: ReminderItem) = a == b
    }
}
