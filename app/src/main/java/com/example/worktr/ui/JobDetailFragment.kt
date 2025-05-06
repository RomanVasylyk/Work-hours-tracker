package com.example.worktr.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.JobRepository
import com.example.worktr.data.WorkEntryRepository
import com.example.worktr.databinding.FragmentJobDetailBinding
import com.example.worktr.util.ExcelExporter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.DayOfWeek

class JobDetailFragment : Fragment() {
    private var _binding: FragmentJobDetailBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<JobDetailFragmentArgs>()
    private lateinit var viewModel: com.example.worktr.viewmodel.JobDetailViewModel
    private lateinit var workRepository: WorkEntryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentJobDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = DatabaseProvider.get(requireContext())
        val jobRepository = JobRepository(db.jobDao())
        workRepository = WorkEntryRepository(db.workEntryDao())
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return com.example.worktr.viewmodel.JobDetailViewModel(jobRepository, args.jobId) as T
            }
        })[com.example.worktr.viewmodel.JobDetailViewModel::class.java]
        viewModel.job.observe(viewLifecycleOwner) { job -> binding.textJobName.text = job?.name ?: "" }

        val years = resources.getStringArray(R.array.years)
        val yearsAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.years, android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerYear.adapter = yearsAdapter
        val currentYear = LocalDate.now().year.toString()
        val yearIndex = years.indexOf(currentYear)
        if (yearIndex >= 0) binding.spinnerYear.setSelection(yearIndex)

        val monthsAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.months, android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerMonth.adapter = monthsAdapter
        binding.spinnerMonth.setSelection(LocalDate.now().monthValue - 1)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val month = binding.spinnerMonth.selectedItemPosition + 1
                val year = binding.spinnerYear.selectedItem.toString().toInt()
                loadStats(month, year)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        binding.spinnerYear.onItemSelectedListener = listener
        binding.spinnerMonth.onItemSelectedListener = listener

        binding.buttonAddEntry.setOnClickListener {
            findNavController().navigate(JobDetailFragmentDirections.actionJobDetailFragmentToAddEntryFragment(args.jobId))
        }
    }

    private fun loadStats(month: Int, year: Int) {
        val zone = ZoneId.systemDefault()
        val ym = YearMonth.of(year, month)
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = ym.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        lifecycleScope.launch {
            workRepository.getEntriesForPeriod(args.jobId, start, end).collectLatest { list ->
                var hours = 0.0
                var morning = 0
                var day = 0
                var night = 0
                val dates = mutableSetOf<LocalDate>()
                var holidays = 0
                val holidayDates = mutableSetOf<LocalDate>()
                var saturdays = 0
                var sundays = 0

                list.forEach {
                    hours += it.hoursWorked - it.breakHours

                    val type = it.shiftType.lowercase()
                    when {
                        type in listOf("morning",  "ранкова") -> morning++
                        type in listOf("day",      "денна")   -> day++
                        type in listOf("night",    "нічна")   -> night++
                    }

                    val date = java.time.Instant.ofEpochMilli(it.date)
                        .atZone(zone).toLocalDate()
                    if (dates.add(date)) {
                        when (date.dayOfWeek) {
                            DayOfWeek.SATURDAY -> saturdays++
                            DayOfWeek.SUNDAY   -> sundays++
                            else -> {}
                        }
                    }
                    if (it.isHoliday && holidayDates.add(date)) {
                        holidays++
                    }
                }

                binding.textMonth.text =
                    "${resources.getStringArray(R.array.months)[month - 1]} $year"
                binding.textHours.text =
                    getString(R.string.hours_worked_format, hours)
                binding.textDays.text =
                    getString(R.string.days_worked_format, dates.size)
                binding.textMorning.text =
                    getString(R.string.morning_shifts_format, morning)
                binding.textDay.text =
                    getString(R.string.day_shifts_format, day)
                binding.textNight.text =
                    getString(R.string.night_shifts_format, night)
                binding.textHolidays.text =
                    getString(R.string.holiday_days_format, holidays)
                binding.textSaturday.text =
                    getString(R.string.saturday_days_format, saturdays)
                binding.textSunday.text =
                    getString(R.string.sunday_days_format, sundays)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_job_detail, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> {
                val file: File = ExcelExporter(requireContext()).export(args.jobId)
                val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share)))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
