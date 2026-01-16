package com.example.worktr.ui

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.Job
import com.example.worktr.data.WorkEntryRepository
import com.example.worktr.viewmodel.JobDetailViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.*
import java.time.DayOfWeek
import java.time.Instant

class StatsFragment : Fragment() {
    private lateinit var viewModel: JobDetailViewModel
    private lateinit var repo: WorkEntryRepository
    private lateinit var chartHours: LineChart
    private lateinit var chartSalary: BarChart
    private var currentJob: Job? = null

    private lateinit var yearsAdapter: ArrayAdapter<String>
    private val yearsList = mutableListOf<Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater.inflate(R.layout.fragment_stats, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = StatsFragmentArgs.fromBundle(requireArguments())
        val db = DatabaseProvider.get(requireContext())
        repo = WorkEntryRepository(db.workEntryDao())
        val jobRepo = com.example.worktr.data.JobRepository(db.jobDao())

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(c: Class<T>) =
                JobDetailViewModel(jobRepo, args.jobId) as T
        })[JobDetailViewModel::class.java]

        chartHours = view.findViewById(R.id.chartHours)
        chartSalary = view.findViewById(R.id.chartSalary)

        val radioYear = view.findViewById<RadioButton>(R.id.radioYear)
        val radioMonth = view.findViewById<RadioButton>(R.id.radioMonth)
        val spinnerY = view.findViewById<Spinner>(R.id.spinnerStatsYear)
        val spinnerM = view.findViewById<Spinner>(R.id.spinnerStatsMonth)

        // --- Months adapter (як було)
        ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.months)
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerM.adapter = it
        }

        yearsAdapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            mutableListOf()
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerY.adapter = it
        }

        val now = LocalDate.now()
        spinnerM.setSelection(now.monthValue - 1)

        fun updateMode() {
            spinnerM.visibility = if (radioYear.isChecked) View.GONE else View.VISIBLE
            loadCharts()
        }
        radioYear.setOnCheckedChangeListener { _, _ -> updateMode() }
        radioMonth.setOnCheckedChangeListener { _, _ -> updateMode() }

        val selListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                loadCharts()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
        spinnerY.onItemSelectedListener = selListener
        spinnerM.onItemSelectedListener = selListener

        viewModel.job.observe(viewLifecycleOwner) { job: Job? ->
            currentJob = job ?: return@observe
            loadAvailableYears(job.jobId)   
        }
    }

    private fun loadAvailableYears(jobId: Int) {
        val root = view ?: return
        val spinnerY = root.findViewById<Spinner>(R.id.spinnerStatsYear)
        val nowYear = LocalDate.now().year

        lifecycleScope.launch {
            val years = repo.getYearsWithEntries(jobId).distinct().sorted()

            yearsList.clear()
            if (years.isEmpty()) {
                yearsList.add(nowYear)
            } else {
                yearsList.addAll(years)
            }

            yearsAdapter.clear()
            yearsAdapter.addAll(yearsList.map { it.toString() })
            yearsAdapter.notifyDataSetChanged()

            val idx = yearsList.indexOf(nowYear).let { if (it >= 0) it else 0 }
            spinnerY.setSelection(idx, false)

            loadCharts()
        }
    }

    private fun loadCharts() {
        val job = currentJob ?: return
        val root = view ?: return

        val spinnerY = root.findViewById<Spinner>(R.id.spinnerStatsYear)
        if (spinnerY.adapter == null || spinnerY.count == 0) return

        val isMonth = root.findViewById<RadioButton>(R.id.radioMonth).isChecked
        val year = spinnerY.selectedItem.toString().toInt()

        val month = root.findViewById<Spinner>(R.id.spinnerStatsMonth).selectedItemPosition + 1
        val zone = ZoneId.systemDefault()

        val (start, end, periodCount) = if (!isMonth) {
            val s = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
            val e = LocalDate.of(year, 12, 31).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            Triple(s, e, 12)
        } else {
            val ym = YearMonth.of(year, month)
            val s = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val e = ym.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            Triple(s, e, ym.lengthOfMonth())
        }

        lifecycleScope.launch {
            repo.getEntriesForPeriod(job.jobId, start, end).collectLatest { list ->
                val hoursEntries = mutableListOf<Entry>()
                val salaryEntries = mutableListOf<BarEntry>()
                val buckets = if (!isMonth) (1..12) else (1..periodCount)

                var totalHours = 0.0
                var totalSalary = 0.0

                buckets.forEach { i ->
                    val filtered = list.filter { dateMatches(it.date, zone, isMonth, i) }

                    val sumHours = filtered.sumOf { it.hoursWorked - it.breakHours }

                    val sumSalary = filtered.sumOf { entry ->
                        val h = entry.hoursWorked - entry.breakHours
                        var s = h * job.hourlyRate
                        if (entry.shiftType.lowercase() in listOf("night", "нічна")) s += h * job.nightBonus
                        val dow = Instant.ofEpochMilli(entry.date).atZone(zone).dayOfWeek
                        if (dow == DayOfWeek.SATURDAY) s += h * job.saturdayBonus
                        if (dow == DayOfWeek.SUNDAY) s += h * job.sundayBonus
                        if (entry.isHoliday) s += h * job.holidayBonus
                        s
                    }

                    hoursEntries.add(Entry(i.toFloat(), sumHours.toFloat()))
                    salaryEntries.add(BarEntry(i.toFloat(), sumSalary.toFloat()))
                    totalHours += sumHours
                    totalSalary += sumSalary
                }

                chartHours.data = LineData(LineDataSet(hoursEntries, getString(R.string.hours_worked_format, 0.0)))
                chartHours.xAxis.position = XAxis.XAxisPosition.BOTTOM
                chartHours.invalidate()

                chartSalary.data = BarData(BarDataSet(salaryEntries, getString(R.string.salary_format, 0.0)))
                chartSalary.setFitBars(true)
                chartSalary.xAxis.position = XAxis.XAxisPosition.BOTTOM
                chartSalary.invalidate()

                val totalHoursLabel = root.findViewById<TextView>(R.id.textTotalHours)
                val avgHoursLabel = root.findViewById<TextView>(R.id.textAvgHours)
                val totalSalaryLabel = root.findViewById<TextView>(R.id.textTotalSalary)
                val avgSalaryLabel = root.findViewById<TextView>(R.id.textAvgSalary)

                totalHoursLabel.text = getString(R.string.total_hours, totalHours)
                totalSalaryLabel.text = getString(R.string.total_salary, totalSalary)

                if (!isMonth) {
                    avgHoursLabel.visibility = View.VISIBLE
                    avgSalaryLabel.visibility = View.VISIBLE
                    avgHoursLabel.text = getString(R.string.avg_hours, totalHours / periodCount)
                    avgSalaryLabel.text = getString(R.string.avg_salary, totalSalary / periodCount)
                } else {
                    avgHoursLabel.visibility = View.GONE
                    avgSalaryLabel.visibility = View.GONE
                }
            }
        }
    }

    private fun dateMatches(millis: Long, zone: ZoneId, isMonth: Boolean, index: Int): Boolean {
        val dt = Instant.ofEpochMilli(millis).atZone(zone)
        return if (isMonth) dt.dayOfMonth == index else dt.monthValue == index
    }
}

