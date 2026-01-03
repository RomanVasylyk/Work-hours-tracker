package com.example.worktr.ui

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.WorkEntryRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.TextStyle
import java.util.Locale

class CalendarDialogFragment : DialogFragment() {
    private var currentMonth = YearMonth.now()
    private val selectedDates = mutableSetOf<LocalDate>()
    private val entries = mutableSetOf<LocalDate>()
    private lateinit var repo: WorkEntryRepository
    private lateinit var actionButton: MaterialButton
    private lateinit var adapter: DayListAdapter
    private var entriesJob: Job? = null
    private val jobId get() = requireArguments().getInt("jobId")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.dialog_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = WorkEntryRepository(DatabaseProvider.get(requireContext()).workEntryDao())

        val spinnerYear = view.findViewById<Spinner>(R.id.spinnerYear)
        ArrayAdapter.createFromResource(
            requireContext(), R.array.years, android.R.layout.simple_spinner_item
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerYear.adapter = it
        }
        spinnerYear.setSelection(resources.getStringArray(R.array.years).indexOf(currentMonth.year.toString()))
        spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                currentMonth = YearMonth.of(resources.getStringArray(R.array.years)[pos].toInt(), currentMonth.month)
                render(view)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        view.findViewById<ImageButton>(R.id.buttonPrevMonth).setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            spinnerYear.setSelection(resources.getStringArray(R.array.years).indexOf(currentMonth.year.toString()))
            render(view)
        }

        view.findViewById<ImageButton>(R.id.buttonNextMonth).setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            spinnerYear.setSelection(resources.getStringArray(R.array.years).indexOf(currentMonth.year.toString()))
            render(view)
        }

        actionButton = view.findViewById(R.id.buttonBulkAdd)
        actionButton.text = getString(R.string.next)
        actionButton.isVisible = false
        actionButton.setOnClickListener { submitSelectedAndClose() }

        adapter = DayListAdapter(
            onClick = { date ->
                if (selectedDates.isNotEmpty()) {
                    if (entries.contains(date)) return@DayListAdapter
                    toggleSelected(date)
                } else {
                    submitAndClose(listOf(date))
                }
            },
            onLongClick = { date ->
                if (entries.contains(date)) return@DayListAdapter
                toggleSelected(date)
            }
        )

        val rv = view.findViewById<RecyclerView>(R.id.calendarRecycler)
        rv.layoutManager = GridLayoutManager(requireContext(), 7)
        rv.adapter = adapter
        rv.itemAnimator = null
        rv.isNestedScrollingEnabled = false


        render(view)
    }

    private fun submitAndClose(dates: List<LocalDate>) {
        val millis = dates.sorted().map {
            it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.toLongArray()

        setFragmentResult("calendar_dates", Bundle().apply {
            putLongArray("dates", millis)
        })
        dismiss()
    }

    private fun submitSelectedAndClose() {
        if (selectedDates.isEmpty()) return
        submitAndClose(selectedDates.toList())
    }

    private fun toggleSelected(date: LocalDate) {
        if (selectedDates.contains(date)) selectedDates.remove(date) else selectedDates.add(date)
        actionButton.isVisible = selectedDates.isNotEmpty()
        adapter.setState(entries, selectedDates, currentMonth)
    }

    private fun render(view: View) {
        view.findViewById<TextView>(R.id.textMonthYear).text =
            currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + currentMonth.year

        selectedDates.clear()
        actionButton.isVisible = false

        adapter.submitList(buildDays(currentMonth))
        adapter.setState(entries, selectedDates, currentMonth)

        loadEntries()
    }

    private fun buildDays(month: YearMonth): List<DayCell> {
        val first = month.atDay(1)
        val shift = (first.dayOfWeek.value + 6) % 7
        val list = mutableListOf<DayCell>()
        repeat(shift) { list.add(DayCell.Empty) }
        for (d in 1..month.lengthOfMonth()) list.add(DayCell.Date(month.atDay(d)))
        while (list.size % 7 != 0) list.add(DayCell.Empty)
        return list
    }

    private fun loadEntries() {
        entriesJob?.cancel()
        val zone = ZoneId.systemDefault()
        val start = currentMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        entriesJob = viewLifecycleOwner.lifecycleScope.launch {
            repo.getEntriesForPeriod(jobId, start, end).collectLatest { list ->
                entries.clear()
                list.forEach { e ->
                    entries.add(Instant.ofEpochMilli(e.date).atZone(zone).toLocalDate())
                }
                adapter.setState(entries, selectedDates, currentMonth)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        entriesJob?.cancel()
    }

    sealed class DayCell {
        data object Empty : DayCell()
        data class Date(val value: LocalDate) : DayCell()
    }

    class DayListAdapter(
        private val onClick: (LocalDate) -> Unit,
        private val onLongClick: (LocalDate) -> Unit
    ) : ListAdapter<DayCell, DayListAdapter.VH>(Diff) {

        private var entries: Set<LocalDate> = emptySet()
        private var selected: Set<LocalDate> = emptySet()
        private var month: YearMonth = YearMonth.now()

        fun setState(entries: Set<LocalDate>, selected: Set<LocalDate>, month: YearMonth) {
            this.entries = entries.toSet()
            this.selected = selected.toSet()
            this.month = month
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int) = if (getItem(position) is DayCell.Empty) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_day_cell, parent, false)
            return VH(v, onClick, onLongClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position), entries, selected, month)
        }

        class VH(
            itemView: View,
            private val onClick: (LocalDate) -> Unit,
            private val onLongClick: (LocalDate) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {

            private val card = itemView.findViewById<MaterialCardView>(R.id.dayCard)
            private val tv = itemView.findViewById<TextView>(R.id.dayNumber)
            private val dot = itemView.findViewById<View>(R.id.dayDot)

            fun bind(cell: DayCell, entries: Set<LocalDate>, selected: Set<LocalDate>, month: YearMonth) {
                if (cell is DayCell.Empty) {
                    tv.text = ""
                    dot.visibility = View.GONE
                    card.setCardBackgroundColor(0x00000000)
                    card.strokeWidth = 0
                    itemView.setOnClickListener(null)
                    itemView.setOnLongClickListener(null)
                    return
                }

                val date = (cell as DayCell.Date).value
                tv.text = date.dayOfMonth.toString()

                val ctx = itemView.context
                val isInMonth = YearMonth.from(date) == month
                val isToday = date == LocalDate.now()
                val hasEntry = entries.contains(date)
                val isSelected = selected.contains(date)

                dot.visibility = if (hasEntry) View.VISIBLE else View.GONE

                val surface = com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurface)
                val primary = com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimary)
                val primaryContainer = com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimaryContainer)
                val onPrimaryContainer = com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnPrimaryContainer)
                val onSurface = com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurface)
                val onSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)

                when {
                    isSelected -> {
                        card.setCardBackgroundColor(primaryContainer)
                        tv.setTextColor(onPrimaryContainer)
                        card.strokeWidth = 0
                    }
                    isToday -> {
                        card.setCardBackgroundColor(surface)
                        tv.setTextColor(onSurface)
                        card.strokeWidth = (ctx.resources.displayMetrics.density * 2).toInt()
                        card.setStrokeColor(primary)
                    }
                    else -> {
                        card.setCardBackgroundColor(surface)
                        tv.setTextColor(if (isInMonth) onSurface else onSurfaceVariant)
                        card.strokeWidth = 0
                    }
                }

                itemView.setOnClickListener { onClick(date) }
                itemView.setOnLongClickListener { onLongClick(date); true }
            }
        }

        companion object {
            val Diff = object : DiffUtil.ItemCallback<DayCell>() {
                override fun areItemsTheSame(oldItem: DayCell, newItem: DayCell) = oldItem == newItem
                override fun areContentsTheSame(oldItem: DayCell, newItem: DayCell) = oldItem == newItem
            }
        }
    }

    companion object {
        fun newInstance(jobId: Int) = CalendarDialogFragment().apply {
            arguments = Bundle().apply { putInt("jobId", jobId) }
        }
    }
}
