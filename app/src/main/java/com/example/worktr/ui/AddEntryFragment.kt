package com.example.worktr.ui

import android.os.Bundle
import android.view.Gravity
import android.view.*
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.JobRepository
import com.example.worktr.data.WorkEntry
import com.example.worktr.data.WorkEntryRepository
import com.example.worktr.databinding.FragmentAddEntryBinding
import com.example.worktr.ui.picker.DropdownUi
import com.example.worktr.ui.picker.DurationPicker
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.ShiftType
import com.example.worktr.viewmodel.AddEntryViewModel
import com.google.android.material.transition.platform.MaterialSharedAxis
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AddEntryFragment : Fragment() {
    private var _binding: FragmentAddEntryBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<AddEntryFragmentArgs>()
    private lateinit var viewModel: AddEntryViewModel
    private lateinit var jobRepository: JobRepository
    private var currentEntry: WorkEntry? = null
    private var selectedMillis: Long? = null
    private var selectedDateMillis: LongArray = longArrayOf()
    private var loadEntryJob: Job? = null
    private var selectedBreakHours: Double = 0.0
    private var shiftStartMinutes: Int? = null
    private var shiftEndMinutes: Int? = null

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentAddEntryBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        val db = DatabaseProvider.get(requireContext())
        val repo = WorkEntryRepository(db.workEntryDao())
        jobRepository = JobRepository(db.jobDao())
        viewModel = ViewModelProvider(this, object: ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>) =
                AddEntryViewModel(repo) as T
        })[AddEntryViewModel::class.java]
        applyResponsiveLayout()
        setupShiftDropdown()

        setFragmentResultListener(CalendarDialogFragment.dateResultKey()) { _, b ->
            selectDates(longArrayOf(b.getLong("date")))
        }
        setFragmentResultListener(CalendarDialogFragment.datesResultKey()) { _, b ->
            selectDates(b.getLongArray("dates") ?: longArrayOf())
        }

        binding.buttonSelectDate.setOnClickListener {
            CalendarDialogFragment.newInstance(args.jobId)
                .show(parentFragmentManager, "calDialog")
        }
        binding.buttonShiftStart.setOnClickListener { showShiftTimePicker(isStart = true) }
        binding.buttonShiftEnd.setOnClickListener { showShiftTimePicker(isStart = false) }
        updateShiftTimeButtons()
        binding.buttonPickBreak.setOnClickListener { showBreakPicker() }
        binding.textBreakValue.setOnClickListener { showBreakPicker() }
        binding.buttonSaveEntry.setOnClickListener { saveEntry() }
        binding.buttonDeleteEntry.setOnClickListener { deleteEntry() }
        updateBreakSummary()

        findNavController()
            .previousBackStackEntry
            ?.savedStateHandle
            ?.remove<LongArray>(ENTRY_DATES_KEY)
            ?.takeIf { it.isNotEmpty() }
            ?.let { selectDates(it) }
    }

    private fun applyResponsiveLayout() {
        val profile = ResponsiveUi.profile(requireContext())
        ResponsiveUi.applyOuterPadding(binding.addEntryScroll, profile)
        ResponsiveUi.applyContentPadding(binding.addEntryContent, profile)
        binding.addEntryContent.gravity = if (profile.isCompact) Gravity.TOP else Gravity.CENTER_HORIZONTAL
        binding.buttonSelectDate.layoutParams = binding.buttonSelectDate.layoutParams.apply {
            width = if (profile.isCompact) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        }
        binding.buttonPickBreak.layoutParams = binding.buttonPickBreak.layoutParams.apply {
            width = if (profile.isCompact) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    private fun loadExisting(millis: Long) {
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
            .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = start + 86_399_999
        loadEntryJob?.cancel()
        loadEntryJob = viewLifecycleOwner.lifecycleScope.launch {
            bindEntry(viewModel.getEntryForDay(args.jobId, start, end))
        }
    }

    private fun selectDates(rawDates: LongArray) {
        val dates = rawDates.distinct().sorted().toLongArray()
        if (dates.isEmpty()) return
        selectedDateMillis = dates
        selectedMillis = dates.first()
        if (dates.size == 1) {
            val z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dates.first()), ZoneId.systemDefault())
            binding.textSelectedDate.text = z.toLocalDate().toString()
            loadExisting(dates.first())
        } else {
            binding.textSelectedDate.text = getString(R.string.add_entry_selected_dates, dates.size)
            bindEntry(null)
        }
    }

    private fun bindEntry(entry: WorkEntry?) {
        val shiftTypes = resources.getStringArray(R.array.shift_types)
        currentEntry = entry
        shiftStartMinutes = null
        shiftEndMinutes = null
        updateShiftTimeButtons()
        if (entry != null) {
            binding.inputHours.setText(entry.hoursWorked.toString())
            selectedBreakHours = entry.breakHours
            val selectedShift = shiftTypes.getOrElse(ShiftType.fromStored(entry.shiftType).labelIndex) {
                shiftTypes.firstOrNull().orEmpty()
            }
            binding.inputShiftType.setText(selectedShift, false)
            binding.checkHoliday.isChecked = entry.isHoliday
            binding.buttonDeleteEntry.visibility = View.VISIBLE
        } else {
            binding.inputHours.text = null
            selectedBreakHours = 0.0
            binding.inputShiftType.setText(shiftTypes.firstOrNull().orEmpty(), false)
            binding.checkHoliday.isChecked = false
            binding.buttonDeleteEntry.visibility = View.GONE
        }
        updateBreakSummary()
    }

    private fun saveEntry() {
        val dates = selectedDateMillis.takeIf { it.isNotEmpty() }
            ?: selectedMillis?.let { longArrayOf(it) }
            ?: run {
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    R.string.select_date,
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show()
                return
            }
        binding.inputHours.error = null
        val hours = binding.inputHours.text.toString().replace(',', '.').toDoubleOrNull()
        if (hours == null || hours <= 0.0) {
            binding.inputHours.error = getString(R.string.validation_positive_number)
            return
        }
        val br = selectedBreakHours
        if (br > hours) {
            binding.inputHours.error = getString(R.string.validation_break_too_large)
            return
        }
        val shift = ShiftType.fromStored(binding.inputShiftType.text?.toString()).code
        val hol = binding.checkHoliday.isChecked
        viewLifecycleOwner.lifecycleScope.launch {
            val job = jobRepository.getJobById(args.jobId) ?: return@launch
            dates.forEachIndexed { index, millis ->
                val base = currentEntry?.takeIf { dates.size == 1 && index == 0 } ?: WorkEntry(
                    jobId = args.jobId,
                    date = millis,
                    hoursWorked = 0.0,
                    breakHours = 0.0,
                    shiftType = "",
                    isHoliday = false,
                    hourlyRate = job.hourlyRate,
                    nightBonus = job.nightBonus,
                    saturdayBonus = job.saturdayBonus,
                    sundayBonus = job.sundayBonus,
                    holidayBonus = job.holidayBonus
                )

                val entry = base.copy(
                    date = millis,
                    hoursWorked = hours,
                    breakHours = br,
                    shiftType = shift,
                    isHoliday = hol
                )
                if (dates.size == 1 && currentEntry != null) {
                    viewModel.update(entry)
                } else {
                    viewModel.insert(entry)
                }
            }
            findNavController().popBackStack()
        }
    }

    private fun showShiftTimePicker(isStart: Boolean) {
        val current = (if (isStart) shiftStartMinutes else shiftEndMinutes) ?: (6 * 60)
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .setTitleText(if (isStart) R.string.shift_time_start_title else R.string.shift_time_end_title)
            .setHour(current / 60)
            .setMinute(current % 60)
            .build()
        picker.addOnPositiveButtonClickListener {
            val minutes = picker.hour * 60 + picker.minute
            if (isStart) shiftStartMinutes = minutes else shiftEndMinutes = minutes
            updateShiftTimeButtons()
            applyShiftTimes()
        }
        picker.show(parentFragmentManager, if (isStart) "shiftStartPicker" else "shiftEndPicker")
    }

    private fun updateShiftTimeButtons() {
        binding.buttonShiftStart.text =
            getString(R.string.shift_time_start, shiftStartMinutes?.let(::formatMinutes) ?: "—")
        binding.buttonShiftEnd.text =
            getString(R.string.shift_time_end, shiftEndMinutes?.let(::formatMinutes) ?: "—")
    }

    private fun formatMinutes(minutes: Int): String =
        String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

    /**
     * When both times are set, fill the hours field and pick the shift type:
     * anything touching the 22:00–06:00 window is a night shift, otherwise
     * starts before 10:00 count as morning and the rest as day shifts.
     */
    private fun applyShiftTimes() {
        val start = shiftStartMinutes ?: return
        val rawEnd = shiftEndMinutes ?: return
        val end = if (rawEnd <= start) rawEnd + MINUTES_PER_DAY else rawEnd
        val durationHours = (end - start) / 60.0
        binding.inputHours.setText(formatHours(durationHours))

        val touchesNight =
            overlaps(start, end, 22 * 60, MINUTES_PER_DAY) ||
                overlaps(start, end, 0, 6 * 60) ||
                overlaps(start, end, MINUTES_PER_DAY, MINUTES_PER_DAY + 6 * 60)
        val shiftType = when {
            touchesNight -> ShiftType.NIGHT
            start < 10 * 60 -> ShiftType.MORNING
            else -> ShiftType.DAY
        }
        val shiftTypes = resources.getStringArray(R.array.shift_types)
        binding.inputShiftType.setText(shiftTypes.getOrElse(shiftType.labelIndex) { shiftTypes.first() }, false)
    }

    private fun overlaps(start: Int, end: Int, rangeStart: Int, rangeEnd: Int): Boolean =
        start < rangeEnd && end > rangeStart

    private fun formatHours(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }

    private fun showBreakPicker() {
        DurationPicker.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.break_picker_title),
            initialHours = selectedBreakHours,
            tag = "addEntryBreakPicker"
        ) { hours ->
            selectedBreakHours = hours
            updateBreakSummary()
        }
    }

    private fun updateBreakSummary() {
        binding.textBreakValue.text = DurationPicker.format(requireContext(), selectedBreakHours)
    }

    private fun setupShiftDropdown() {
        val shiftTypes = resources.getStringArray(R.array.shift_types).toList()
        binding.inputShiftType.setAdapter(DropdownUi.adapter(requireContext(), shiftTypes))
        DropdownUi.attach(binding.inputShiftType) {
            shiftTypes.indexOf(binding.inputShiftType.text?.toString().orEmpty()).takeIf { it >= 0 }
        }
        if (binding.inputShiftType.text.isNullOrBlank()) {
            binding.inputShiftType.setText(shiftTypes.firstOrNull().orEmpty(), false)
        }
    }

    private fun deleteEntry() {
        currentEntry?.let { entry ->
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.delete(entry)
                findNavController().popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadEntryJob?.cancel()
        _binding = null
    }

    companion object {
        const val ENTRY_DATES_KEY = "entry_dates"
        private const val MINUTES_PER_DAY = 24 * 60
    }
}
