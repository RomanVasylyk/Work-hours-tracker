package com.example.worktr.ui

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.WorkEntry
import com.example.worktr.data.WorkEntryRepository
import com.example.worktr.databinding.FragmentAddEntryBinding
import com.example.worktr.util.DecimalInput
import com.example.worktr.viewmodel.AddEntryViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AddEntryFragment : Fragment() {
    private var _binding: FragmentAddEntryBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<AddEntryFragmentArgs>()
    private lateinit var viewModel: AddEntryViewModel

    private var currentEntry: WorkEntry? = null
    private var selectedDates: List<Long> = emptyList()

    private var dayLiveData: androidx.lifecycle.LiveData<WorkEntry?>? = null

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentAddEntryBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        val repo = WorkEntryRepository(DatabaseProvider.get(requireContext()).workEntryDao())
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>) =
                AddEntryViewModel(repo) as T
        })[AddEntryViewModel::class.java]

        DecimalInput.attach(binding.inputHours, 2)
        DecimalInput.attach(binding.inputBreak, 2)

        setFragmentResultListener("calendar_dates") { _, b ->
            selectedDates = b.getLongArray("dates")?.toList().orEmpty()
            updateSelectedDateLabel()
            if (selectedDates.size == 1) loadExisting(selectedDates.first()) else {
                currentEntry = null
                binding.buttonDeleteEntry.visibility = View.GONE
                clearErrors()
            }
        }

        binding.buttonSelectDate.setOnClickListener {
            CalendarDialogFragment.newInstance(args.jobId)
                .show(parentFragmentManager, "calDialog")
        }

        binding.buttonSaveEntry.setOnClickListener { saveEntry() }
        binding.buttonDeleteEntry.setOnClickListener { deleteEntry() }
    }

    private fun updateSelectedDateLabel() {
        if (selectedDates.isEmpty()) {
            binding.textSelectedDate.text = ""
            return
        }
        val zone = ZoneId.systemDefault()
        if (selectedDates.size == 1) {
            val z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(selectedDates.first()), zone)
            binding.textSelectedDate.text = z.toLocalDate().toString()
        } else {
            binding.textSelectedDate.text = getString(R.string.selected_days_count, selectedDates.size)
        }
    }

    private fun loadExisting(millis: Long) {
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
            .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = start + 86_399_999

        dayLiveData?.removeObservers(viewLifecycleOwner)
        dayLiveData = viewModel.getEntryForDay(args.jobId, start, end)

        dayLiveData!!.observe(viewLifecycleOwner) { e ->
            currentEntry = e
            if (e != null) {
                binding.inputHours.setText(e.hoursWorked.toString())
                binding.inputBreak.setText(e.breakHours.toString())
                val idx = resources.getStringArray(R.array.shift_types).indexOf(e.shiftType)
                if (idx >= 0) binding.spinnerShift.setSelection(idx)
                binding.checkHoliday.isChecked = e.isHoliday
                binding.buttonDeleteEntry.visibility = View.VISIBLE
            } else {
                binding.buttonDeleteEntry.visibility = View.GONE
            }
            clearErrors()
        }
    }

    private fun saveEntry() {
        clearErrors()

        if (selectedDates.isEmpty()) return

        val hoursText = binding.inputHours.text?.toString()?.trim().orEmpty()
        val breakText = binding.inputBreak.text?.toString()?.trim().orEmpty()

        var valid = true
        if (hoursText.isEmpty()) {
            binding.layoutHours.error = getString(R.string.required_field)
            valid = false
        }
        if (breakText.isEmpty()) {
            binding.layoutBreak.error = getString(R.string.required_field)
            valid = false
        }
        if (!valid) return

        val hours = hoursText.toDoubleOrNull() ?: 0.0
        val br = breakText.toDoubleOrNull() ?: 0.0

        if (br > hours) {
            binding.layoutBreak.error = getString(R.string.break_gt_hours)
            return
        }

        val shift = binding.spinnerShift.selectedItem?.toString().orEmpty()
        val hol = binding.checkHoliday.isChecked
        val zone = ZoneId.systemDefault()

        selectedDates.forEach { millis ->
            val start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
                .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val end = start + 86_399_999

            val entry = WorkEntry(
                entryId = 0,
                jobId = args.jobId,
                date = millis,
                hoursWorked = hours,
                breakHours = br,
                shiftType = shift,
                isHoliday = hol
            )

            viewModel.upsertForDay(args.jobId, start, end, entry)
        }

        findNavController().popBackStack()
    }

    private fun deleteEntry() {
        if (selectedDates.size != 1) return
        currentEntry?.let {
            viewModel.delete(it)
            findNavController().popBackStack()
        }
    }

    private fun clearErrors() {
        binding.layoutHours.error = null
        binding.layoutBreak.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
