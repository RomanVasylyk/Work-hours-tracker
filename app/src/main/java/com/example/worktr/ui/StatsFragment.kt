package com.example.worktr.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.*
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.Job
import com.example.worktr.data.WorkEntryRepository
import com.example.worktr.ui.picker.DropdownUi
import com.example.worktr.ui.picker.DynamicYearSpinner
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.localDate
import com.example.worktr.util.salaryBreakdown
import com.example.worktr.util.workedHours
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.example.worktr.viewmodel.JobDetailViewModel
import com.example.worktr.ui.chart.chartMarker
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@AndroidEntryPoint
class StatsFragment : Fragment() {
    private val jobViewModel: JobDetailViewModel by viewModels()
    @Inject lateinit var repo: WorkEntryRepository
    private lateinit var chartHours: CartesianChartView
    private lateinit var chartSalary: CartesianChartView
    private lateinit var salaryLegend: GridLayout
    private val hoursProducer = CartesianChartModelProducer()
    private val salaryProducer = CartesianChartModelProducer()
    private var axisLabels: List<String> = emptyList()
    private lateinit var yearSpinner: DynamicYearSpinner
    private lateinit var monthLabels: List<String>
    private var currentJob: Job? = null
    private var chartsJob: CoroutineJob? = null
    private var targetJobId: Int = -1
    private var currentMode: StatsMode = StatsMode.YEAR

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) =
        inflater.inflate(R.layout.fragment_stats, container, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = StatsFragmentArgs.fromBundle(requireArguments())
        targetJobId = args.jobId
        chartHours = view.findViewById(R.id.chartHours)
        chartSalary = view.findViewById(R.id.chartSalary)
        salaryLegend = view.findViewById(R.id.layoutSalaryLegend)
        setupCharts()
        applyResponsiveLayout(view)
        updateScopeHeader(activeJobs = 0)
        val modeGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.groupStatsMode)
        val inputY = view.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.inputStatsYear)
        val inputM = view.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.inputStatsMonth)

        if (targetJobId != -1) {
            jobViewModel.job.observe(viewLifecycleOwner) {
                currentJob = it
                updateScopeHeader(activeJobs = null)
                loadCharts()
            }
        } else {
            activity?.title = getString(R.string.overall_stats)
        }

        yearSpinner = DynamicYearSpinner(
            context = requireContext(),
            input = inputY,
            initialYear = LocalDate.now().year
        ) {
            loadCharts()
        }
        monthLabels = resources.getStringArray(R.array.months).toList()
        inputM.setAdapter(DropdownUi.adapter(requireContext(), monthLabels))
        val now = LocalDate.now()
        inputM.setText(monthLabels[now.monthValue - 1], false)
        DropdownUi.attach(inputM) {
            monthLabels.indexOf(inputM.text?.toString().orEmpty()).takeIf { it >= 0 }
        }

        modeGroup.check(R.id.buttonModeYear)
        modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = if (checkedId == R.id.buttonModeMonth) StatsMode.MONTH else StatsMode.YEAR
            setStatsMode(mode)
        }
        inputM.setOnItemClickListener { _, _, _, _ -> loadCharts() }
        setStatsMode(StatsMode.YEAR, reload = false)

        loadCharts()
    }

    private fun loadCharts() {
        val root = view ?: return
        if (!::yearSpinner.isInitialized || !::monthLabels.isInitialized) return
        if (targetJobId != -1 && currentJob == null) return
        val isMonth = currentMode == StatsMode.MONTH
        val year = yearSpinner.getSelectedYear() ?: return
        val month = monthLabels.indexOf(
            root.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.inputStatsMonth)
                .text
                ?.toString()
                .orEmpty()
        ).let { index ->
            if (index >= 0) index + 1 else LocalDate.now().monthValue
        }
        val zone = ZoneId.systemDefault()
        val (start, end, periodCount) = if (!isMonth) {
            val s = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
            val e = LocalDate.of(year, 12, 31).plusDays(1).atStartOfDay(zone).toInstant()
                .toEpochMilli() - 1
            Triple(s, e, 12)
        } else {
            val ym = YearMonth.of(year, month)
            val s = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val e = ym.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            Triple(s, e, ym.lengthOfMonth())
        }

        chartsJob?.cancel()
        chartsJob = viewLifecycleOwner.lifecycleScope.launch {
            val entriesFlow = if (targetJobId == -1) {
                repo.getEntriesForPeriod(start, end)
            } else {
                repo.getEntriesForPeriod(targetJobId, start, end)
            }
            val invoicesFlow = DatabaseProvider.get(requireContext().applicationContext)
                .invoiceDao()
                .getAllInvoices()
            combine(entriesFlow, invoicesFlow) { entries, invoices -> entries to invoices }
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { (list, invoices) ->
                val labels = buildLabels(isMonth, periodCount)
                axisLabels = labels
                val buckets = if (!isMonth) (1..12) else (1..periodCount)
                val hoursBuckets = DoubleArray(periodCount + 1)
                val baseBuckets = DoubleArray(periodCount + 1)
                val nightBuckets = DoubleArray(periodCount + 1)
                val saturdayBuckets = DoubleArray(periodCount + 1)
                val sundayBuckets = DoubleArray(periodCount + 1)
                val holidayBuckets = DoubleArray(periodCount + 1)
                val invoiceBuckets = DoubleArray(periodCount + 1)

                list.forEach { entry ->
                    val date = entry.localDate(zone)
                    val index = if (isMonth) date.dayOfMonth else date.monthValue
                    val breakdown = entry.salaryBreakdown(zone)
                    hoursBuckets[index] += entry.workedHours()
                    baseBuckets[index] += breakdown.base
                    nightBuckets[index] += breakdown.night
                    saturdayBuckets[index] += breakdown.saturday
                    sundayBuckets[index] += breakdown.sunday
                    holidayBuckets[index] += breakdown.holiday
                }
                invoices
                    .asSequence()
                    .filter { invoice -> targetJobId == -1 || invoice.jobId == targetJobId }
                    .forEach { invoice ->
                        if (!isMonth) {
                            if (invoice.periodYear == year && invoice.periodMonth in 1..12) {
                                invoiceBuckets[invoice.periodMonth] += invoice.totalAmount
                            }
                        } else if (invoice.periodYear == year && invoice.periodMonth == month) {
                            val issueDate = runCatching { LocalDate.parse(invoice.issueDate) }.getOrNull()
                            val index = if (
                                issueDate != null &&
                                issueDate.year == year &&
                                issueDate.monthValue == month
                            ) {
                                issueDate.dayOfMonth
                            } else {
                                periodCount
                            }
                            invoiceBuckets[index] += invoice.totalAmount
                        }
                    }

                val xs = buckets.toList()
                val totalHours = xs.sumOf { hoursBuckets[it] }
                val totalSalary = xs.sumOf {
                    baseBuckets[it] + nightBuckets[it] + saturdayBuckets[it] + sundayBuckets[it] + holidayBuckets[it]
                }

                hoursProducer.runTransaction {
                    lineSeries { series(xs, xs.map { hoursBuckets[it] }) }
                }
                salaryProducer.runTransaction {
                    columnSeries {
                        series(xs, xs.map { baseBuckets[it] })
                        series(xs, xs.map { nightBuckets[it] })
                        series(xs, xs.map { saturdayBuckets[it] })
                        series(xs, xs.map { sundayBuckets[it] })
                        series(xs, xs.map { holidayBuckets[it] })
                    }
                    lineSeries { series(xs, xs.map { invoiceBuckets[it] }) }
                }

                val totalHoursLabel = root.findViewById<TextView>(R.id.textTotalHours)
                val avgHoursLabel = root.findViewById<TextView>(R.id.textAvgHours)
                val totalSalaryLabel = root.findViewById<TextView>(R.id.textTotalSalary)
                val avgSalaryLabel = root.findViewById<TextView>(R.id.textAvgSalary)

                totalHoursLabel.text = getString(R.string.total_hours, totalHours)
                totalSalaryLabel.text = getString(R.string.total_salary, totalSalary)
                updateScopeHeader(
                    activeJobs = if (targetJobId == -1) list.map { it.jobId }.distinct().size else null
                )

                if (!isMonth) {
                    val averagePeriodCount = averageYearPeriodCount(year)
                    avgHoursLabel.visibility = View.VISIBLE
                    avgSalaryLabel.visibility = View.VISIBLE
                    avgHoursLabel.text = getString(R.string.avg_hours, safeAverage(totalHours, averagePeriodCount))
                    avgSalaryLabel.text = getString(R.string.avg_salary, safeAverage(totalSalary, averagePeriodCount))
                } else {
                    avgHoursLabel.visibility = View.GONE
                    avgSalaryLabel.visibility = View.GONE
                }
            }
        }
    }

    private fun setStatsMode(mode: StatsMode, reload: Boolean = true) {
        val root = view ?: return
        currentMode = mode
        val modeGroup = root.findViewById<MaterialButtonToggleGroup>(R.id.groupStatsMode)
        val targetButtonId = if (mode == StatsMode.MONTH) R.id.buttonModeMonth else R.id.buttonModeYear
        if (modeGroup.checkedButtonId != targetButtonId) {
            modeGroup.check(targetButtonId)
        }
        root.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutStatsMonthField)
            .visibility = if (mode == StatsMode.MONTH) View.VISIBLE else View.GONE
        if (reload) {
            loadCharts()
        }
    }





    private fun setupCharts() {
        val context = requireContext()
        val axisLabelFormatter = CartesianValueFormatter { _, value, _ ->
            axisLabels.getOrElse(value.roundToInt() - 1) { "" }
        }

        val hoursColor = ContextCompat.getColor(context, R.color.chart_hours_line)
        val hoursLine = LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(hoursColor)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thicknessDp = 3f),
            areaFill = LineCartesianLayer.AreaFill.single(
                Fill(ColorUtils.setAlphaComponent(hoursColor, 60))
            )
        )
        chartHours.chart = CartesianChart(
            LineCartesianLayer(LineCartesianLayer.LineProvider.series(hoursLine)),
            startAxis = VerticalAxis.start(),
            bottomAxis = HorizontalAxis.bottom(valueFormatter = axisLabelFormatter),
            marker = chartMarker(context)
        )
        chartHours.modelProducer = hoursProducer

        val salaryColumns = listOf(
            R.color.chart_salary_base,
            R.color.chart_salary_night,
            R.color.chart_salary_saturday,
            R.color.chart_salary_sunday,
            R.color.chart_salary_holiday
        ).map { colorRes ->
            LineComponent(fill = Fill(ContextCompat.getColor(context, colorRes)), thicknessDp = 10f)
        }
        val invoiceLine = LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(
                Fill(ContextCompat.getColor(context, R.color.chart_invoice_bar))
            ),
            stroke = LineCartesianLayer.LineStroke.Continuous(thicknessDp = 2f)
        )
        chartSalary.chart = CartesianChart(
            ColumnCartesianLayer(
                ColumnCartesianLayer.ColumnProvider.series(salaryColumns),
                mergeMode = { ColumnCartesianLayer.MergeMode.Stacked }
            ),
            LineCartesianLayer(LineCartesianLayer.LineProvider.series(invoiceLine)),
            startAxis = VerticalAxis.start(),
            bottomAxis = HorizontalAxis.bottom(valueFormatter = axisLabelFormatter),
            marker = chartMarker(context)
        )
        chartSalary.modelProducer = salaryProducer
    }

    private fun bindSalaryLegend() {
        val items = listOf(
            SalaryLegendItem(R.color.chart_salary_base, getString(R.string.hourly_rate)),
            SalaryLegendItem(R.color.chart_salary_night, getString(R.string.night_bonus)),
            SalaryLegendItem(R.color.chart_salary_saturday, getString(R.string.sat_bonus)),
            SalaryLegendItem(R.color.chart_salary_sunday, getString(R.string.sun_bonus)),
            SalaryLegendItem(R.color.chart_salary_holiday, getString(R.string.hol_bonus)),
            SalaryLegendItem(R.color.chart_invoice_bar, getString(R.string.chart_salary_invoiced))
        )
        val columns = if (ResponsiveUi.profile(requireContext()).isCompact) 1 else 2
        salaryLegend.removeAllViews()
        salaryLegend.columnCount = columns
        items.forEach { item ->
            salaryLegend.addView(createLegendItem(item), GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(0, ResponsiveUi.dp(requireContext(), 2), ResponsiveUi.dp(requireContext(), 8), ResponsiveUi.dp(requireContext(), 2))
            })
        }
    }

    private fun createLegendItem(item: SalaryLegendItem): View {
        val color = ContextCompat.getColor(requireContext(), item.colorRes)
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ResponsiveUi.dp(requireContext(), 32)
            setPadding(0, ResponsiveUi.dp(requireContext(), 4), ResponsiveUi.dp(requireContext(), 8), ResponsiveUi.dp(requireContext(), 4))

            addView(View(requireContext()).apply {
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle_dot)
                ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(color))
            }, LinearLayout.LayoutParams(ResponsiveUi.dp(requireContext(), 10), ResponsiveUi.dp(requireContext(), 10)))

            addView(TextView(requireContext()).apply {
                text = item.label
                maxLines = 2
                setTextColor(ContextCompat.getColor(requireContext(), R.color.onSurface))
                textSize = 12f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ResponsiveUi.dp(requireContext(), 8)
            })
        }
    }


    private fun buildLabels(isMonth: Boolean, periodCount: Int): List<String> {
        return if (isMonth) {
            (1..periodCount).map { it.toString() }
        } else {
            (1..12).map {
                Month.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    .replaceFirstChar { ch -> ch.titlecase(Locale.getDefault()) }
            }
        }
    }

    private fun averageYearPeriodCount(year: Int): Int {
        val now = LocalDate.now()
        return when {
            year < now.year -> 12
            year == now.year -> now.monthValue
            else -> 0
        }
    }

    private fun safeAverage(total: Double, periodCount: Int): Double =
        if (periodCount > 0) total / periodCount else 0.0

    private fun updateScopeHeader(activeJobs: Int?) {
        val root = view ?: return
        val textScope = root.findViewById<TextView>(R.id.textStatsScope)
        val textMeta = root.findViewById<TextView>(R.id.textStatsMeta)
        if (targetJobId == -1) {
            textScope.text = getString(R.string.stats_scope_all)
            textMeta.text = getString(R.string.stats_scope_all_meta, activeJobs ?: 0)
        } else {
            val name = currentJob?.name.orEmpty()
            textScope.text = getString(R.string.stats_scope_job, name)
            textMeta.text = getString(R.string.stats_scope_job_meta)
        }
    }

    private fun applyResponsiveLayout(view: View) {
        val profile = ResponsiveUi.profile(requireContext())
        ResponsiveUi.applyOuterPadding(view.findViewById(R.id.statsScroll), profile)
        ResponsiveUi.applyContentPadding(view.findViewById(R.id.statsContent), profile)
        ResponsiveUi.updateHeight(chartHours, profile.chartHeightPx)
        ResponsiveUi.updateHeight(chartSalary, profile.chartHeightPx)
        bindSalaryLegend()

        val groupMode = view.findViewById<MaterialButtonToggleGroup>(R.id.groupStatsMode)
        val filters = view.findViewById<LinearLayout>(R.id.layoutStatsFilters)
        val buttonYear = view.findViewById<MaterialButton>(R.id.buttonModeYear)
        val buttonMonth = view.findViewById<MaterialButton>(R.id.buttonModeMonth)
        val yearField = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutStatsYearField)
        val monthField = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutStatsMonthField)
        val yearButtonParams = buttonYear.layoutParams as LinearLayout.LayoutParams
        val monthButtonParams = buttonMonth.layoutParams as LinearLayout.LayoutParams

        if (profile.isCompact) {
            groupMode.orientation = LinearLayout.VERTICAL
            ResponsiveUi.setLinearOrientation(filters, true)
            yearButtonParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            monthButtonParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            yearButtonParams.weight = 0f
            monthButtonParams.weight = 0f
            buttonYear.layoutParams = yearButtonParams
            buttonMonth.layoutParams = monthButtonParams
            ResponsiveUi.setTopMargin(buttonMonth, ResponsiveUi.dp(requireContext(), 8))
            val yearParams = yearField.layoutParams as LinearLayout.LayoutParams
            val monthParams = monthField.layoutParams as LinearLayout.LayoutParams
            yearParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            monthParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            yearParams.weight = 0f
            monthParams.weight = 0f
            yearField.layoutParams = yearParams
            monthField.layoutParams = monthParams
            ResponsiveUi.setStartMargin(monthField, 0)
            ResponsiveUi.setTopMargin(monthField, ResponsiveUi.dp(requireContext(), 8))
        } else {
            groupMode.orientation = LinearLayout.HORIZONTAL
            ResponsiveUi.setLinearOrientation(filters, false)
            yearButtonParams.width = 0
            monthButtonParams.width = 0
            yearButtonParams.weight = 1f
            monthButtonParams.weight = 1f
            buttonYear.layoutParams = yearButtonParams
            buttonMonth.layoutParams = monthButtonParams
            ResponsiveUi.setTopMargin(buttonMonth, 0)
            val yearParams = yearField.layoutParams as LinearLayout.LayoutParams
            val monthParams = monthField.layoutParams as LinearLayout.LayoutParams
            yearParams.width = 0
            monthParams.width = 0
            yearParams.weight = 1f
            monthParams.weight = 1f
            yearField.layoutParams = yearParams
            monthField.layoutParams = monthParams
            ResponsiveUi.setStartMargin(monthField, ResponsiveUi.dp(requireContext(), 8))
            ResponsiveUi.setTopMargin(monthField, 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chartsJob?.cancel()
    }

    private enum class StatsMode {
        YEAR,
        MONTH
    }

    private data class SalaryLegendItem(
        val colorRes: Int,
        val label: String
    )
}
