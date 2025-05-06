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
    private var selectedMillis: Long? = null

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentAddEntryBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onViewCreated(v: View, s: Bundle?) {
        val repo = WorkEntryRepository(DatabaseProvider.get(requireContext()).workEntryDao())
        viewModel = ViewModelProvider(this, object: ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>) =
                AddEntryViewModel(repo) as T
        })[AddEntryViewModel::class.java]

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
        binding.buttonSaveEntry.setOnClickListener { saveEntry() }
        binding.buttonDeleteEntry.setOnClickListener { deleteEntry() }
    }

    private fun loadExisting(millis: Long) {
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
            .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = start + 86_399_999
        viewModel.getEntryForDay(args.jobId, start, end).observe(viewLifecycleOwner) { e ->
            currentEntry = e
            if (e != null) {
                binding.inputHours.setText(e.hoursWorked.toString())
                binding.inputBreak.setText(e.breakHours.toString())
                val idx = resources.getStringArray(R.array.shift_types).indexOf(e.shiftType)
                if (idx >= 0) binding.spinnerShift.setSelection(idx)
                binding.checkHoliday.isChecked = e.isHoliday
                binding.buttonDeleteEntry.visibility = View.VISIBLE
            } else {
                binding.inputHours.text = null
                binding.inputBreak.text = null
                binding.spinnerShift.setSelection(0)
                binding.checkHoliday.isChecked = false
                binding.buttonDeleteEntry.visibility = View.GONE
            }
        }
    }

    private fun saveEntry() {
        val millis = selectedMillis ?: return
        val hours = binding.inputHours.text.toString().toDoubleOrNull() ?: 0.0
        val br = binding.inputBreak.text.toString().toDoubleOrNull() ?: 0.0
        val shift = binding.spinnerShift.selectedItem.toString()
        val hol = binding.checkHoliday.isChecked
        val base = currentEntry ?: WorkEntry(
            jobId = args.jobId,
            date = millis,
            hoursWorked = 0.0,
            breakHours = 0.0,
            shiftType = "",
            isHoliday = false
        )
        val entry = base.copy(
            hoursWorked = hours,
            breakHours = br,
            shiftType = shift,
            isHoliday = hol
        )
        if (currentEntry == null) viewModel.insert(entry) else viewModel.update(entry)
        findNavController().popBackStack()
    }

    private fun deleteEntry() {
        currentEntry?.let {
            viewModel.delete(it)
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
