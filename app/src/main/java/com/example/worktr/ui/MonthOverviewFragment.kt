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
import com.example.worktr.util.WorkSummaries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

class MonthOverviewFragment : Fragment() {
    private var _binding: FragmentMonthOverviewBinding? = null
    private val binding get() = _binding!!
    private var currentPeriod: YearMonth = YearMonth.now()
    private val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val hoursFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
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
        binding.buttonPrevOverviewMonth.setOnClickListener { showMonth(currentPeriod.minusMonths(1)) }
        binding.buttonNextOverviewMonth.setOnClickListener { showMonth(currentPeriod.plusMonths(1)) }
        attachHorizontalSwipe(binding.monthScroll) { forward ->
            showMonth(if (forward) currentPeriod.plusMonths(1) else currentPeriod.minusMonths(1))
        }
        loadMonth()
    }

    private fun showMonth(period: YearMonth) {
        currentPeriod = period
        loadMonth()
    }

    private fun loadMonth() {
        val zone = ZoneId.systemDefault()
        val period = currentPeriod
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
            val summary = WorkSummaries.summarize(entries, zone)

            binding.textMonthHours.text = getString(R.string.month_stat_hours, hoursFormat.format(summary.hours))
            binding.textMonthSalary.text = getString(R.string.month_stat_salary, numberFormat.format(summary.totalSalary))
            binding.textMonthDays.text = getString(R.string.month_stat_days, summary.daysWorked)
            binding.textMonthInvoices.text = getString(R.string.month_stat_invoices, invoices.size)
            binding.textMonthJobs.text = entries.groupBy { it.jobId }
                .map { (jobId, jobEntries) ->
                    val jobName = jobs[jobId]?.name ?: "Job $jobId"
                    val jobSummary = WorkSummaries.summarize(jobEntries, zone)
                    getString(
                        R.string.month_job_line,
                        jobName,
                        hoursFormat.format(jobSummary.hours),
                        numberFormat.format(jobSummary.totalSalary)
                    )
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
