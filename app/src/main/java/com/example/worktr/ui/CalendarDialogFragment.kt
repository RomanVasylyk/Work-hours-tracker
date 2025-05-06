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
import com.example.worktr.data.WorkEntryRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.TextStyle
import java.util.*

class CalendarDialogFragment : DialogFragment() {
    private var currentMonth = YearMonth.now()
    private val days = mutableListOf<LocalDate?>()
    private val entries = mutableSetOf<LocalDate>()
    private lateinit var adapter: DayAdapter
    private val jobId get() = requireArguments().getInt("jobId")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.dialog_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val grid = view.findViewById<GridView>(R.id.calendarGridDialog)
        val spinnerYear = view.findViewById<Spinner>(R.id.spinnerYear)

        adapter = DayAdapter()
        grid.adapter = adapter

        val years = (currentMonth.year - 5..currentMonth.year + 5).map { it.toString() }
        spinnerYear.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, years).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerYear.setSelection(years.indexOf(currentMonth.year.toString()))
        spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val y = years[pos].toInt()
                currentMonth = YearMonth.of(y, currentMonth.month)
                render(view, grid)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        view.findViewById<ImageButton>(R.id.buttonPrevMonth).setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            spinnerYear.setSelection(years.indexOf(currentMonth.year.toString()))
            render(view, grid)
        }
        view.findViewById<ImageButton>(R.id.buttonNextMonth).setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            spinnerYear.setSelection(years.indexOf(currentMonth.year.toString()))
            render(view, grid)
        }

        grid.setOnItemClickListener { _, _, pos, _ ->
            days[pos]?.let { date ->
                setFragmentResult("calendar_date", Bundle().apply {
                    putLong("date", date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                })
                dismiss()
            }
        }
        render(view, grid)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun render(view: View, grid: GridView) {
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
        val repo = WorkEntryRepository(DatabaseProvider.get(requireContext()).workEntryDao())
        lifecycleScope.launch {
            repo.getEntriesForPeriod(jobId, start, end).collectLatest {
                entries.clear()
                it.forEach { e ->
                    entries.add(Instant.ofEpochMilli(e.date).atZone(zone).toLocalDate())
                }
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
                val color = if (entries.contains(d)) R.color.purple_200 else android.R.color.transparent
                v.setBackgroundColor(ContextCompat.getColor(requireContext(), color))
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
