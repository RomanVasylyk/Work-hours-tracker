package com.example.worktr.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.InvoiceRecord
import com.example.worktr.databinding.FragmentInvoiceArchiveBinding
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.InvoiceFiles
import com.example.worktr.util.InvoiceInputJson
import com.example.worktr.util.InvoicePdfGenerator
import com.example.worktr.util.workedHours
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth
import java.time.ZoneId

class InvoiceArchiveFragment : Fragment() {
    private var _binding: FragmentInvoiceArchiveBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: InvoiceArchiveAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInvoiceArchiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val profile = ResponsiveUi.profile(requireContext())
        ResponsiveUi.applyOuterPadding(binding.invoiceArchiveContent, profile)

        adapter = InvoiceArchiveAdapter(
            monthLabels = resources.getStringArray(R.array.months).toList(),
            onOpen = ::openInvoice,
            onShare = ::shareInvoice,
            onRecreate = ::recreateInvoice
        )
        binding.recyclerInvoices.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerInvoices.adapter = adapter

        val db = DatabaseProvider.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            db.invoiceDao().getAllInvoices()
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { invoices ->
                    adapter.submitList(invoices)
                    binding.textEmptyInvoices.visibility = if (invoices.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerInvoices.visibility = if (invoices.isEmpty()) View.GONE else View.VISIBLE
                }
        }
    }

    private fun openInvoice(invoice: InvoiceRecord) {
        val file = invoiceFile(invoice)
        if (!file.exists()) {
            Snackbar.make(binding.root, R.string.invoice_file_missing, Snackbar.LENGTH_LONG).show()
            return
        }
        val context = requireContext()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { Snackbar.make(binding.root, R.string.invoice_open_failed, Snackbar.LENGTH_LONG).show() }
    }

    private fun shareInvoice(invoice: InvoiceRecord) {
        val file = invoiceFile(invoice)
        if (!file.exists()) {
            Snackbar.make(binding.root, R.string.invoice_file_missing, Snackbar.LENGTH_LONG).show()
            return
        }
        shareFile(file)
    }

    private fun recreateInvoice(invoice: InvoiceRecord) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val appContext = requireContext().applicationContext
                withContext(Dispatchers.IO) {
                    val db = DatabaseProvider.get(appContext)
                    val job = db.jobDao().getJobById(invoice.jobId)
                        ?: error(getString(R.string.invoice_job_missing))
                    val period = YearMonth.of(invoice.periodYear, invoice.periodMonth)
                    val start = period.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val end = period.atEndOfMonth()
                        .plusDays(1)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli() - 1
                    val entries = db.workEntryDao().getEntriesForPeriod(invoice.jobId, start, end).first()
                    if (entries.isEmpty()) {
                        error(getString(R.string.invoice_no_entries))
                    }
                    val input = InvoiceInputJson.decode(invoice.inputJson)
                    val generatedFile = InvoicePdfGenerator(appContext).generate(job, entries, period, input)
                    val archiveFile = InvoiceFiles.persist(appContext, generatedFile)
                    val totalAmount = entries.sumOf { entry ->
                        entry.workedHours() * entry.hourlyRate
                    } + (input.extraItem?.total ?: 0.0)
                    db.invoiceDao().update(
                        invoice.copy(
                            jobName = job.name,
                            totalAmount = totalAmount,
                            createdAtMillis = System.currentTimeMillis(),
                            fileName = archiveFile.name
                        )
                    )
                    archiveFile
                }
            }
            if (!isAdded) return@launch
            result.onSuccess { file ->
                Snackbar.make(binding.root, R.string.invoice_recreated, Snackbar.LENGTH_SHORT).show()
                shareFile(file)
            }.onFailure {
                Snackbar.make(
                    binding.root,
                    it.message ?: getString(R.string.invoice_open_failed),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun invoiceFile(invoice: InvoiceRecord): File =
        InvoiceFiles.fileFor(requireContext(), invoice.fileName)

    private fun shareFile(file: File) {
        val context = requireContext()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.invoice_share_title)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
