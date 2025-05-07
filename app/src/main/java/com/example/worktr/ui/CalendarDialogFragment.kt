package com.example.worktr.ui

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.WorkEntry
import com.example.worktr.data.WorkEntryRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.TextStyle
import java.util.*

class CalendarDialogFragment : DialogFragment() {
    private var currentMonth = YearMonth.now()
    private val days = mutableListOf<LocalDate?>()
    private val entries = mutableSetOf<LocalDate>()
    private val selectedDates = mutableSetOf<LocalDate>()
    private lateinit var adapter: DayAdapter
    private lateinit var repo: WorkEntryRepository
    private lateinit var bulkButton: MaterialButton
    private val jobId get() = requireArguments().getInt("jobId")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.dialog_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = WorkEntryRepository(DatabaseProvider.get(requireContext()).workEntryDao())
        adapter = DayAdapter()
        bulkButton = view.findViewById(R.id.buttonBulkAdd)
        bulkButton.setOnClickListener { showBulkDialog() }

        // Year spinner setup
        val spinnerYear = view.findViewById<Spinner>(R.id.spinnerYear)
        ArrayAdapter.createFromResource(
            requireContext(), R.array.years, android.R.layout.simple_spinner_item
        ).also { arr ->
            arr.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerYear.adapter = arr
        }
        spinnerYear.setSelection(
            resources.getStringArray(R.array.years).indexOf(currentMonth.year.toString())
        )
        spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                currentMonth = YearMonth.of(
                    resources.getStringArray(R.array.years)[pos].toInt(),
                    currentMonth.month
                )
                render(view)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Prev/Next buttons
        val btnPrev = view.findViewById<ImageButton>(R.id.buttonPrevMonth)
        val btnNext = view.findViewById<ImageButton>(R.id.buttonNextMonth)
        btnPrev.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            spinnerYear.setSelection(
                resources.getStringArray(R.array.years).indexOf(currentMonth.year.toString())
            )
            render(view)
        }
        btnNext.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            spinnerYear.setSelection(
                resources.getStringArray(R.array.years).indexOf(currentMonth.year.toString())
            )
            render(view)
        }

        // Calendar grid
        val grid = view.findViewById<GridView>(R.id.calendarGridDialog)
        grid.adapter = adapter
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val date = days[pos] ?: return@OnItemClickListener
            if (selectedDates.isNotEmpty()) {
                // If in bulk mode, allow toggling only non-existing days
                if (entries.contains(date)) return@OnItemClickListener
                if (selectedDates.contains(date)) selectedDates.remove(date) else selectedDates.add(date)
                bulkButton.visibility = if (selectedDates.isEmpty()) View.GONE else View.VISIBLE
                adapter.notifyDataSetChanged()
            } else {
                // Single selection
                setFragmentResult("calendar_date", Bundle().apply {
                    putLong("date", date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                })
                dismiss()
            }
        }
        grid.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val date = days[pos] ?: return@OnItemLongClickListener true
            // Prevent selecting days that already have entries
            if (entries.contains(date)) return@OnItemLongClickListener true
            if (selectedDates.contains(date)) selectedDates.remove(date) else selectedDates.add(date)
            bulkButton.visibility = if (selectedDates.isEmpty()) View.GONE else View.VISIBLE
            adapter.notifyDataSetChanged()
            true
        }

        render(view)
    }

    private fun showBulkDialog() {
        val dates = selectedDates.toList()
        selectedDates.clear()
        bulkButton.visibility = View.GONE

        val dlgView = layoutInflater.inflate(R.layout.dialog_bulk_entry, null)
        val inputH = dlgView.findViewById<EditText>(R.id.inputHours)
        val inputB = dlgView.findViewById<EditText>(R.id.inputBreak)
        val spin = dlgView.findViewById<Spinner>(R.id.spinnerShift)
        val chk = dlgView.findViewById<CheckBox>(R.id.checkHoliday)
        val save = dlgView.findViewById<MaterialButton>(R.id.buttonSaveBulk)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dlgView).create()
        save.setOnClickListener {
            val h = inputH.text.toString().toDoubleOrNull() ?: 0.0
            val b = inputB.text.toString().toDoubleOrNull() ?: 0.0
            val sft = spin.selectedItem.toString()
            val hol = chk.isChecked
            requireActivity().lifecycleScope.launch(Dispatchers.IO) {
                dates.forEach { d ->
                    val millis = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    repo.insert(WorkEntry(jobId = jobId, date = millis, hoursWorked = h, breakHours = b, shiftType = sft, isHoliday = hol))
                }
            }
            dialog.dismiss()
            dismiss()
        }
        dialog.show()
    }

    private fun render(view: View) {
        val zone = ZoneId.systemDefault()
        val first = currentMonth.atDay(1)
        val offset = first.dayOfWeek.ordinal
        days.clear()
        repeat(offset) { days.add(null) }
        (1..currentMonth.lengthOfMonth()).map { currentMonth.atDay(it) }.forEach { days.add(it) }
        loadEntries(zone)
        adapter.notifyDataSetChanged()
        view.findViewById<TextView>(R.id.textMonthYear).text =
            currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + currentMonth.year
    }

    private fun loadEntries(zone: ZoneId) {
        val start = currentMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        lifecycleScope.launch {
            repo.getEntriesForPeriod(jobId, start, end).collectLatest { list ->
                entries.clear()
                list.forEach { e -> entries.add(Instant.ofEpochMilli(e.date).atZone(zone).toLocalDate()) }
                adapter.notifyDataSetChanged()
            }
        }
    }

    inner class DayAdapter : BaseAdapter() {
        override fun getCount() = days.size
        override fun getItem(pos: Int) = days[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
            val v = cv ?: layoutInflater.inflate(R.layout.item_day_cell, parent, false)
            val tv = v.findViewById<TextView>(R.id.dayNumber)
            days[pos]?.let { d ->
                tv.text = d.dayOfMonth.toString()
                val bg = when {
                    selectedDates.contains(d) -> R.color.teal_200
                    entries.contains(d) -> R.color.purple_200
                    else -> android.R.color.transparent
                }
                v.setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
            } ?: run { tv.text = ""; v.setBackgroundColor(0) }
            return v
        }
    }

    companion object {
        fun newInstance(jobId: Int) = CalendarDialogFragment().apply {
            arguments = Bundle().apply { putInt("jobId", jobId) }
        }
    }
}