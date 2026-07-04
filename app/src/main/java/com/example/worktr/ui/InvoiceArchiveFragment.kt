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
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
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
import com.example.worktr.databinding.ItemInvoiceExtraPositionBinding
import com.example.worktr.ui.picker.DropdownUi
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.InvoiceFiles
import com.example.worktr.util.InvoiceExtraItem
import com.example.worktr.util.InvoiceInput
import com.example.worktr.util.InvoiceInputJson
import com.example.worktr.util.InvoiceLanguage
import com.example.worktr.util.InvoicePdfGenerator
import com.example.worktr.util.InvoiceRules
import com.example.worktr.util.InvoiceStatus
import com.example.worktr.util.workedHours
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
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

private const val INVOICE_DUE_DAYS = 15L

class InvoiceArchiveFragment : Fragment() {
    private var _binding: FragmentInvoiceArchiveBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: InvoiceArchiveAdapter
    private var allInvoices: List<InvoiceRecord> = emptyList()
    private lateinit var allLabel: String
    private lateinit var statusLabels: Map<String, InvoiceStatus?>
    private val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInvoiceArchiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val profile = ResponsiveUi.profile(requireContext())
        ResponsiveUi.applyOuterPadding(binding.invoiceArchiveContent, profile)
        setupMenu()
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
            onDelete = ::confirmDeleteInvoice
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

    private fun setupMenu() {
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_invoice_archive, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                    when (menuItem.itemId) {
                        R.id.action_export_invoice_zip -> {
                            showExportZipDialog()
                            true
                        }
                        else -> false
                    }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
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
        dialogBinding.editServiceQuantity.setText(input.serviceQuantity?.toString().orEmpty())
        dialogBinding.editServiceUnit.setText(input.serviceUnit)
        dialogBinding.editCurrency.setText(input.currency.ifBlank { "EUR" })
        val invoiceLanguages = InvoiceLanguage.entries.map { it.label }
        val selectedLanguage = InvoiceLanguage.fromCode(input.pdfLanguage)
        dialogBinding.inputPdfLanguage.setAdapter(DropdownUi.adapter(requireContext(), invoiceLanguages))
        dialogBinding.inputPdfLanguage.setText(selectedLanguage.label, false)
        DropdownUi.attach(dialogBinding.inputPdfLanguage) {
            invoiceLanguages.indexOf(dialogBinding.inputPdfLanguage.text?.toString().orEmpty()).takeIf { it >= 0 }
        }
        dialogBinding.inputPdfLanguage.setOnItemClickListener { _, _, _, _ ->
            if (dialogBinding.editServiceUnit.value().isBlank()) {
                dialogBinding.editServiceUnit.setText(
                    defaultServiceUnit(InvoiceLanguage.fromLabel(dialogBinding.inputPdfLanguage.text?.toString().orEmpty()))
                )
            }
        }
        input.allExtraItems().forEach { addExtraItemRow(dialogBinding.layoutExtraItemsContainer, it) }
        dialogBinding.buttonAddExtraItem.setOnClickListener {
            addExtraItemRow(dialogBinding.layoutExtraItemsContainer)
        }
        dialogBinding.editIssueDate.setText(input.issueDate.toString())
        fun updateDueDatePreview() {
            val issueDate = dialogBinding.editIssueDate.value().let { runCatching { LocalDate.parse(it) }.getOrNull() }
            dialogBinding.textDueDatePreview.text = if (issueDate != null) {
                getString(R.string.invoice_due_date_auto, issueDate.plusDays(INVOICE_DUE_DAYS).toString())
            } else {
                getString(R.string.invoice_due_date_auto, input.dueDate.toString())
            }
        }
        dialogBinding.editIssueDate.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateDueDatePreview()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateDueDatePreview()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialogBinding.buttonCancelInvoice.setOnClickListener { dialog.dismiss() }
        dialogBinding.buttonCreateInvoice.setOnClickListener {
            val editedInput = runCatching {
                val issueDate = LocalDate.parse(dialogBinding.editIssueDate.value())
                input.copy(
                    description = dialogBinding.editDescription.text?.toString()?.trim().orEmpty(),
                    serviceQuantity = dialogBinding.editServiceQuantity.text
                        ?.toString()
                        ?.trim()
                        .orEmpty()
                        .replace(',', '.')
                        .toDoubleOrNull()
                        ?.takeIf { it > 0.0 },
                    serviceUnit = dialogBinding.editServiceUnit.text?.toString()?.trim().orEmpty(),
                    extraItem = null,
                    extraItems = readExtraItemRows(dialogBinding.layoutExtraItemsContainer),
                    currency = dialogBinding.editCurrency.value().uppercase(Locale.ROOT).ifBlank { "EUR" },
                    pdfLanguage = InvoiceLanguage.fromLabel(dialogBinding.inputPdfLanguage.text?.toString().orEmpty()).code,
                    issueDate = issueDate,
                    dueDate = issueDate.plusDays(INVOICE_DUE_DAYS)
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
                    val totalAmount = InvoiceRules.calculateTotals(
                        entries = entries,
                        extraItems = input.allExtraItems(),
                        serviceQuantityOverride = input.serviceQuantity
                    ).total
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

    private fun addExtraItemRow(container: LinearLayout, item: InvoiceExtraItem? = null) {
        val rowBinding = ItemInvoiceExtraPositionBinding.inflate(layoutInflater, container, false)
        rowBinding.editExtraRowName.setText(item?.name.orEmpty())
        rowBinding.editExtraRowQuantity.setText(item?.quantity?.toString().orEmpty())
        rowBinding.editExtraRowUnit.setText(item?.unit.orEmpty())
        rowBinding.editExtraRowPrice.setText(item?.unitPrice?.toString().orEmpty())
        rowBinding.buttonRemoveExtraRow.setOnClickListener {
            container.removeView(rowBinding.root)
        }
        container.addView(rowBinding.root)
    }

    private fun readExtraItemRows(container: LinearLayout): List<InvoiceExtraItem> =
        (0 until container.childCount)
            .mapNotNull { index ->
                val row = container.getChildAt(index)
                val name = row.findViewById<TextInputEditText>(R.id.editExtraRowName)
                    ?.value()
                    .orEmpty()
                if (name.isBlank()) return@mapNotNull null
                InvoiceExtraItem(
                    name = name,
                    quantity = row.findViewById<TextInputEditText>(R.id.editExtraRowQuantity)
                        ?.value()
                        .orEmpty()
                        .ifBlank { "0" }
                        .toInvoiceDouble(0.0),
                    unit = row.findViewById<TextInputEditText>(R.id.editExtraRowUnit)
                        ?.value()
                        .orEmpty(),
                    unitPrice = row.findViewById<TextInputEditText>(R.id.editExtraRowPrice)
                        ?.value()
                        .orEmpty()
                        .ifBlank { "0" }
                        .toInvoiceDouble(0.0)
                )
            }

    private fun TextInputEditText.value(): String = text?.toString()?.trim().orEmpty()

    private fun String.toInvoiceDouble(defaultValue: Double): Double =
        replace(',', '.').toDoubleOrNull() ?: defaultValue

    private fun defaultServiceUnit(language: InvoiceLanguage): String =
        when (language) {
            InvoiceLanguage.SLOVAK -> "hod"
            InvoiceLanguage.UKRAINIAN -> "год"
            InvoiceLanguage.ENGLISH -> "hrs"
        }

    private fun InvoiceInput.allExtraItems(): List<InvoiceExtraItem> =
        extraItems.ifEmpty { listOfNotNull(extraItem) }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
