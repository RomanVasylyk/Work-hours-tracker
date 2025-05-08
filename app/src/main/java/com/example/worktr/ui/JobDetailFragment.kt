package com.example.worktr.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
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
import com.example.worktr.viewmodel.JobDetailViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.Instant

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
            override fun <T : ViewModel> create(modelClass: Class<T>) =
                JobDetailViewModel(jobRepository, args.jobId) as T
        })[JobDetailViewModel::class.java]

        viewModel.job.observe(viewLifecycleOwner) { job ->
            binding.textJobName.text = job?.name ?: ""
        }

        ArrayAdapter.createFromResource(
            requireContext(), R.array.years, android.R.layout.simple_spinner_item
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerYear.adapter = it
        }
        binding.spinnerYear.setSelection(
            resources.getStringArray(R.array.years)
                .indexOf(LocalDate.now().year.toString())
        )
        ArrayAdapter.createFromResource(
            requireContext(), R.array.months, android.R.layout.simple_spinner_item
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerMonth.adapter = it
        }
        binding.spinnerMonth.setSelection(LocalDate.now().monthValue - 1)

        val refreshStats: () -> Unit = {
            val m = binding.spinnerMonth.selectedItemPosition + 1
            val y = binding.spinnerYear.selectedItem.toString().toInt()
            loadStats(m, y)
        }

        binding.spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) = refreshStats()
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
        binding.spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) = refreshStats()
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        binding.buttonAddEntry.setOnClickListener {
            findNavController().navigate(
                JobDetailFragmentDirections
                    .actionJobDetailFragmentToAddEntryFragment(args.jobId)
            )
        }
        binding.buttonStats.setOnClickListener {
            findNavController().navigate(
                JobDetailFragmentDirections
                    .actionJobDetailFragmentToStatsFragment(args.jobId)
            )
        }
    }

    private fun loadStats(month: Int, year: Int) {
        val zone = ZoneId.systemDefault()
        val ym = YearMonth.of(year, month)
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = ym.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            workRepository.getEntriesForPeriod(args.jobId, start, end)
                .collectLatest { list ->
                    val job = viewModel.job.value ?: return@collectLatest

                    var hours = 0.0
                    var morning = 0; var dayCount = 0; var night = 0
                    var baseSalary = 0.0

                    var bonusNight = 0.0
                    var bonusSat = 0.0
                    var bonusSun = 0.0
                    var bonusHol = 0.0

                    val dates = mutableSetOf<LocalDate>()
                    var holidays = 0; var saturdays = 0; var sundays = 0

                    list.forEach { entry ->
                        val h = entry.hoursWorked - entry.breakHours
                        hours += h

                        when (entry.shiftType.lowercase()) {
                            "ранкова","morning" -> morning++
                            "денна","day"       -> dayCount++
                            "нічна","night"     -> night++
                        }

                        val date = Instant.ofEpochMilli(entry.date).atZone(zone).toLocalDate()
                        if (dates.add(date)) {
                            if (date.dayOfWeek == DayOfWeek.SATURDAY) saturdays++
                            if (date.dayOfWeek == DayOfWeek.SUNDAY)   sundays++
                        }
                        if (entry.isHoliday && dates.add(date)) holidays++

                        baseSalary += h * job.hourlyRate

                        if (entry.shiftType.lowercase() in listOf("нічна","night")) {
                            bonusNight += h * job.nightBonus
                        }
                        if (date.dayOfWeek == DayOfWeek.SATURDAY) {
                            bonusSat += h * job.saturdayBonus
                        }
                        if (date.dayOfWeek == DayOfWeek.SUNDAY) {
                            bonusSun += h * job.sundayBonus
                        }
                        if (entry.isHoliday) {
                            bonusHol += h * job.holidayBonus
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
                        getString(R.string.day_shifts_format, dayCount)
                    binding.textNight.text =
                        getString(R.string.night_shifts_format, night)
                    binding.textHolidays.text =
                        getString(R.string.holiday_days_format, holidays)
                    binding.textSaturday.text =
                        getString(R.string.saturday_days_format, saturdays)
                    binding.textSunday.text =
                        getString(R.string.sunday_days_format, sundays)
                    binding.textNightBonus.text =
                        getString(R.string.night_bonus_total, bonusNight)
                    binding.textSaturdayBonus.text =
                        getString(R.string.saturday_bonus_total, bonusSat)
                    binding.textSundayBonus.text =
                        getString(R.string.sunday_bonus_total, bonusSun)
                    binding.textHolidayBonus.text =
                        getString(R.string.holiday_bonus_total, bonusHol)
                    binding.textSalary.text =
                        getString(R.string.salary_format, baseSalary + bonusNight + bonusSat + bonusSun + bonusHol)
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
