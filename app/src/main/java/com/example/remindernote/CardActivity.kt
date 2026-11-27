package com.example.remindernote

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.example.remindernote.databinding.ActivityCardBinding
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate

/**
 * Одна форма для трёх режимов:
 *  - добавление (нет id, поля редактируемы);
 *  - просмотр (есть id, поля заблокированы, внизу «Редактировать»);
 *  - редактирование (есть id, внизу «Сохранить»).
 */
class CardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "card_id"
        const val EXTRA_ADDED = "card_added"
        private val ICONS = listOf(
            "🔔", "🎂", "🎁", "💊", "💳", "🏠", "🚗", "📞", "✈️", "🛒", "📅", "⭐", "💡", "🔧", "🐾", "💼", "📚", "🏋️", "🧾"
        )
        private const val S_EDITING = "editing"
        private const val S_ICON = "icon"
        private const val S_EXEC_DATE = "exec_date"
        private const val S_EXEC_DAY = "exec_day"
        private const val S_END_DATE = "end_date"
        private const val S_STATUS = "status"
        private const val S_STATUS_DATE = "status_date"
        private const val S_DONE_DATE = "done_date"
        private const val S_COMPLETED_DATE = "completed_date"
    }

    private lateinit var b: ActivityCardBinding

    private var reminderId = -1L
    private var editing = false

    private var selectedIcon = ICONS[0]
    private var execDay = 1
    private var execDate: LocalDate? = null
    private var endDate: LocalDate? = null
    private var status = Status.ACTIVE
    private var statusDate: LocalDate? = null
    private var doneDate: LocalDate? = null
    private var completedDate: LocalDate? = null

    private val isNew get() = reminderId == -1L
    private val isMonthly get() = b.switchRepeat.isChecked && b.btnMonth.id == b.togglePeriod.checkedButtonId

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        b = ActivityCardBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.cardRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        reminderId = intent.getLongExtra(EXTRA_ID, -1L)

        // Чтобы заблокированные поля в режиме просмотра оставались читаемыми.
        val textColor = MaterialColors.getColor(b.root, com.google.android.material.R.attr.colorOnSurface)
        listOf(b.etTitle, b.etDescription, b.etExec, b.etEnd, b.etRemind).forEach { it.setTextColor(textColor) }

        buildIconChips()
        b.togglePeriod.check(b.btnMonth.id)
        b.etRemind.setText("1")

        if (savedInstanceState != null) {
            editing = savedInstanceState.getBoolean(S_EDITING)
            selectedIcon = savedInstanceState.getString(S_ICON) ?: ICONS[0]
            execDay = savedInstanceState.getInt(S_EXEC_DAY, 1)
            execDate = savedInstanceState.getString(S_EXEC_DATE)?.let(LocalDate::parse)
            endDate = savedInstanceState.getString(S_END_DATE)?.let(LocalDate::parse)
            status = Status.valueOf(savedInstanceState.getString(S_STATUS) ?: Status.ACTIVE.name)
            statusDate = savedInstanceState.getString(S_STATUS_DATE)?.let(LocalDate::parse)
            doneDate = savedInstanceState.getString(S_DONE_DATE)?.let(LocalDate::parse)
            completedDate = savedInstanceState.getString(S_COMPLETED_DATE)?.let(LocalDate::parse)
        } else if (!isNew) {
            val r = ReminderRepository.get(this, reminderId)
            if (r == null) {
                finish()
                return
            }
            fillFrom(r)
            editing = false
        } else {
            editing = true
            statusDate = LocalDate.now()
        }

        selectIconChip()
        wireListeners()
        renderDates()
        applyMode()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(S_EDITING, editing)
        outState.putString(S_ICON, selectedIcon)
        outState.putInt(S_EXEC_DAY, execDay)
        execDate?.let { outState.putString(S_EXEC_DATE, it.toString()) }
        endDate?.let { outState.putString(S_END_DATE, it.toString()) }
        outState.putString(S_STATUS, status.name)
        statusDate?.let { outState.putString(S_STATUS_DATE, it.toString()) }
        doneDate?.let { outState.putString(S_DONE_DATE, it.toString()) }
        completedDate?.let { outState.putString(S_COMPLETED_DATE, it.toString()) }
    }

    // ---------- Построение формы ----------

    private fun buildIconChips() {
        ICONS.forEach { emoji ->
            val chip = layoutInflater.inflate(R.layout.chip_icon, b.chipGroup, false) as Chip
            chip.id = View.generateViewId()
            chip.text = emoji
            chip.tag = emoji
            b.chipGroup.addView(chip)
        }
        b.chipGroup.setOnCheckedStateChangeListener { group, ids ->
            val chip = ids.firstOrNull()?.let { group.findViewById<Chip>(it) }
            selectedIcon = chip?.tag as? String ?: selectedIcon
        }
    }

    private fun selectIconChip() {
        for (i in 0 until b.chipGroup.childCount) {
            val chip = b.chipGroup.getChildAt(i) as Chip
            if (chip.tag == selectedIcon) {
                b.chipGroup.check(chip.id)
                return
            }
        }
    }

    private fun fillFrom(r: Reminder) {
        selectedIcon = r.icon
        b.etTitle.setText(r.title)
        b.etDescription.setText(r.description)
        b.switchRepeat.isChecked = r.repeat
        b.togglePeriod.check(if (r.period == RepeatPeriod.MONTH) b.btnMonth.id else b.btnYear.id)
        execDay = r.execDay
        execDate = r.execDate
        endDate = r.endDate
        status = r.status
        statusDate = r.statusDate
        doneDate = r.doneDate
        completedDate = r.completedDate
        b.etRemind.setText(r.remindDays.toString())
    }

    private fun wireListeners() {
        b.switchRepeat.setOnCheckedChangeListener { _, _ ->
            updatePeriodVisibility()
            renderDates()
            updateTabPreview()
        }
        b.togglePeriod.addOnButtonCheckedListener { _, _, _ ->
            renderDates()
            updateTabPreview()
        }
        b.etExec.setOnClickListener { if (editing) pickExec() }
        b.etEnd.setOnClickListener { if (editing) pickEnd() }
        b.tilEnd.setEndIconOnClickListener {
            if (editing) {
                endDate = null
                renderDates()
                updateTabPreview()
            }
        }
        b.etRemind.doAfterTextChanged { updateTabPreview() }
        b.btnDone.setOnClickListener { changeStatus(Status.DONE) }
        b.btnComplete.setOnClickListener { changeStatus(Status.COMPLETED) }
        b.btnAction.setOnClickListener {
            if (editing) save() else {
                editing = true
                applyMode()
            }
        }
    }

    // ---------- Режимы ----------

    private fun applyMode() {
        supportActionBar?.setTitle(
            when {
                isNew -> R.string.title_new
                editing -> R.string.title_edit
                else -> R.string.title_view
            }
        )
        b.btnAction.setText(if (editing) R.string.action_save else R.string.action_edit)

        b.tilTitle.isEnabled = editing
        b.tilDescription.isEnabled = editing
        b.tilExec.isEnabled = editing
        b.tilEnd.isEnabled = editing
        b.tilRemind.isEnabled = editing
        b.switchRepeat.isEnabled = editing
        for (i in 0 until b.togglePeriod.childCount) b.togglePeriod.getChildAt(i).isEnabled = editing
        for (i in 0 until b.chipGroup.childCount) b.chipGroup.getChildAt(i).isEnabled = editing

        // Просмотр: иконка и название в одной строке, повтор — обычным текстом; редактирование: поля формы.
        val editVis = if (editing) View.VISIBLE else View.GONE
        val viewVis = if (editing) View.GONE else View.VISIBLE
        b.labelIcon.visibility = editVis
        b.scrollIcons.visibility = editVis
        b.tilTitle.visibility = editVis
        b.tilDescription.visibility = editVis
        b.switchRepeat.visibility = editVis
        b.rowHeader.visibility = viewVis
        b.tvRepeatInfo.visibility = viewVis
        val description = b.etDescription.text?.toString()?.trim().orEmpty()
        b.tvIcon.text = selectedIcon
        b.tvHeaderTitle.text = b.etTitle.text?.toString()?.trim().orEmpty()
        b.tvDescriptionView.text = description
        b.tvDescriptionView.visibility = if (!editing && description.isNotEmpty()) View.VISIBLE else View.GONE
        b.tvRepeatInfo.setText(
            when {
                !b.switchRepeat.isChecked -> R.string.repeat_view_none
                b.btnYear.id == b.togglePeriod.checkedButtonId -> R.string.repeat_view_year
                else -> R.string.repeat_view_month
            }
        )
        updatePeriodVisibility()
        renderDates()
        renderStatus()
        updateTabPreview()
        invalidateOptionsMenu()
    }

    private fun updatePeriodVisibility() {
        b.groupPeriod.visibility = if (editing && b.switchRepeat.isChecked) View.VISIBLE else View.GONE
    }

    // ---------- Статус ----------

    private fun renderStatus() {
        b.tvStatus.text = getString(R.string.status_line, statusLabel(this, status))
        b.tvStatusDate.text = getString(
            R.string.status_date_line, statusDate?.format(DATE_FORMAT) ?: getString(R.string.not_set)
        )
        b.tvDoneDate.text = doneDate?.let { getString(R.string.done_line, it.format(DATE_FORMAT)) }.orEmpty()
        b.tvDoneDate.visibility = if (doneDate != null) View.VISIBLE else View.GONE
        b.tvCompletedDate.text = completedDate?.let { getString(R.string.completed_line, it.format(DATE_FORMAT)) }.orEmpty()
        b.tvCompletedDate.visibility = if (completedDate != null) View.VISIBLE else View.GONE
        // Действия доступны только для сохранённой карточки в режиме просмотра.
        b.groupActions.visibility = if (!isNew && !editing) View.VISIBLE else View.GONE
        b.btnDone.isEnabled = status == Status.ACTIVE || status == Status.OVERDUE
        b.btnComplete.isEnabled = status != Status.COMPLETED
    }

    private fun changeStatus(newStatus: Status) {
        val stored = ReminderRepository.get(this, reminderId) ?: return
        val today = LocalDate.now()
        val updated = stored.copy(
            status = newStatus,
            statusDate = today,
            doneDate = if (newStatus == Status.DONE) today else stored.doneDate,
            completedDate = if (newStatus == Status.COMPLETED) today else stored.completedDate
        )
        ReminderRepository.save(this, updated)
        status = updated.status
        statusDate = updated.statusDate
        doneDate = updated.doneDate
        completedDate = updated.completedDate
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ID, reminderId))
        renderStatus()
        Toast.makeText(
            this,
            if (newStatus == Status.DONE) R.string.toast_done else R.string.toast_completed,
            Toast.LENGTH_SHORT
        ).show()
    }

    // ---------- Даты ----------

    private fun renderDates() {
        if (isMonthly) {
            b.tilExec.hint = getString(R.string.field_exec_day)
            b.etExec.setText(execDay.toString())
        } else {
            b.tilExec.hint = getString(R.string.field_exec_date)
            b.etExec.setText(execDate?.format(DATE_FORMAT) ?: "")
        }
        b.etEnd.setText(endDate?.format(DATE_FORMAT) ?: "")
        b.tilEnd.isEndIconVisible = editing && endDate != null
    }

    private fun pickExec() {
        if (isMonthly) {
            val days = (1..31).map { it.toString() }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.field_exec_day)
                .setSingleChoiceItems(days, execDay - 1) { dialog, which ->
                    execDay = which + 1
                    renderDates()
                    updateTabPreview()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            pickDate(execDate) {
                execDate = it
                b.tilExec.error = null
                renderDates()
                updateTabPreview()
            }
        }
    }

    private fun pickEnd() = pickDate(endDate) {
        endDate = it
        renderDates()
        updateTabPreview()
    }

    private fun pickDate(initial: LocalDate?, onPicked: (LocalDate) -> Unit) {
        val d = initial ?: LocalDate.now()
        DatePickerDialog(this, { _, y, m, day -> onPicked(LocalDate.of(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    // ---------- Чтение формы / сохранение ----------

    private fun readForm(): Reminder? {
        val repeat = b.switchRepeat.isChecked
        val period = if (b.btnYear.id == b.togglePeriod.checkedButtonId) RepeatPeriod.YEAR else RepeatPeriod.MONTH
        val monthly = repeat && period == RepeatPeriod.MONTH
        if (!monthly && execDate == null) return null
        return Reminder(
            id = reminderId,
            icon = selectedIcon,
            title = b.etTitle.text?.toString()?.trim().orEmpty(),
            repeat = repeat,
            period = period,
            execDay = execDay,
            execDate = if (monthly) null else execDate,
            endDate = endDate,
            remindDays = b.etRemind.text?.toString()?.toIntOrNull() ?: 0,
            status = status,
            statusDate = statusDate,
            doneDate = doneDate,
            completedDate = completedDate,
            description = b.etDescription.text?.toString()?.trim().orEmpty()
        )
    }

    /** Показывает, на какой вкладке окажется карточка. */
    private fun updateTabPreview() {
        val bucket = readForm()?.schedule(LocalDate.now())?.bucket
        b.tvTab.text = getString(R.string.tab_of_card, bucketLabel(this, bucket))
    }

    private fun save() {
        val form = readForm()
        var ok = true
        if (b.etTitle.text?.toString()?.isBlank() != false) {
            b.tilTitle.error = getString(R.string.error_title)
            ok = false
        } else b.tilTitle.error = null
        if (form == null) {
            b.tilExec.error = getString(R.string.error_exec)
            ok = false
        } else b.tilExec.error = null
        if (!ok || form == null) return

        val wasNew = isNew
        if (wasNew) reminderId = ReminderRepository.nextId(this)
        // Дата периода и статус «Действует» / «Просрочено» пересчитываются по новым данным.
        val today = LocalDate.now()
        val previous = if (wasNew) null else ReminderRepository.get(this, reminderId)
        val toSave = form.copy(id = reminderId).withFreshExec(previous, today).recalcStatus(today)
        ReminderRepository.save(this, toSave)
        status = toSave.status
        statusDate = toSave.statusDate
        doneDate = toSave.doneDate
        completedDate = toSave.completedDate

        setResult(RESULT_OK, Intent().putExtra(EXTRA_ID, reminderId).putExtra(EXTRA_ADDED, wasNew))
        if (wasNew) {
            finish()
        } else {
            editing = false
            applyMode()
            renderDates()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Меню ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_card, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_delete)?.isVisible = !isNew
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_delete -> {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    ReminderRepository.delete(this, reminderId)
                    setResult(RESULT_OK)
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
