package com.example.worktr.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.WorkEntry
import com.example.worktr.data.WorkEntryRepository
import com.example.worktr.databinding.FragmentAddEntryBinding
import com.example.worktr.viewmodel.AddEntryViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AddEntryFragment : Fragment() {
    private var _binding: FragmentAddEntryBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<AddEntryFragmentArgs>()
    private lateinit var viewModel: AddEntryViewModel
    private var selectedDate: Long? = null
    private var currentEntry: WorkEntry? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = WorkEntryRepository(DatabaseProvider.get(requireContext()).workEntryDao())
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>): T =
                AddEntryViewModel(repo) as T
        })[AddEntryViewModel::class.java]

        binding.buttonSelectDate.setOnClickListener { showDatePicker() }
        binding.buttonSaveEntry.setOnClickListener { saveEntry() }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_date)).build()
        picker.addOnPositiveButtonClickListener { millis ->
            selectedDate = millis
            val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            binding.textSelectedDate.text = zdt.toLocalDate().toString()
            loadExistingEntry(millis)
        }
        picker.show(parentFragmentManager, "datePicker")
    }

    private fun loadExistingEntry(millis: Long) {
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
            .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = start + 86_399_999
        viewModel.getEntryForDay(args.jobId, start, end).observe(viewLifecycleOwner) { entry ->
            currentEntry = entry
            if (entry != null) {
                binding.inputHours.setText(entry.hoursWorked.toString())
                binding.inputBreak.setText(entry.breakHours.toString())
                val index = resources.getStringArray(R.array.shift_types).indexOf(entry.shiftType)
                if (index >= 0) binding.spinnerShift.setSelection(index)
                binding.checkHoliday.isChecked = entry.isHoliday
            } else {
                binding.inputHours.text = null
                binding.inputBreak.text = null
                binding.spinnerShift.setSelection(0)
                binding.checkHoliday.isChecked = false
            }
        }
    }

    private fun saveEntry() {
        val date = selectedDate ?: return
        val hours = binding.inputHours.text.toString().toDoubleOrNull() ?: 0.0
        val breakHours = binding.inputBreak.text.toString().toDoubleOrNull() ?: 0.0
        val shiftType = binding.spinnerShift.selectedItem?.toString() ?: ""
        val holiday = binding.checkHoliday.isChecked
        val base = currentEntry ?: WorkEntry(
            jobId = args.jobId,
            date = date,
            hoursWorked = 0.0,
            breakHours = 0.0,
            shiftType = "",
            isHoliday = false
        )
        val entry = base.copy(
            hoursWorked = hours,
            breakHours = breakHours,
            shiftType = shiftType,
            isHoliday = holiday
        )


        if (currentEntry == null) viewModel.insert(entry) else viewModel.update(entry)
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
