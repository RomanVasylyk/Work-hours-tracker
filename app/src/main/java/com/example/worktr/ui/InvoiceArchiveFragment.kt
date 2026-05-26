package com.example.worktr.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
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
import com.example.worktr.databinding.DialogInvoiceRecreateEditBinding
import com.example.worktr.databinding.FragmentInvoiceArchiveBinding
import com.example.worktr.ui.picker.DropdownUi
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.InvoiceFiles
import com.example.worktr.util.InvoiceExtraItem
import com.example.worktr.util.InvoiceInput
import com.example.worktr.util.InvoiceInputJson
import com.example.worktr.util.InvoicePdfGenerator
import com.example.worktr.util.InvoiceStatus
import com.example.worktr.util.workedHours
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.text.NumberFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InvoiceArchiveFragment : Fragment() {
    private var _binding: FragmentInvoiceArchiveBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: InvoiceArchiveAdapter
    private var allInvoices: List<InvoiceRecord> = emptyList()
    private lateinit var allLabel: String
    private lateinit var statusLabels: Map<String, InvoiceStatus?>
    private val numberFormat = NumberFormat.getNumberInstance(Locale("sk", "SK")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInvoiceArchiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val profile = ResponsiveUi.profile(requireContext())
        ResponsiveUi.applyOuterPadding(binding.invoiceArchiveContent, profile)
        allLabel = getString(R.string.invoice_filter_all)
        statusLabels = mapOf(
            allLabel to null,
            getString(R.string.invoice_status_created) to InvoiceStatus.CREATED,
            getString(R.string.invoice_status_sent) to InvoiceStatus.SENT,
            getString(R.string.invoice_status_paid) to InvoiceStatus.PAID,
            getString(R.string.invoice_status_overdue) to InvoiceStatus.OVERDUE
        )

        adapter = InvoiceArchiveAdapter(
            monthLabels = resources.getStringArray(R.array.months).toList(),
            onOpen = ::openInvoice,
            onShare = ::shareInvoice,
            onRecreate = ::showRecreateOptions,
            onStatus = ::showStatusDialog,
            onLongPress = ::confirmDeleteInvoice
        )
        binding.recyclerInvoices.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerInvoices.adapter = adapter
        setupFilters()

        val db = DatabaseProvider.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            db.invoiceDao().getAllInvoices()
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { invoices ->
                    allInvoices = invoices
                    refreshFilterOptions()
                    applyFilters()
                }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_invoice_archive, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_invoice_zip -> {
                showExportZipDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupFilters() {
        binding.inputInvoiceYear.setText(allLabel, false)
        binding.inputInvoiceMonth.setText(allLabel, false)
        binding.inputInvoiceClient.setText(allLabel, false)
        binding.inputInvoiceStatus.setAdapter(DropdownUi.adapter(requireContext(), statusLabels.keys.toList()))
        binding.inputInvoiceStatus.setText(allLabel, false)
        binding.layoutInvoiceSearch.visibility = View.GONE
        binding.layoutInvoiceFilters.visibility = View.GONE
        binding.switchInvoiceFilters.setOnCheckedChangeListener { _, isChecked ->
            val visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.layoutInvoiceSearch.visibility = visibility
            binding.layoutInvoiceFilters.visibility = visibility
        }

        listOf(
            binding.inputInvoiceYear,
            binding.inputInvoiceMonth,
            binding.inputInvoiceClient,
            binding.inputInvoiceStatus
        ).forEach { input ->
            DropdownUi.attach(input)
            input.setOnItemClickListener { _, _, _, _ -> applyFilters() }
        }

        binding.editInvoiceSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilters()
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun refreshFilterOptions() {
        val years = listOf(allLabel) + allInvoices.map { it.periodYear.toString() }.distinct().sortedDescending()
        val months = listOf(allLabel) + resources.getStringArray(R.array.months).toList()
        val clients = listOf(allLabel) + allInvoices.map { it.customerName }.filter { it.isNotBlank() }.distinct().sorted()
        val currentYear = binding.inputInvoiceYear.text?.toString().orEmpty()
        val currentMonth = binding.inputInvoiceMonth.text?.toString().orEmpty()
        val currentClient = binding.inputInvoiceClient.text?.toString().orEmpty()
        binding.inputInvoiceYear.setAdapter(DropdownUi.adapter(requireContext(), years))
        binding.inputInvoiceMonth.setAdapter(DropdownUi.adapter(requireContext(), months))
        binding.inputInvoiceClient.setAdapter(DropdownUi.adapter(requireContext(), clients))
        if (currentYear !in years) binding.inputInvoiceYear.setText(allLabel, false)
        if (currentMonth !in months) binding.inputInvoiceMonth.setText(allLabel, false)
        if (currentClient !in clients) binding.inputInvoiceClient.setText(allLabel, false)
    }

    private fun applyFilters() {
        val query = binding.editInvoiceSearch.text?.toString()?.trim().orEmpty().lowercase(Locale.getDefault())
        val selectedYear = binding.inputInvoiceYear.text?.toString()?.takeIf { it != allLabel }?.toIntOrNull()
        val selectedMonth = binding.inputInvoiceMonth.text?.toString()?.takeIf { it != allLabel }?.let { month ->
            resources.getStringArray(R.array.months).indexOf(month).takeIf { it >= 0 }?.plus(1)
        }
        val selectedClient = binding.inputInvoiceClient.text?.toString()?.takeIf { it != allLabel }
        val selectedStatus = statusLabels[binding.inputInvoiceStatus.text?.toString().orEmpty()]

        val filtered = allInvoices.filter { invoice ->
            val matchesQuery = query.isBlank() ||
                invoice.invoiceNumber.lowercase(Locale.getDefault()).contains(query) ||
                invoice.customerName.lowercase(Locale.getDefault()).contains(query) ||
                numberFormat.format(invoice.totalAmount).lowercase(Locale.getDefault()).contains(query) ||
                invoice.totalAmount.toString().contains(query)
            matchesQuery &&
                (selectedYear == null || invoice.periodYear == selectedYear) &&
                (selectedMonth == null || invoice.periodMonth == selectedMonth) &&
                (selectedClient == null || invoice.customerName == selectedClient) &&
                (selectedStatus == null || InvoiceStatus.fromValue(invoice.status) == selectedStatus)
        }
        adapter.submitList(filtered)
        binding.textEmptyInvoices.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerInvoices.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmDeleteInvoice(invoice: InvoiceRecord) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.invoice_delete_title)
            .setMessage(getString(R.string.invoice_delete_message, invoice.invoiceNumber))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.invoice_delete_confirm) { _, _ -> deleteInvoice(invoice) }
            .show()
    }

    private fun deleteInvoice(invoice: InvoiceRecord) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val appContext = requireContext().applicationContext
                withContext(Dispatchers.IO) {
                    InvoiceFiles.fileFor(appContext, invoice.fileName).delete()
                    DatabaseProvider.get(appContext).invoiceDao().deleteById(invoice.invoiceId)
                }
            }
            if (!isAdded) return@launch
            result.onSuccess {
                Snackbar.make(binding.root, R.string.invoice_deleted, Snackbar.LENGTH_SHORT).show()
            }.onFailure {
                Snackbar.make(
                    binding.root,
                    it.message ?: getString(R.string.invoice_open_failed),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openInvoice(invoice: InvoiceRecord) {
        val file = invoiceFile(invoice)
        if (!file.exists()) {
            Snackbar.make(binding.root, R.string.invoice_file_missing, Snackbar.LENGTH_LONG).show()
            return
        }
        openFile(file, "application/pdf")
    }

    private fun shareInvoice(invoice: InvoiceRecord) {
        val file = invoiceFile(invoice)
        if (!file.exists()) {
            Snackbar.make(binding.root, R.string.invoice_file_missing, Snackbar.LENGTH_LONG).show()
            return
        }
        shareFile(file)
    }

    private fun showRecreateOptions(invoice: InvoiceRecord) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.invoice_recreate)
            .setItems(
                arrayOf(
                    getString(R.string.invoice_recreate_direct),
                    getString(R.string.invoice_recreate_edit)
                )
            ) { _, which ->
                when (which) {
                    0 -> recreateInvoice(invoice)
                    1 -> showRecreateEditDialog(invoice)
                }
            }
            .show()
    }

    private fun showRecreateEditDialog(invoice: InvoiceRecord) {
        val input = runCatching { InvoiceInputJson.decode(invoice.inputJson) }.getOrElse {
            Snackbar.make(binding.root, R.string.invoice_open_failed, Snackbar.LENGTH_LONG).show()
            return
        }
        val dialogBinding = DialogInvoiceRecreateEditBinding.inflate(layoutInflater)
        dialogBinding.editDescription.setText(input.description)
        dialogBinding.editExtraItems.setText(input.allExtraItems().joinToString("\n") {
            listOf(it.name, it.quantity.toString(), it.unit, it.unitPrice.toString()).joinToString(" | ")
        })
        dialogBinding.editIssueDate.setText(input.issueDate.toString())
        dialogBinding.editDueDate.setText(input.dueDate.toString())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialogBinding.buttonCancelInvoice.setOnClickListener { dialog.dismiss() }
        dialogBinding.buttonCreateInvoice.setOnClickListener {
            val editedInput = runCatching {
                input.copy(
                    description = dialogBinding.editDescription.text?.toString()?.trim().orEmpty(),
                    extraItem = null,
                    extraItems = parseExtraItems(dialogBinding.editExtraItems.text?.toString().orEmpty()),
                    issueDate = LocalDate.parse(dialogBinding.editIssueDate.text?.toString()?.trim().orEmpty()),
                    dueDate = LocalDate.parse(dialogBinding.editDueDate.text?.toString()?.trim().orEmpty())
                )
            }.getOrElse {
                Snackbar.make(dialogBinding.root, it.message ?: getString(R.string.invoice_open_failed), Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            recreateInvoice(invoice, editedInput)
        }
        dialog.show()
    }

    private fun showStatusDialog(invoice: InvoiceRecord) {
        val statuses = listOf(
            InvoiceStatus.CREATED to getString(R.string.invoice_status_created),
            InvoiceStatus.SENT to getString(R.string.invoice_status_sent),
            InvoiceStatus.PAID to getString(R.string.invoice_status_paid),
            InvoiceStatus.OVERDUE to getString(R.string.invoice_status_overdue)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.invoice_filter_status)
            .setItems(statuses.map { it.second }.toTypedArray()) { _, which ->
                viewLifecycleOwner.lifecycleScope.launch {
                    DatabaseProvider.get(requireContext()).invoiceDao()
                        .updateStatus(invoice.invoiceId, statuses[which].first.value)
                }
            }
            .show()
    }

    private fun recreateInvoice(invoice: InvoiceRecord, inputOverride: InvoiceInput? = null) {
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
                    val input = inputOverride ?: InvoiceInputJson.decode(invoice.inputJson)
                    val generatedFile = InvoicePdfGenerator(appContext).generate(job, entries, period, input)
                    val archiveFile = InvoiceFiles.persist(appContext, generatedFile)
                    val totalAmount = entries.sumOf { entry ->
                        entry.workedHours() * entry.hourlyRate
                    } + input.allExtraItems().sumOf { it.total }
                    db.invoiceDao().update(
                        invoice.copy(
                            jobName = job.name,
                            totalAmount = totalAmount,
                            createdAtMillis = System.currentTimeMillis(),
                            fileName = archiveFile.name,
                            inputJson = InvoiceInputJson.encode(input)
                        )
                    )
                    archiveFile
                }
            }
            if (!isAdded) return@launch
            result.onSuccess { file ->
                Snackbar.make(binding.root, R.string.invoice_recreated, Snackbar.LENGTH_SHORT).show()
                showPdfReadyActions(file)
            }.onFailure {
                Snackbar.make(
                    binding.root,
                    it.message ?: getString(R.string.invoice_open_failed),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showExportZipDialog() {
        val years = allInvoices.map { it.periodYear }.distinct().sortedDescending()
        if (years.isEmpty()) {
            Snackbar.make(binding.root, R.string.invoice_archive_empty, Snackbar.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.invoice_export_zip)
            .setItems(years.map { it.toString() }.toTypedArray()) { _, which ->
                exportInvoicesZip(years[which])
            }
            .show()
    }

    private fun exportInvoicesZip(year: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val appContext = requireContext().applicationContext
                val invoices = allInvoices.filter { it.periodYear == year }
                val emptyMessage = getString(R.string.invoice_export_zip_empty)
                withContext(Dispatchers.IO) {
                    if (invoices.isEmpty()) error(emptyMessage)
                    val zipFile = File(appContext.cacheDir, "faktury-$year.zip")
                    var exportedCount = 0
                    ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                        invoices.forEach { invoice ->
                            val pdf = InvoiceFiles.fileFor(appContext, invoice.fileName)
                            if (!pdf.exists()) return@forEach
                            zip.putNextEntry(ZipEntry(pdf.name))
                            pdf.inputStream().use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                            exportedCount++
                        }
                    }
                    if (exportedCount == 0) error(emptyMessage)
                    zipFile
                }
            }
            if (!isAdded) return@launch
            result.onSuccess { file ->
                Snackbar.make(binding.root, R.string.invoice_export_zip_created, Snackbar.LENGTH_SHORT).show()
                shareZipFile(file)
            }.onFailure {
                Snackbar.make(
                    binding.root,
                    it.message ?: getString(R.string.invoice_export_zip_empty),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun invoiceFile(invoice: InvoiceRecord): File =
        InvoiceFiles.fileFor(requireContext(), invoice.fileName)

    private fun shareFile(file: File) {
        shareFile(file, "application/pdf", getString(R.string.invoice_share_title))
    }

    private fun shareZipFile(file: File) {
        shareFile(file, "application/zip", getString(R.string.invoice_export_zip))
    }

    private fun shareFile(file: File, mimeType: String, title: String) {
        val context = requireContext()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    private fun showPdfReadyActions(file: File) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.invoice_created)
            .setMessage(R.string.invoice_created_actions)
            .setNegativeButton(R.string.invoice_open) { _, _ -> openFile(file, "application/pdf") }
            .setPositiveButton(R.string.share) { _, _ -> shareFile(file) }
            .show()
    }

    private fun openFile(file: File, mimeType: String) {
        val context = requireContext()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { Snackbar.make(binding.root, R.string.invoice_open_failed, Snackbar.LENGTH_LONG).show() }
    }

    private fun parseExtraItems(text: String): List<InvoiceExtraItem> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("|").map { it.trim() }
                require(parts.size >= 4) { getString(R.string.invoice_extra_items) }
                InvoiceExtraItem(
                    name = parts[0],
                    quantity = parts[1].replace(',', '.').toDouble(),
                    unit = parts[2],
                    unitPrice = parts[3].replace(',', '.').toDouble()
                )
            }

    private fun InvoiceInput.allExtraItems(): List<InvoiceExtraItem> =
        extraItems.ifEmpty { listOfNotNull(extraItem) }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
