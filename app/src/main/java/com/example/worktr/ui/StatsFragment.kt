package com.example.worktr.ui

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.WorkEntryRepository
import com.example.worktr.data.Job
import com.example.worktr.viewmodel.JobDetailViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.*
import java.time.Instant
import java.time.DayOfWeek

class StatsFragment : Fragment() {
    private lateinit var viewModel: JobDetailViewModel
    private lateinit var repo: WorkEntryRepository
    private lateinit var chartHours: LineChart
    private lateinit var chartSalary: BarChart
    private var currentJob: Job? = null

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
        viewModel.job.observe(viewLifecycleOwner) {
            currentJob = it
            loadCharts()
        }
        chartHours = view.findViewById(R.id.chartHours)
        chartSalary = view.findViewById(R.id.chartSalary)
        val radioYear = view.findViewById<RadioButton>(R.id.radioYear)
        val radioMonth = view.findViewById<RadioButton>(R.id.radioMonth)
        val spinnerY = view.findViewById<Spinner>(R.id.spinnerStatsYear)
        val spinnerM = view.findViewById<Spinner>(R.id.spinnerStatsMonth)
        val years = resources.getStringArray(R.array.years)
        val months = resources.getStringArray(R.array.months)
        spinnerY.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, years).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerM.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, months).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val now = LocalDate.now()
        spinnerY.setSelection(years.indexOf(now.year.toString()))
        spinnerM.setSelection(now.monthValue - 1)
        val modeChange = {
            spinnerM.visibility = if (radioYear.isChecked) View.GONE else View.VISIBLE
            loadCharts()
        }
        radioYear.setOnCheckedChangeListener { _, _ -> modeChange() }
        radioMonth.setOnCheckedChangeListener { _, _ -> modeChange() }
        val selListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) = loadCharts()
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
        spinnerY.onItemSelectedListener = selListener
        spinnerM.onItemSelectedListener = selListener
    }

    private fun loadCharts() {
        val job = currentJob ?: return
        val isMonth = view?.findViewById<RadioButton>(R.id.radioMonth)?.isChecked == true
        val year = view?.findViewById<Spinner>(R.id.spinnerStatsYear)?.selectedItem.toString().toInt()
        val month = view?.findViewById<Spinner>(R.id.spinnerStatsMonth)?.selectedItemPosition?.plus(1)
        val zone = ZoneId.systemDefault()
        val (start, end) = if (!isMonth) {
            val s = LocalDate.of(year,1,1).atStartOfDay(zone).toInstant().toEpochMilli()
            val e = LocalDate.of(year,12,31).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1
            s to e
        } else {
            val ym = YearMonth.of(year, month!!)
            val s = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val e = ym.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1
            s to e
        }
        lifecycleScope.launch {
            repo.getEntriesForPeriod(viewModel.job.value!!.jobId, start, end).collectLatest { list ->
                val hoursEntries = if (!isMonth) {
                    (1..12).map { m ->
                        val sum = list.filter {
                            Instant.ofEpochMilli(it.date).atZone(zone).monthValue==m
                        }.sumOf { it.hoursWorked-it.breakHours }
                        Entry(m.toFloat(), sum.toFloat())
                    }
                } else {
                    val days=YearMonth.of(year,month!!).lengthOfMonth()
                    (1..days).map { d ->
                        val sum=list.filter {
                            Instant.ofEpochMilli(it.date).atZone(zone).dayOfMonth==d
                        }.sumOf { it.hoursWorked-it.breakHours }
                        Entry(d.toFloat(),sum.toFloat())
                    }
                }
                val salaryEntries = if (!isMonth) {
                    (1..12).map { m ->
                        val sum=list.filter {
                            Instant.ofEpochMilli(it.date).atZone(zone).monthValue==m
                        }.sumOf {
                            val h=it.hoursWorked-it.breakHours
                            var s=h*job.hourlyRate
                            if(it.shiftType.lowercase() in listOf("night","нічна"))s+=h*job.nightBonus
                            val dow=Instant.ofEpochMilli(it.date).atZone(zone).dayOfWeek
                            if(dow==DayOfWeek.SATURDAY)s+=h*job.saturdayBonus
                            if(dow==DayOfWeek.SUNDAY)s+=h*job.sundayBonus
                            if(it.isHoliday)s+=h*job.holidayBonus
                            s
                        }
                        BarEntry(m.toFloat(),sum.toFloat())
                    }
                } else {
                    val days=YearMonth.of(year,month!!).lengthOfMonth()
                    (1..days).map { d ->
                        val sum=list.filter {
                            Instant.ofEpochMilli(it.date).atZone(zone).dayOfMonth==d
                        }.sumOf {
                            val h=it.hoursWorked-it.breakHours
                            var s=h*job.hourlyRate
                            if(it.shiftType.lowercase() in listOf("night","нічна"))s+=h*job.nightBonus
                            val dow=Instant.ofEpochMilli(it.date).atZone(zone).dayOfWeek
                            if(dow==DayOfWeek.SATURDAY)s+=h*job.saturdayBonus
                            if(dow==DayOfWeek.SUNDAY)s+=h*job.sundayBonus
                            if(it.isHoliday)s+=h*job.holidayBonus
                            s
                        }
                        BarEntry(d.toFloat(),sum.toFloat())
                    }
                }
                chartHours.data=LineData(LineDataSet(hoursEntries,"Години"))
                chartHours.xAxis.position=XAxis.XAxisPosition.BOTTOM
                chartHours.invalidate()
                val set=BarDataSet(salaryEntries,"Зарплата")
                val data=BarData(set).apply{barWidth= if(!isMonth)0.7f else 0.9f}
                chartSalary.data=data
                chartSalary.setFitBars(true)
                chartSalary.xAxis.position=XAxis.XAxisPosition.BOTTOM
                chartSalary.invalidate()
            }
        }
    }
}
