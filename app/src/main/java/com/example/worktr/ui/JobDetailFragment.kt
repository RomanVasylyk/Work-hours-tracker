package com.example.worktr.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.JobRepository
import com.example.worktr.data.WorkEntryRepository
import com.example.worktr.databinding.FragmentJobDetailBinding
import com.example.worktr.ui.picker.DropdownUi
import com.example.worktr.ui.picker.DynamicYearSpinner
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.CsvImporter
import com.example.worktr.util.ExcelExporter
import com.example.worktr.viewmodel.JobDetailViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialSharedAxis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class JobDetailFragment : Fragment() {
    private var _binding: FragmentJobDetailBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<JobDetailFragmentArgs>()
    private lateinit var viewModel: com.example.worktr.viewmodel.JobDetailViewModel
    private lateinit var workRepository: WorkEntryRepository
    private lateinit var yearSpinner: DynamicYearSpinner
    private lateinit var monthLabels: List<String>
    private var statsJob: Job? = null
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            importIntoCurrentJob(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
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
        applyResponsiveLayout()

        viewModel.job.observe(viewLifecycleOwner) { job ->
            binding.textJobName.text = job?.name ?: ""
        }

        val refreshStats: () -> Unit = {
            val m = monthLabels.indexOf(binding.inputMonth.text?.toString().orEmpty()).let { index ->
                if (index >= 0) index + 1 else LocalDate.now().monthValue
            }
            val y = yearSpinner.getSelectedYear() ?: LocalDate.now().year
            loadStats(m, y)
        }

        yearSpinner = DynamicYearSpinner(
            context = requireContext(),
            input = binding.inputYear,
            initialYear = LocalDate.now().year
        ) {
            refreshStats()
        }
        monthLabels = resources.getStringArray(R.array.months).toList()
        binding.inputMonth.setAdapter(DropdownUi.adapter(requireContext(), monthLabels))
        binding.inputMonth.setText(monthLabels[LocalDate.now().monthValue - 1], false)
        binding.inputMonth.setOnItemClickListener { _, _, _, _ -> refreshStats() }
        DropdownUi.attach(binding.inputMonth) {
            monthLabels.indexOf(binding.inputMonth.text?.toString().orEmpty()).takeIf { it >= 0 }
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

        refreshStats()
    }

    private fun applyResponsiveLayout() {
        val profile = ResponsiveUi.profile(requireContext())
        ResponsiveUi.applyOuterPadding(binding.jobDetailScroll, profile)
        ResponsiveUi.applyContentPadding(binding.jobDetailContent, profile)
        ResponsiveUi.setLinearOrientation(binding.layoutJobFilters, profile.isCompact)
        val stackHeroActions = profile.isCompact || profile.isMedium
        ResponsiveUi.setLinearOrientation(binding.layoutHeroActions, stackHeroActions)
        binding.gridPrimaryStats.columnCount = if (profile.isCompact) 1 else 2
        binding.gridBonusStats.columnCount = if (profile.isCompact) 1 else 2

        val yearParams = binding.layoutYearField.layoutParams as LinearLayout.LayoutParams
        val monthParams = binding.layoutMonthField.layoutParams as LinearLayout.LayoutParams
        val addParams = binding.buttonAddEntry.layoutParams as LinearLayout.LayoutParams
        val statsParams = binding.buttonStats.layoutParams as LinearLayout.LayoutParams
        if (profile.isCompact) {
            yearParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            monthParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            yearParams.weight = 0f
            monthParams.weight = 0f
            ResponsiveUi.setStartMargin(binding.layoutMonthField, 0)
            ResponsiveUi.setTopMargin(binding.layoutMonthField, ResponsiveUi.dp(requireContext(), 8))
            addParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            statsParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            addParams.weight = 0f
            statsParams.weight = 0f
            ResponsiveUi.setStartMargin(binding.buttonStats, 0)
            ResponsiveUi.setTopMargin(binding.buttonStats, ResponsiveUi.dp(requireContext(), 10))
        } else {
            yearParams.width = 0
            monthParams.width = 0
            yearParams.weight = 1f
            monthParams.weight = 1f
            ResponsiveUi.setStartMargin(binding.layoutMonthField, ResponsiveUi.dp(requireContext(), 8))
            ResponsiveUi.setTopMargin(binding.layoutMonthField, 0)
            if (stackHeroActions) {
                addParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                statsParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                addParams.weight = 0f
                statsParams.weight = 0f
                ResponsiveUi.setStartMargin(binding.buttonStats, 0)
                ResponsiveUi.setTopMargin(binding.buttonStats, ResponsiveUi.dp(requireContext(), 10))
            } else {
                addParams.width = 0
                statsParams.width = 0
                addParams.weight = 1.25f
                statsParams.weight = 0.75f
                ResponsiveUi.setStartMargin(binding.buttonStats, ResponsiveUi.dp(requireContext(), 12))
                ResponsiveUi.setTopMargin(binding.buttonStats, 0)
            }
        }
        binding.layoutYearField.layoutParams = yearParams
        binding.layoutMonthField.layoutParams = monthParams
        binding.buttonAddEntry.layoutParams = addParams
        binding.buttonStats.layoutParams = statsParams
    }

    private fun loadStats(month: Int, year: Int) {
        val zone = ZoneId.systemDefault()
        val ym = YearMonth.of(year, month)
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = ym.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        statsJob?.cancel()
        statsJob = viewLifecycleOwner.lifecycleScope.launch {
            workRepository.getEntriesForPeriod(args.jobId, start, end)
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { list ->
                    var hours = 0.0
                    var morning = 0; var dayCount = 0; var night = 0
                    var baseSalary = 0.0

                    var bonusNight = 0.0
                    var bonusSat = 0.0
                    var bonusSun = 0.0
                    var bonusHol = 0.0

                    val dates = mutableSetOf<LocalDate>()
                    val holidayDates = mutableSetOf<LocalDate>()
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
                        if (entry.isHoliday) holidayDates.add(date)

                        baseSalary += h * entry.hourlyRate

                        if (entry.shiftType.lowercase() in listOf("нічна","night")) {
                            bonusNight += h * entry.nightBonus
                        }
                        if (date.dayOfWeek == DayOfWeek.SATURDAY) {
                            bonusSat += h * entry.saturdayBonus
                        }
                        if (date.dayOfWeek == DayOfWeek.SUNDAY) {
                            bonusSun += h * entry.sundayBonus
                        }
                        if (entry.isHoliday) {
                            bonusHol += h * entry.holidayBonus
                        }
                    }
                    holidays = holidayDates.size

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
                viewLifecycleOwner.lifecycleScope.launch {
                    val appContext = requireContext().applicationContext
                    val file: File = withContext(Dispatchers.IO) {
                        ExcelExporter(appContext).export(args.jobId)
                    }
                    if (!isAdded) return@launch

                    val context = requireContext()
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.share)))
                }
                true
            }
            R.id.action_import -> {
                launchImportPicker()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun launchImportPicker() {
        importLauncher.launch(
            arrayOf(
                "text/*",
                "text/comma-separated-values",
                "application/csv",
                "application/vnd.ms-excel"
            )
        )
    }

    private fun importIntoCurrentJob(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val summary = runCatching {
                val appContext = requireContext().applicationContext
                withContext(Dispatchers.IO) {
                    CsvImporter(appContext, DatabaseProvider.get(appContext)).import(uri, args.jobId)
                }
            }
            if (!isAdded) return@launch
            summary.onSuccess {
                Snackbar.make(
                    binding.root,
                    getString(
                        R.string.import_success_summary,
                        it.importedEntries,
                        it.createdJobs,
                        it.matchedJobs
                    ),
                    Snackbar.LENGTH_LONG
                ).show()
            }.onFailure {
                Snackbar.make(
                    binding.root,
                    it.message ?: getString(R.string.import_failed),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        statsJob?.cancel()
        _binding = null
    }
}
