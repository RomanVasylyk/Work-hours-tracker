package com.example.worktr.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.databinding.FragmentMonthOverviewBinding
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.salaryBreakdown
import com.example.worktr.util.workedHours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

class MonthOverviewFragment : Fragment() {
    private var _binding: FragmentMonthOverviewBinding? = null
    private val binding get() = _binding!!
    private val numberFormat = NumberFormat.getNumberInstance(Locale("sk", "SK")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val hoursFormat = NumberFormat.getNumberInstance(Locale("sk", "SK")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMonthOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val profile = ResponsiveUi.profile(requireContext())
        ResponsiveUi.applyOuterPadding(binding.monthScroll, profile)
        ResponsiveUi.applyContentPadding(binding.monthContent, profile)
        loadMonth()
    }

    private fun loadMonth() {
        val zone = ZoneId.systemDefault()
        val period = YearMonth.now()
        val month = resources.getStringArray(R.array.months)[period.monthValue - 1]
        binding.textMonthTitle.text = "$month ${period.year}"
        val start = period.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = period.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        viewLifecycleOwner.lifecycleScope.launch {
            val appContext = requireContext().applicationContext
            val data = withContext(Dispatchers.IO) {
                val db = DatabaseProvider.get(appContext)
                val entries = db.workEntryDao().getAllEntriesForPeriod(start, end).first()
                val jobs = db.jobDao().getAllJobsList().associateBy { it.jobId }
                val invoices = db.invoiceDao().getAllInvoicesList()
                    .filter { it.periodYear == period.year && it.periodMonth == period.monthValue }
                Triple(entries, jobs, invoices)
            }
            val entries = data.first
            val jobs = data.second
            val invoices = data.third
            val hours = entries.sumOf { it.workedHours() }
            val salary = entries.sumOf { it.salaryBreakdown(zone).total }
            val days = entries.map { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }.distinct().size

            binding.textMonthHours.text = "Години\n${hoursFormat.format(hours)} hod"
            binding.textMonthSalary.text = "Зарплата\n${numberFormat.format(salary)} €"
            binding.textMonthDays.text = "Дні\n$days"
            binding.textMonthInvoices.text = "Фактури\n${invoices.size}"
            binding.textMonthJobs.text = entries.groupBy { it.jobId }
                .map { (jobId, jobEntries) ->
                    val jobName = jobs[jobId]?.name ?: "Job $jobId"
                    val jobHours = jobEntries.sumOf { it.workedHours() }
                    val jobSalary = jobEntries.sumOf { it.salaryBreakdown(zone).total }
                    "$jobName · ${hoursFormat.format(jobHours)} hod · ${numberFormat.format(jobSalary)} €"
                }
                .ifEmpty { listOf(getString(R.string.month_no_jobs)) }
                .joinToString("\n")
            binding.textMonthInvoiceList.text = invoices
                .map { "${it.invoiceNumber} · ${it.customerName} · ${numberFormat.format(it.totalAmount)} ${it.currency}" }
                .ifEmpty { listOf(getString(R.string.month_no_invoices)) }
                .joinToString("\n")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
