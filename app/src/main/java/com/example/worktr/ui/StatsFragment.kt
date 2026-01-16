package com.example.worktr.ui

import android.graphics.Typeface
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
import com.example.worktr.util.ChartMarkerView
import com.example.worktr.viewmodel.JobDetailViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Job as KJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.*
import java.time.DayOfWeek
import java.time.Instant
import java.text.DecimalFormat

class StatsFragment : Fragment() {
    private lateinit var viewModel: JobDetailViewModel
    private lateinit var repo: WorkEntryRepository
    private lateinit var chartHours: LineChart
    private lateinit var chartSalary: BarChart
    private var currentJob: Job? = null

    private lateinit var yearsAdapter: ArrayAdapter<String>
    private val yearsList = mutableListOf<Int>()

    private var chartsJob: KJob? = null

    private val monthShort = arrayOf("Січ","Лют","Бер","Кві","Тра","Чер","Лип","Сер","Вер","Жов","Лис","Гру")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.fragment_stats, container, false)

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
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) = loadCharts()
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
            if (years.isEmpty()) yearsList.add(nowYear) else yearsList.addAll(years)

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
        if (spinnerY.count == 0) return

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

        chartsJob?.cancel()
        chartsJob = viewLifecycleOwner.lifecycleScope.launch {
            repo.getEntriesForPeriod(job.jobId, start, end).collectLatest { list ->
                if (list.isEmpty()) {
                    chartHours.clear()
                    chartSalary.clear()
                    chartHours.setNoDataText(getString(R.string.no_data))
                    chartSalary.setNoDataText(getString(R.string.no_data))
                    chartHours.invalidate()
                    chartSalary.invalidate()
                    updateTotals(root, 0.0, 0.0, isMonth, periodCount)
                    return@collectLatest
                }

                applyPremiumStyle(isMonth)

                val hoursEntries = mutableListOf<Entry>()
                val salaryEntries = mutableListOf<BarEntry>()

                var totalHours = 0.0
                var totalSalary = 0.0

                for (i in 1..periodCount) {
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

                val lineSet = LineDataSet(hoursEntries, if (isMonth) "Години / день" else "Години / місяць")
                styleLineDataSet(lineSet)
                chartHours.data = LineData(lineSet)
                chartHours.invalidate()

                val barSet = BarDataSet(salaryEntries, if (isMonth) "Зарплата / день" else "Зарплата / місяць")
                styleBarDataSet(barSet)
                chartSalary.data = BarData(barSet).apply { barWidth = 0.75f }
                chartSalary.invalidate()

                updateTotals(root, totalHours, totalSalary, isMonth, periodCount)
            }
        }
    }

    private fun applyPremiumStyle(isMonth: Boolean) {
        val xLabelProvider: (Int) -> String = { x ->
            if (isMonth) "День $x" else monthShort.getOrNull(x - 1)?.let { "Місяць $it" } ?: "Місяць $x"
        }

        chartHours.description = Description().apply { text = "" }
        chartHours.axisRight.isEnabled = false
        chartHours.setScaleEnabled(false)
        chartHours.setPinchZoom(false)
        chartHours.isDragEnabled = true
        chartHours.setTouchEnabled(true)
        chartHours.legend.apply {
            verticalAlignment = Legend.LegendVerticalAlignment.TOP
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            textSize = 12f
            form = Legend.LegendForm.LINE
        }
        chartHours.axisLeft.apply {
            setDrawAxisLine(false)
            setDrawGridLines(true)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            axisMinimum = 0f
            valueFormatter = object : ValueFormatter() {
                private val df = DecimalFormat("0.0")
                override fun getFormattedValue(value: Float): String = df.format(value)
            }
        }
        chartHours.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawAxisLine(false)
            setDrawGridLines(false)
            granularity = 1f
            textSize = 12f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    if (isMonth) value.toInt().toString()
                    else monthShort.getOrNull(value.toInt() - 1) ?: value.toInt().toString()
            }
        }
        chartHours.marker = ChartMarkerView(
            requireContext(),
            R.layout.view_chart_marker,
            xLabelProvider = xLabelProvider,
            valueSuffix = " h",
            decimals = 1
        )
        chartHours.animateY(450)

        // SALARY chart
        chartSalary.description = Description().apply { text = "" }
        chartSalary.axisRight.isEnabled = false
        chartSalary.setScaleEnabled(false)
        chartSalary.setPinchZoom(false)
        chartSalary.isDragEnabled = true
        chartSalary.setTouchEnabled(true)
        chartSalary.legend.apply {
            verticalAlignment = Legend.LegendVerticalAlignment.TOP
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            textSize = 12f
            form = Legend.LegendForm.SQUARE
        }
        chartSalary.axisLeft.apply {
            setDrawAxisLine(false)
            setDrawGridLines(true)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            axisMinimum = 0f
            valueFormatter = object : ValueFormatter() {
                private val df = DecimalFormat("0.00")
                override fun getFormattedValue(value: Float): String = df.format(value)
            }
        }
        chartSalary.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawAxisLine(false)
            setDrawGridLines(false)
            granularity = 1f
            textSize = 12f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    if (isMonth) value.toInt().toString()
                    else monthShort.getOrNull(value.toInt() - 1) ?: value.toInt().toString()
            }
        }
        chartSalary.marker = ChartMarkerView(
            requireContext(),
            R.layout.view_chart_marker,
            xLabelProvider = xLabelProvider,
            valueSuffix = "",
            decimals = 2
        )
        chartSalary.setFitBars(true)
        chartSalary.animateY(450)
    }

    private fun styleLineDataSet(ds: LineDataSet) {
        ds.lineWidth = 2.5f
        ds.setDrawCircles(true)
        ds.circleRadius = 3.5f
        ds.setDrawCircleHole(false)
        ds.setDrawValues(false)
        ds.mode = LineDataSet.Mode.CUBIC_BEZIER
        ds.setDrawHorizontalHighlightIndicator(false)
        ds.setDrawVerticalHighlightIndicator(true)
    }

    private fun styleBarDataSet(ds: BarDataSet) {
        ds.setDrawValues(false)
        ds.highLightAlpha = 60
    }

    private fun updateTotals(root: View, totalHours: Double, totalSalary: Double, isMonth: Boolean, periodCount: Int) {
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

    private fun dateMatches(millis: Long, zone: ZoneId, isMonth: Boolean, index: Int): Boolean {
        val dt = Instant.ofEpochMilli(millis).atZone(zone)
        return if (isMonth) dt.dayOfMonth == index else dt.monthValue == index
    }
}
