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
    private var loadEntryJob: Job? = null
    private var selectedBreakHours: Double = 0.0

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

        setFragmentResultListener("calendar_date") { _, b ->
            selectedMillis = b.getLong("date")
            val z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(selectedMillis!!), ZoneId.systemDefault())
            binding.textSelectedDate.text = z.toLocalDate().toString()
            loadExisting(selectedMillis!!)
        }

        binding.buttonSelectDate.setOnClickListener {
            CalendarDialogFragment.newInstance(args.jobId)
                .show(parentFragmentManager, "calDialog")
        }
        binding.buttonPickBreak.setOnClickListener { showBreakPicker() }
        binding.textBreakValue.setOnClickListener { showBreakPicker() }
        binding.buttonSaveEntry.setOnClickListener { saveEntry() }
        binding.buttonDeleteEntry.setOnClickListener { deleteEntry() }
        updateBreakSummary()
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

    private fun bindEntry(entry: WorkEntry?) {
        val shiftTypes = resources.getStringArray(R.array.shift_types)
        currentEntry = entry
        if (entry != null) {
            binding.inputHours.setText(entry.hoursWorked.toString())
            selectedBreakHours = entry.breakHours
            val selectedShift = shiftTypes.find { it.equals(entry.shiftType, ignoreCase = true) }
                ?: shiftTypes.firstOrNull().orEmpty()
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
        val millis = selectedMillis ?: return
        val hours = binding.inputHours.text.toString().toDoubleOrNull() ?: 0.0
        val br = selectedBreakHours
        val shift = binding.inputShiftType.text?.toString().orEmpty()
        val hol = binding.checkHoliday.isChecked
        viewLifecycleOwner.lifecycleScope.launch {
            val base = currentEntry ?: jobRepository.getJobById(args.jobId)?.let { job ->
                WorkEntry(
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
            } ?: return@launch

            val entry = base.copy(
                hoursWorked = hours,
                breakHours = br,
                shiftType = shift,
                isHoliday = hol
            )
            if (currentEntry == null) viewModel.insert(entry) else viewModel.update(entry)
            findNavController().popBackStack()
        }
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
}
