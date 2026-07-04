package com.example.worktr.ui

import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.graphics.ColorUtils
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.WorkEntry
import com.example.worktr.data.WorkEntryRepository
import com.example.worktr.ui.picker.DynamicYearSpinner
import com.example.worktr.ui.picker.DurationPicker
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.worktr.ui.responsive.ResponsiveProfile
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.ShiftType
import com.example.worktr.util.localDate
import com.example.worktr.util.salaryBreakdown
import com.example.worktr.util.workedHours
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.*
import java.time.format.TextStyle
import java.util.*
import kotlin.math.max

class CalendarDialogFragment : DialogFragment() {
    private enum class SelectionMode { ADD, DELETE }

    private var currentMonth = YearMonth.now()
    private val days = mutableListOf<LocalDate?>()
    private val entries = mutableSetOf<LocalDate>()
    private val entryByDate = mutableMapOf<LocalDate, WorkEntry>()
    private val selectedDates = mutableSetOf<LocalDate>()
    private lateinit var adapter: DayAdapter
    private lateinit var repo: WorkEntryRepository
    private lateinit var yearSpinner: DynamicYearSpinner
    private lateinit var bulkButton: MaterialButton
    private lateinit var selectionState: TextView
    private lateinit var responsiveProfile: ResponsiveProfile
    private var entriesJob: Job? = null
    private var selectionMode: SelectionMode? = null
    private val numberFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }
    private val jobId get() = requireArguments().getInt("jobId")
    private val resultPrefix get() = requireArguments().getString(ARG_RESULT_PREFIX) ?: REQUEST_DEFAULT

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.dialog_calendar, container, false)

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.surface)))
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window.decorView.setPadding(0, 0, 0, 0)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = DatabaseProvider.get(requireContext())
        repo = WorkEntryRepository(db.workEntryDao())
        adapter = DayAdapter()
        bulkButton = view.findViewById(R.id.buttonBulkAdd)
        selectionState = view.findViewById(R.id.textSelectionState)
        responsiveProfile = ResponsiveUi.profile(requireContext())
        bulkButton.setOnClickListener {
            when (selectionMode) {
                SelectionMode.ADD -> finishAddSelection()
                SelectionMode.DELETE -> confirmBulkDelete()
                null -> Unit
            }
        }
        applyResponsiveLayout(view)
        applyWindowInsets(view)

        // Year spinner setup
        val inputYear = view.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.inputCalendarYear)
        yearSpinner = DynamicYearSpinner(
            context = requireContext(),
            input = inputYear,
            initialYear = currentMonth.year
        ) { selectedYear ->
            currentMonth = YearMonth.of(selectedYear, currentMonth.month)
            render(view)
        }
        view.findViewById<MaterialButton>(R.id.buttonToday).setOnClickListener {
            currentMonth = YearMonth.now()
            yearSpinner.setYear(currentMonth.year)
            render(view)
        }

        // Prev/Next buttons
        val btnPrev = view.findViewById<ImageButton>(R.id.buttonPrevMonth)
        val btnNext = view.findViewById<ImageButton>(R.id.buttonNextMonth)
        btnPrev.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            yearSpinner.setYear(currentMonth.year)
            render(view)
        }
        btnNext.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            yearSpinner.setYear(currentMonth.year)
            render(view)
        }

        // Calendar grid
        val grid = view.findViewById<GridView>(R.id.calendarGridDialog)
        grid.isNestedScrollingEnabled = false
        grid.adapter = adapter
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val date = days[pos] ?: return@OnItemClickListener
            if (selectedDates.isNotEmpty()) {
                toggleSelection(date)
                updateSelectionUi()
                adapter.notifyDataSetChanged()
            } else {
                // Single selection
                setFragmentResult(dateResultKey(resultPrefix), Bundle().apply {
                    putLong("date", date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                })
                dismiss()
            }
        }
        grid.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val date = days[pos] ?: return@OnItemLongClickListener true
            if (selectedDates.isNotEmpty()) {
                if (!canToggleSelection(date, true)) return@OnItemLongClickListener true
                toggleSelection(date)
                updateSelectionUi()
                adapter.notifyDataSetChanged()
            } else if (entries.contains(date)) {
                showEntryDetails(date)
            } else {
                if (!canToggleSelection(date, true)) return@OnItemLongClickListener true
                toggleSelection(date)
                updateSelectionUi()
                adapter.notifyDataSetChanged()
            }
            true
        }

        render(view)
    }

    private fun applyWindowInsets(view: View) {
        val scroll = view.findViewById<ScrollView>(R.id.calendarScroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = responsiveProfile.screenPaddingPx + safeInsets.left,
                top = responsiveProfile.screenPaddingPx + safeInsets.top,
                right = responsiveProfile.screenPaddingPx + safeInsets.right,
                bottom = responsiveProfile.screenPaddingPx + safeInsets.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
    }

    private fun canToggleSelection(date: LocalDate, startSelection: Boolean = false): Boolean {
        return when (selectionMode ?: if (startSelection) modeForDate(date) else null) {
            SelectionMode.ADD -> !entries.contains(date)
            SelectionMode.DELETE -> entries.contains(date)
            null -> true
        }
    }

    private fun toggleSelection(date: LocalDate) {
        if (selectionMode == null) {
            selectionMode = modeForDate(date)
        }
        if (!canToggleSelection(date)) return
        if (selectedDates.contains(date)) {
            selectedDates.remove(date)
            if (selectedDates.isEmpty()) selectionMode = null
        } else {
            selectedDates.add(date)
        }
    }

    private fun modeForDate(date: LocalDate): SelectionMode =
        if (entries.contains(date)) SelectionMode.DELETE else SelectionMode.ADD

    private fun finishAddSelection() {
        val dates = selectedDates.toList()
        if (dates.isEmpty()) return
        setFragmentResult(datesResultKey(resultPrefix), Bundle().apply {
            putLongArray(
                "dates",
                dates.map { it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                    .toLongArray()
            )
        })
        dismiss()
    }

    private fun showEntryDetails(date: LocalDate) {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        viewLifecycleOwner.lifecycleScope.launch {
            val entry = repo.getEntryForDay(jobId, start, end) ?: return@launch
            val detailsView = layoutInflater.inflate(R.layout.dialog_entry_details, null)
            bindEntryDetails(detailsView, entry, zone)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.entry_details_title)
                .setView(detailsView)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.entry_details_multi_delete) { _, _ ->
                    startDeleteSelection(date)
                }
                .setPositiveButton(R.string.entry_details_edit) { _, _ ->
                    setFragmentResult(dateResultKey(resultPrefix), Bundle().apply { putLong("date", start) })
                    dismiss()
                }
                .show()
        }
    }

    private fun bindEntryDetails(view: View, entry: WorkEntry, zone: ZoneId) {
        val date = entry.localDate(zone)
        val breakdown = entry.salaryBreakdown(zone)
        val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val holidayLabel = if (entry.isHoliday) {
            getString(R.string.entry_details_holiday_yes)
        } else {
            getString(R.string.entry_details_holiday_no)
        }
        view.findViewById<TextView>(R.id.textEntryDate).text =
            getString(R.string.entry_details_date, date.toString())
        val shiftLabels = resources.getStringArray(R.array.shift_types)
        val shiftLabel = shiftLabels.getOrElse(ShiftType.fromStored(entry.shiftType).labelIndex) { entry.shiftType }
        view.findViewById<TextView>(R.id.textEntryShift).text =
            getString(R.string.entry_details_shift, shiftLabel)
        view.findViewById<TextView>(R.id.textEntryHours).text =
            getString(R.string.entry_details_hours, numberFormatter.format(entry.hoursWorked))
        view.findViewById<TextView>(R.id.textEntryBreak).text =
            getString(R.string.entry_details_break, DurationPicker.format(requireContext(), entry.breakHours))
        view.findViewById<TextView>(R.id.textEntryWorked).text =
            getString(R.string.entry_details_worked, numberFormatter.format(entry.workedHours()))
        view.findViewById<TextView>(R.id.textEntryDayType).text =
            getString(R.string.entry_details_day_type, "$dayName • $holidayLabel")
        view.findViewById<TextView>(R.id.textEntrySalary).text =
            getString(R.string.entry_details_salary, numberFormatter.format(breakdown.total))
    }

    private fun startDeleteSelection(date: LocalDate) {
        selectedDates.clear()
        selectionMode = SelectionMode.DELETE
        selectedDates.add(date)
        updateSelectionUi()
        adapter.notifyDataSetChanged()
    }

    private fun confirmBulkDelete() {
        val dates = selectedDates.toList()
        if (dates.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_selected_entries_title)
            .setMessage(getString(R.string.delete_selected_entries_message, dates.size))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_selected_entries_confirm) { _, _ ->
                performBulkDelete(dates)
            }
            .show()
    }

    private fun performBulkDelete(dates: List<LocalDate>) {
        val millis = dates.map { it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        selectedDates.clear()
        selectionMode = null
        updateSelectionUi()
        viewLifecycleOwner.lifecycleScope.launch {
            repo.deleteEntriesForDates(jobId, millis)
            dismiss()
        }
    }

    private fun render(view: View) {
        val zone = ZoneId.systemDefault()
        val first = currentMonth.atDay(1)
        val offset = first.dayOfWeek.ordinal
        days.clear()
        selectedDates.clear()
        selectionMode = null
        updateSelectionUi()
        repeat(offset) { days.add(null) }
        (1..currentMonth.lengthOfMonth()).map { currentMonth.atDay(it) }.forEach { days.add(it) }
        updateCalendarGridHeight(view)
        loadEntries(zone)
        adapter.notifyDataSetChanged()
        view.findViewById<TextView>(R.id.textMonthYear).text =
            currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + currentMonth.year
    }

    private fun applyResponsiveLayout(view: View) {
        ResponsiveUi.applyOuterPadding(view.findViewById(R.id.calendarScroll), responsiveProfile)
        ResponsiveUi.applyContentPadding(view.findViewById(R.id.calendarContent), responsiveProfile)
        val controls = view.findViewById<LinearLayout>(R.id.layoutCalendarControls)
        val todayButton = view.findViewById<MaterialButton>(R.id.buttonToday)
        ResponsiveUi.setLinearOrientation(controls, responsiveProfile.isCompact)
        if (responsiveProfile.isCompact) {
            todayButton.layoutParams = todayButton.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            ResponsiveUi.setStartMargin(todayButton, 0)
            ResponsiveUi.setTopMargin(todayButton, ResponsiveUi.dp(requireContext(), 8))
        } else {
            todayButton.layoutParams = todayButton.layoutParams.apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            ResponsiveUi.setStartMargin(todayButton, ResponsiveUi.dp(requireContext(), 12))
            ResponsiveUi.setTopMargin(todayButton, 0)
        }
    }

    private fun loadEntries(zone: ZoneId) {
        val start = currentMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        entriesJob?.cancel()
        entriesJob = viewLifecycleOwner.lifecycleScope.launch {
            repo.getEntriesForPeriod(jobId, start, end)
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { list ->
                entries.clear()
                entryByDate.clear()
                list.forEach { e ->
                    val date = Instant.ofEpochMilli(e.date).atZone(zone).toLocalDate()
                    entries.add(date)
                    entryByDate[date] = e
                }
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun updateCalendarGridHeight(view: View) {
        val grid = view.findViewById<GridView>(R.id.calendarGridDialog)
        grid.post {
            val rows = ((days.size + 6) / 7).coerceAtLeast(5)
            val height = rows * responsiveProfile.calendarCellHeightPx + (rows - 1) * grid.verticalSpacing
            grid.layoutParams = grid.layoutParams.apply {
                this.height = height
            }
        }
    }

    inner class DayAdapter : BaseAdapter() {
        override fun getCount() = days.size
        override fun getItem(pos: Int) = days[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
            val v = cv ?: layoutInflater.inflate(R.layout.item_day_cell, parent, false)
            val cellWidth = calculateCellWidth(parent as GridView)
            v.layoutParams = AbsListView.LayoutParams(cellWidth, responsiveProfile.calendarCellHeightPx)
            val card = v.findViewById<MaterialCardView>(R.id.dayCard)
            val tv = v.findViewById<TextView>(R.id.dayNumber)
            val dot = v.findViewById<View>(R.id.dayDot)
            val indicators = v.findViewById<LinearLayout>(R.id.dayIndicators)
            val nightDot = v.findViewById<View>(R.id.dayNightDot)
            val bonusDot = v.findViewById<View>(R.id.dayBonusDot)
            days[pos]?.let { d ->
                val isSelected = selectedDates.contains(d)
                val hasEntry = entries.contains(d)
                val entry = entryByDate[d]
                val isToday = d == LocalDate.now()
                val isWeekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
                val isHoliday = entry?.isHoliday == true
                val isNight = entry?.let(::isNightShift) == true
                val hasBonus = entry?.salaryBreakdown(ZoneId.systemDefault())?.let { breakdown ->
                    breakdown.night + breakdown.saturday + breakdown.sunday + breakdown.holiday > 0.0
                } == true
                val surfaceColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface)
                val onSurfaceColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurface)
                val outlineColor = ContextCompat.getColor(requireContext(), R.color.calendar_outline)
                val entryFillColor = ContextCompat.getColor(requireContext(), R.color.calendar_entry_fill)
                val entryStrokeColor = ContextCompat.getColor(requireContext(), R.color.calendar_entry_stroke)
                val selectedFillColor = if (selectionMode == SelectionMode.DELETE) {
                    MaterialColors.getColor(card, androidx.appcompat.R.attr.colorError)
                } else {
                    ContextCompat.getColor(requireContext(), R.color.calendar_selected_fill)
                }
                val selectedTextColor = if (selectionMode == SelectionMode.DELETE) {
                    MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnError)
                } else {
                    ContextCompat.getColor(requireContext(), R.color.calendar_selected_text)
                }
                val todayStrokeColor = ContextCompat.getColor(requireContext(), R.color.calendar_today_stroke)
                val weekendTextColor = ContextCompat.getColor(requireContext(), R.color.calendar_weekend_text)
                val dotColor = ContextCompat.getColor(requireContext(), R.color.calendar_dot)
                val holidayColor = ContextCompat.getColor(requireContext(), R.color.calendar_holiday)
                val nightColor = ContextCompat.getColor(requireContext(), R.color.calendar_night)
                val bonusColor = ContextCompat.getColor(requireContext(), R.color.calendar_bonus)

                tv.text = d.dayOfMonth.toString()
                card.setCardBackgroundColor(
                    when {
                        isSelected -> selectedFillColor
                        hasEntry -> entryFillColor
                        else -> surfaceColor
                    }
                )
                card.strokeColor = when {
                    isSelected -> selectedFillColor
                    isHoliday -> holidayColor
                    isToday -> todayStrokeColor
                    hasEntry -> entryStrokeColor
                    else -> outlineColor
                }
                card.strokeWidth = when {
                    isSelected -> ResponsiveUi.dp(requireContext(), 2)
                    isToday || isHoliday -> ResponsiveUi.dp(requireContext(), 2)
                    hasEntry -> ResponsiveUi.dp(requireContext(), 1)
                    else -> ResponsiveUi.dp(requireContext(), 1)
                }
                tv.setTextColor(
                    when {
                        isSelected -> selectedTextColor
                        isWeekend -> weekendTextColor
                        else -> onSurfaceColor
                    }
                )
                indicators.visibility = if (hasEntry && !isSelected) View.VISIBLE else View.GONE
                dot.visibility = View.VISIBLE
                dot.backgroundTintList = ColorStateList.valueOf(dotColor)
                nightDot.visibility = if (isNight) View.VISIBLE else View.GONE
                nightDot.backgroundTintList = ColorStateList.valueOf(nightColor)
                bonusDot.visibility = if (hasBonus) View.VISIBLE else View.GONE
                bonusDot.backgroundTintList = ColorStateList.valueOf(if (isHoliday) holidayColor else bonusColor)
                card.alpha = if (hasEntry || isSelected || isToday) 1f else 0.96f
            } ?: run {
                tv.text = ""
                tv.setTextColor(ColorUtils.setAlphaComponent(MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurface), 0))
                indicators.visibility = View.GONE
                card.setCardBackgroundColor(ColorUtils.setAlphaComponent(MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface), 0))
                card.strokeWidth = 0
                card.alpha = 0f
            }
            return v
        }
    }

    private fun isNightShift(entry: WorkEntry): Boolean =
        ShiftType.fromStored(entry.shiftType) == ShiftType.NIGHT

    private fun updateSelectionUi() {
        val primary = MaterialColors.getColor(bulkButton, androidx.appcompat.R.attr.colorPrimary)
        val onPrimary = MaterialColors.getColor(bulkButton, com.google.android.material.R.attr.colorOnPrimary)
        val error = MaterialColors.getColor(bulkButton, androidx.appcompat.R.attr.colorError)
        val onError = MaterialColors.getColor(bulkButton, com.google.android.material.R.attr.colorOnError)
        val scroll = view?.findViewById<ScrollView>(R.id.calendarScroll)
        if (selectedDates.isEmpty()) {
            selectionState.text = getString(R.string.calendar_hint)
            bulkButton.visibility = View.GONE
        } else {
            when (selectionMode) {
                SelectionMode.DELETE -> {
                    selectionState.text = getString(R.string.calendar_selected_entries, selectedDates.size)
                    bulkButton.text = getString(R.string.delete_selected_entries, selectedDates.size)
                    bulkButton.backgroundTintList = ColorStateList.valueOf(error)
                    bulkButton.setTextColor(onError)
                }
                else -> {
                    selectionState.text = getString(R.string.calendar_selected_days, selectedDates.size)
                    bulkButton.text = getString(R.string.add_selected_entries, selectedDates.size)
                    bulkButton.backgroundTintList = ColorStateList.valueOf(primary)
                    bulkButton.setTextColor(onPrimary)
                }
            }
            bulkButton.visibility = View.VISIBLE
            scroll?.post {
                scroll.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun calculateCellWidth(gridView: GridView): Int {
        val spacing = gridView.horizontalSpacing
        val availableWidth = if (gridView.width > 0) {
            gridView.width - gridView.paddingLeft - gridView.paddingRight - spacing * 6
        } else {
            resources.displayMetrics.widthPixels - responsiveProfile.screenPaddingPx * 2 - spacing * 6
        }
        return max(availableWidth / 7, responsiveProfile.calendarCellMinWidthPx)
    }

    companion object {
        private const val ARG_RESULT_PREFIX = "resultPrefix"
        const val REQUEST_DEFAULT = "calendar"
        const val REQUEST_JOB_DETAIL = "job_detail_calendar"

        fun dateResultKey(prefix: String = REQUEST_DEFAULT): String = "${prefix}_date"

        fun datesResultKey(prefix: String = REQUEST_DEFAULT): String = "${prefix}_dates"

        fun newInstance(jobId: Int, resultPrefix: String = REQUEST_DEFAULT) = CalendarDialogFragment().apply {
            arguments = Bundle().apply {
                putInt("jobId", jobId)
                putString(ARG_RESULT_PREFIX, resultPrefix)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        entriesJob?.cancel()
    }
}
