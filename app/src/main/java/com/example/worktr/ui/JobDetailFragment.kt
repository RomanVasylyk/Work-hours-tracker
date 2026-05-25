package com.example.worktr.ui

import android.content.Context
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
import com.example.worktr.data.InvoiceRecord
import com.example.worktr.data.JobRepository
import com.example.worktr.data.WorkEntryRepository
import com.example.worktr.data.Job as WorkJob
import com.example.worktr.databinding.DialogInvoiceBinding
import com.example.worktr.databinding.FragmentJobDetailBinding
import com.example.worktr.ui.picker.DropdownUi
import com.example.worktr.ui.picker.DynamicYearSpinner
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.CsvImporter
import com.example.worktr.util.ExcelExporter
import com.example.worktr.util.InvoiceExtraItem
import com.example.worktr.util.InvoiceFiles
import com.example.worktr.util.InvoiceInput
import com.example.worktr.util.InvoiceInputJson
import com.example.worktr.util.InvoicePdfGenerator
import com.example.worktr.util.workedHours
import com.example.worktr.viewmodel.JobDetailViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.transition.platform.MaterialSharedAxis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class JobDetailFragment : Fragment() {
    private var _binding: FragmentJobDetailBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<JobDetailFragmentArgs>()
    private lateinit var viewModel: com.example.worktr.viewmodel.JobDetailViewModel
    private lateinit var workRepository: WorkEntryRepository
    private lateinit var yearSpinner: DynamicYearSpinner
    private lateinit var monthLabels: List<String>
    private var currentWorkJob: WorkJob? = null
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
            currentWorkJob = job
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
        binding.buttonInvoice.setOnClickListener {
            showInvoicePreview()
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
        val invoiceParams = binding.buttonInvoice.layoutParams as LinearLayout.LayoutParams
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
            invoiceParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            invoiceParams.weight = 0f
            ResponsiveUi.setStartMargin(binding.buttonStats, 0)
            ResponsiveUi.setTopMargin(binding.buttonStats, ResponsiveUi.dp(requireContext(), 10))
            ResponsiveUi.setStartMargin(binding.buttonInvoice, 0)
            ResponsiveUi.setTopMargin(binding.buttonInvoice, ResponsiveUi.dp(requireContext(), 10))
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
                invoiceParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                addParams.weight = 0f
                statsParams.weight = 0f
                invoiceParams.weight = 0f
                ResponsiveUi.setStartMargin(binding.buttonStats, 0)
                ResponsiveUi.setTopMargin(binding.buttonStats, ResponsiveUi.dp(requireContext(), 10))
                ResponsiveUi.setStartMargin(binding.buttonInvoice, 0)
                ResponsiveUi.setTopMargin(binding.buttonInvoice, ResponsiveUi.dp(requireContext(), 10))
            } else {
                addParams.width = 0
                statsParams.width = 0
                invoiceParams.width = 0
                addParams.weight = 1.25f
                statsParams.weight = 0.75f
                invoiceParams.weight = 1f
                ResponsiveUi.setStartMargin(binding.buttonStats, ResponsiveUi.dp(requireContext(), 12))
                ResponsiveUi.setTopMargin(binding.buttonStats, 0)
                ResponsiveUi.setStartMargin(binding.buttonInvoice, ResponsiveUi.dp(requireContext(), 12))
                ResponsiveUi.setTopMargin(binding.buttonInvoice, 0)
            }
        }
        binding.layoutYearField.layoutParams = yearParams
        binding.layoutMonthField.layoutParams = monthParams
        binding.buttonAddEntry.layoutParams = addParams
        binding.buttonStats.layoutParams = statsParams
        binding.buttonInvoice.layoutParams = invoiceParams
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
                        getString(R.string.job_detail_hours_value, formatInvoiceQuantity(hours))
                    binding.textHeroMeta.text =
                        getString(
                            R.string.job_detail_meta,
                            dates.size,
                            formatInvoiceNumber(currentWorkJob?.hourlyRate ?: 0.0)
                        )
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
                        getString(
                            R.string.job_detail_salary_value,
                            formatInvoiceNumber(baseSalary + bonusNight + bonusSat + bonusSun + bonusHol)
                        )
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
            R.id.action_invoice -> {
                showInvoicePreview()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showInvoicePreview() {
        val job = currentWorkJob
        if (job == null) {
            Snackbar.make(binding.root, R.string.invoice_no_entries, Snackbar.LENGTH_LONG).show()
            return
        }

        val period = selectedInvoicePeriod()
        val start = period.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = period.atEndOfMonth()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli() - 1

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    workRepository.getEntriesForPeriod(job.jobId, start, end).first()
                }
            }
            if (!isAdded) return@launch

            result.onSuccess { entries ->
                if (entries.isEmpty()) {
                    Snackbar.make(binding.root, R.string.invoice_no_entries, Snackbar.LENGTH_LONG).show()
                    return@onSuccess
                }

                val draft = invoiceDraftFromPreferences(job, period)
                val hours = entries.sumOf { it.workedHours() }
                val total = entries.sumOf { it.workedHours() * it.hourlyRate }
                val unitPrice = if (hours > 0.0) total / hours else 0.0
                val summary = getString(
                    R.string.invoice_preview_summary,
                    slovakMonthLabel(period),
                    period.year,
                    formatInvoiceQuantity(hours),
                    formatInvoiceAmount(unitPrice, draft.currency),
                    formatInvoiceAmount(total, draft.currency)
                )

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.invoice_preview_title)
                    .setMessage(summary)
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.invoice_preview_edit) { _, _ -> showInvoiceDialog() }
                    .setPositiveButton(R.string.invoice_create_pdf) { _, _ ->
                        createInvoiceFromDraft(job, period, draft, binding.root)
                    }
                    .show()
            }.onFailure {
                Snackbar.make(
                    binding.root,
                    it.message ?: getString(R.string.invoice_no_entries),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showInvoiceDialog() {
        val job = currentWorkJob
        if (job == null) {
            Snackbar.make(binding.root, R.string.invoice_no_entries, Snackbar.LENGTH_LONG).show()
            return
        }

        val period = selectedInvoicePeriod()
        val prefs = requireContext().getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE)
        val invoiceBinding = DialogInvoiceBinding.inflate(layoutInflater)

        invoiceBinding.textInvoiceSummary.text = getString(
            R.string.invoice_summary,
            monthLabels[period.monthValue - 1],
            period.year
        )
        invoiceBinding.editInvoiceNumber.setText(defaultInvoiceNumber(period))

        invoiceBinding.editSupplierName.setText(prefs.getString(PREF_SUPPLIER_NAME, DEFAULT_SUPPLIER_NAME))
        invoiceBinding.editSupplierStreet.setText(prefs.getString(PREF_SUPPLIER_STREET, DEFAULT_SUPPLIER_STREET))
        invoiceBinding.editSupplierCity.setText(prefs.getString(PREF_SUPPLIER_CITY, DEFAULT_SUPPLIER_CITY))
        invoiceBinding.editSupplierZip.setText(prefs.getString(PREF_SUPPLIER_ZIP, DEFAULT_SUPPLIER_ZIP))
        invoiceBinding.editSupplierCountry.setText(prefs.getString(PREF_SUPPLIER_COUNTRY, DEFAULT_COUNTRY))
        invoiceBinding.editSupplierIco.setText(prefs.getString(PREF_SUPPLIER_ICO, DEFAULT_SUPPLIER_ICO))
        invoiceBinding.editIban.setText(prefs.getString(PREF_IBAN, ""))
        invoiceBinding.editBic.setText(prefs.getString(PREF_BIC, ""))

        invoiceBinding.editCustomerName.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_NAME, DEFAULT_CLIENT_NAME))
        invoiceBinding.editCustomerStreet.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_STREET, DEFAULT_CLIENT_STREET))
        invoiceBinding.editCustomerCity.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_CITY, DEFAULT_CLIENT_CITY))
        invoiceBinding.editCustomerZip.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_ZIP, DEFAULT_CLIENT_ZIP))
        invoiceBinding.editCustomerCountry.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_COUNTRY, DEFAULT_COUNTRY))
        invoiceBinding.editCustomerIco.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_ICO, DEFAULT_CLIENT_ICO))
        invoiceBinding.editCustomerDic.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_DIC, DEFAULT_CLIENT_DIC))
        invoiceBinding.editCustomerIcdph.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_ICDPH, DEFAULT_CLIENT_ICDPH))
        invoiceBinding.editDescription.setText(defaultInvoiceDescription(period))
        invoiceBinding.editExtraName.setText(prefs.getString(PREF_EXTRA_NAME, DEFAULT_EXTRA_NAME))
        invoiceBinding.editExtraQuantity.setText(prefs.getString(PREF_EXTRA_QUANTITY, DEFAULT_EXTRA_QUANTITY))
        invoiceBinding.editExtraUnit.setText(prefs.getString(PREF_EXTRA_UNIT, DEFAULT_EXTRA_UNIT))
        invoiceBinding.editExtraPrice.setText(prefs.getString(PREF_EXTRA_PRICE, DEFAULT_EXTRA_PRICE))
        invoiceBinding.checkExtraItem.setOnCheckedChangeListener { _, isChecked ->
            invoiceBinding.layoutExtraItem.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        invoiceBinding.layoutExtraItem.visibility = View.GONE
        invoiceBinding.editCurrency.setText(prefs.getString(PREF_CURRENCY, "EUR"))

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(invoiceBinding.root)
            .create()

        invoiceBinding.buttonCancelInvoice.setOnClickListener {
            dialog.dismiss()
        }
        invoiceBinding.buttonCreateInvoice.setOnClickListener {
            createInvoice(job, period, invoiceBinding, dialog)
        }

        dialog.show()
    }

    private fun createInvoice(
        job: WorkJob,
        period: YearMonth,
        invoiceBinding: DialogInvoiceBinding,
        dialog: android.app.Dialog
    ) {
        createInvoiceFromDraft(job, period, invoiceDraftFromBinding(invoiceBinding), invoiceBinding.root) {
            dialog.dismiss()
        }
    }

    private fun createInvoiceFromDraft(
        job: WorkJob,
        period: YearMonth,
        draft: InvoiceDraft,
        feedbackView: View,
        onSuccessBeforeShare: () -> Unit = {}
    ) {
        if (draft.invoiceNumber.isBlank() || draft.supplier.name.isBlank() || draft.iban.isBlank() || draft.customer.name.isBlank()) {
            Snackbar.make(feedbackView, R.string.invoice_required_fields, Snackbar.LENGTH_LONG).show()
            return
        }
        if (draft.bic.isBlank()) {
            Snackbar.make(feedbackView, R.string.invoice_bic_missing, Snackbar.LENGTH_LONG).show()
            return
        }

        val issueDate = LocalDate.now()
        val input = InvoiceInput(
            invoiceNumber = draft.invoiceNumber,
            supplier = draft.supplier.toSupplierLines(draft.iban),
            customer = draft.customer.toCustomerLines(),
            note = "",
            description = draft.description,
            extraItem = draft.extraItem,
            currency = draft.currency,
            iban = draft.iban,
            bic = draft.bic,
            variableSymbol = variableSymbol(draft.invoiceNumber),
            issueDate = issueDate,
            dueDate = issueDate.plusDays(15)
        )
        val start = period.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = period.atEndOfMonth()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli() - 1
        val appContext = requireContext().applicationContext

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val entries = withContext(Dispatchers.IO) {
                    workRepository.getEntriesForPeriod(job.jobId, start, end).first()
                }
                if (entries.isEmpty()) {
                    error(getString(R.string.invoice_no_entries))
                }

                withContext(Dispatchers.IO) {
                    val generatedFile = InvoicePdfGenerator(appContext).generate(job, entries, period, input)
                    val archiveFile = InvoiceFiles.persist(appContext, generatedFile)
                    val totalAmount = entries.sumOf { entry ->
                        entry.workedHours() * entry.hourlyRate
                    } + (draft.extraItem?.total ?: 0.0)
                    DatabaseProvider.get(appContext).invoiceDao().insert(
                        InvoiceRecord(
                            invoiceNumber = draft.invoiceNumber,
                            jobId = job.jobId,
                            jobName = job.name,
                            customerName = draft.customer.name,
                            periodYear = period.year,
                            periodMonth = period.monthValue,
                            totalAmount = totalAmount,
                            currency = draft.currency,
                            issueDate = issueDate.toString(),
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
                saveInvoicePreferences(job.jobId, period, draft)
                onSuccessBeforeShare()
                Snackbar.make(binding.root, R.string.invoice_created, Snackbar.LENGTH_SHORT).show()
                shareInvoice(file)
            }.onFailure {
                Snackbar.make(
                    feedbackView,
                    it.message ?: getString(R.string.invoice_no_entries),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun invoiceDraftFromBinding(invoiceBinding: DialogInvoiceBinding): InvoiceDraft {
        val invoiceNumber = invoiceBinding.editInvoiceNumber.text?.toString()?.trim().orEmpty()
        val supplier = InvoiceParty(
            name = invoiceBinding.editSupplierName.value(),
            street = invoiceBinding.editSupplierStreet.value(),
            city = invoiceBinding.editSupplierCity.value(),
            zip = invoiceBinding.editSupplierZip.value(),
            country = invoiceBinding.editSupplierCountry.value(),
            ico = invoiceBinding.editSupplierIco.value(),
            dic = "",
            icdph = "",
            info = ""
        )
        val iban = normalizeBankValue(invoiceBinding.editIban.text?.toString().orEmpty())
        val bic = normalizeBankValue(invoiceBinding.editBic.text?.toString().orEmpty())
            .ifBlank { inferSlovakBic(iban).orEmpty() }
        val customer = InvoiceParty(
            name = invoiceBinding.editCustomerName.value(),
            street = invoiceBinding.editCustomerStreet.value(),
            city = invoiceBinding.editCustomerCity.value(),
            zip = invoiceBinding.editCustomerZip.value(),
            country = invoiceBinding.editCustomerCountry.value(),
            ico = invoiceBinding.editCustomerIco.value(),
            dic = invoiceBinding.editCustomerDic.value(),
            icdph = invoiceBinding.editCustomerIcdph.value(),
            info = ""
        )
        val extraName = invoiceBinding.editExtraName.value().ifBlank { DEFAULT_EXTRA_NAME }
        val extraQuantity = invoiceBinding.editExtraQuantity.value().ifBlank { DEFAULT_EXTRA_QUANTITY }
        val extraUnit = invoiceBinding.editExtraUnit.value()
        val extraPrice = invoiceBinding.editExtraPrice.value().ifBlank { DEFAULT_EXTRA_PRICE }
        val extraItem = if (invoiceBinding.checkExtraItem.isChecked) {
            InvoiceExtraItem(
                name = extraName,
                quantity = extraQuantity.toInvoiceDouble(DEFAULT_EXTRA_QUANTITY.toDouble()),
                unit = extraUnit,
                unitPrice = extraPrice.toInvoiceDouble(DEFAULT_EXTRA_PRICE.toDouble())
            )
        } else {
            null
        }
        return InvoiceDraft(
            invoiceNumber = invoiceNumber,
            supplier = supplier,
            iban = iban,
            bic = bic,
            customer = customer,
            currency = invoiceBinding.editCurrency.text?.toString()?.trim()?.uppercase(Locale.ROOT).orEmpty().ifBlank { "EUR" },
            description = invoiceBinding.editDescription.text?.toString()?.trim().orEmpty(),
            extraItem = extraItem,
            extraName = extraName,
            extraQuantity = extraQuantity,
            extraUnit = extraUnit,
            extraPrice = extraPrice
        )
    }

    private fun invoiceDraftFromPreferences(job: WorkJob, period: YearMonth): InvoiceDraft {
        val prefs = requireContext().getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE)
        val iban = normalizeBankValue(prefs.getString(PREF_IBAN, "").orEmpty())
        val bic = normalizeBankValue(prefs.getString(PREF_BIC, "").orEmpty())
            .ifBlank { inferSlovakBic(iban).orEmpty() }
        return InvoiceDraft(
            invoiceNumber = defaultInvoiceNumber(period),
            supplier = InvoiceParty(
                name = prefs.getString(PREF_SUPPLIER_NAME, DEFAULT_SUPPLIER_NAME).orEmpty(),
                street = prefs.getString(PREF_SUPPLIER_STREET, DEFAULT_SUPPLIER_STREET).orEmpty(),
                city = prefs.getString(PREF_SUPPLIER_CITY, DEFAULT_SUPPLIER_CITY).orEmpty(),
                zip = prefs.getString(PREF_SUPPLIER_ZIP, DEFAULT_SUPPLIER_ZIP).orEmpty(),
                country = prefs.getString(PREF_SUPPLIER_COUNTRY, DEFAULT_COUNTRY).orEmpty(),
                ico = prefs.getString(PREF_SUPPLIER_ICO, DEFAULT_SUPPLIER_ICO).orEmpty(),
                dic = "",
                icdph = "",
                info = ""
            ),
            iban = iban,
            bic = bic,
            customer = InvoiceParty(
                name = clientPrefValue(prefs, job.jobId, PREF_CLIENT_NAME, DEFAULT_CLIENT_NAME),
                street = clientPrefValue(prefs, job.jobId, PREF_CLIENT_STREET, DEFAULT_CLIENT_STREET),
                city = clientPrefValue(prefs, job.jobId, PREF_CLIENT_CITY, DEFAULT_CLIENT_CITY),
                zip = clientPrefValue(prefs, job.jobId, PREF_CLIENT_ZIP, DEFAULT_CLIENT_ZIP),
                country = clientPrefValue(prefs, job.jobId, PREF_CLIENT_COUNTRY, DEFAULT_COUNTRY),
                ico = clientPrefValue(prefs, job.jobId, PREF_CLIENT_ICO, DEFAULT_CLIENT_ICO),
                dic = clientPrefValue(prefs, job.jobId, PREF_CLIENT_DIC, DEFAULT_CLIENT_DIC),
                icdph = clientPrefValue(prefs, job.jobId, PREF_CLIENT_ICDPH, DEFAULT_CLIENT_ICDPH),
                info = ""
            ),
            currency = prefs.getString(PREF_CURRENCY, "EUR").orEmpty().ifBlank { "EUR" },
            description = defaultInvoiceDescription(period),
            extraItem = null,
            extraName = prefs.getString(PREF_EXTRA_NAME, DEFAULT_EXTRA_NAME).orEmpty(),
            extraQuantity = prefs.getString(PREF_EXTRA_QUANTITY, DEFAULT_EXTRA_QUANTITY).orEmpty(),
            extraUnit = prefs.getString(PREF_EXTRA_UNIT, DEFAULT_EXTRA_UNIT).orEmpty(),
            extraPrice = prefs.getString(PREF_EXTRA_PRICE, DEFAULT_EXTRA_PRICE).orEmpty()
        )
    }

    private fun saveInvoicePreferences(jobId: Int, period: YearMonth, draft: InvoiceDraft) {
        val generatedSequence = sequenceFromInvoiceNumber(period, draft.invoiceNumber)
        requireContext().getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_SUPPLIER_NAME, draft.supplier.name)
            .putString(PREF_SUPPLIER_STREET, draft.supplier.street)
            .putString(PREF_SUPPLIER_CITY, draft.supplier.city)
            .putString(PREF_SUPPLIER_ZIP, draft.supplier.zip)
            .putString(PREF_SUPPLIER_COUNTRY, draft.supplier.country)
            .putString(PREF_SUPPLIER_ICO, draft.supplier.ico)
            .putString(PREF_IBAN, draft.iban)
            .putString(PREF_BIC, draft.bic)
            .putString(clientPref(jobId, PREF_CLIENT_NAME), draft.customer.name)
            .putString(clientPref(jobId, PREF_CLIENT_STREET), draft.customer.street)
            .putString(clientPref(jobId, PREF_CLIENT_CITY), draft.customer.city)
            .putString(clientPref(jobId, PREF_CLIENT_ZIP), draft.customer.zip)
            .putString(clientPref(jobId, PREF_CLIENT_COUNTRY), draft.customer.country)
            .putString(clientPref(jobId, PREF_CLIENT_ICO), draft.customer.ico)
            .putString(clientPref(jobId, PREF_CLIENT_DIC), draft.customer.dic)
            .putString(clientPref(jobId, PREF_CLIENT_ICDPH), draft.customer.icdph)
            .putString(PREF_EXTRA_NAME, draft.extraName.ifBlank { DEFAULT_EXTRA_NAME })
            .putString(PREF_EXTRA_QUANTITY, draft.extraQuantity.ifBlank { DEFAULT_EXTRA_QUANTITY })
            .putString(PREF_EXTRA_UNIT, draft.extraUnit)
            .putString(PREF_EXTRA_PRICE, draft.extraPrice.ifBlank { DEFAULT_EXTRA_PRICE })
            .putString(PREF_CURRENCY, draft.currency)
            .putInt(sequencePrefKey(period), maxOf(currentInvoiceSequence(period), generatedSequence))
            .apply()
    }

    private fun selectedMonth(): Int {
        val selected = binding.inputMonth.text?.toString().orEmpty()
        return monthLabels.indexOf(selected).let { index ->
            if (index >= 0) index + 1 else LocalDate.now().monthValue
        }
    }

    private fun selectedYear(): Int = yearSpinner.getSelectedYear() ?: LocalDate.now().year

    private fun selectedInvoicePeriod(): YearMonth {
        val lastClosedMonth = YearMonth.from(LocalDate.now()).minusMonths(1)
        val selected = YearMonth.of(selectedYear(), selectedMonth())
        return if (selected > lastClosedMonth) lastClosedMonth else selected
    }

    private fun defaultInvoiceNumber(period: YearMonth): String =
        "${period.year}${period.monthValue.toString().padStart(2, '0')}${(currentInvoiceSequence(period) + 1).toString().padStart(2, '0')}"

    private fun currentInvoiceSequence(period: YearMonth): Int =
        requireContext().getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE)
            .getInt(sequencePrefKey(period), 0)

    private fun sequencePrefKey(period: YearMonth): String =
        "invoice_sequence_${period.year}${period.monthValue.toString().padStart(2, '0')}"

    private fun sequenceFromInvoiceNumber(period: YearMonth, invoiceNumber: String): Int {
        val prefix = "${period.year}${period.monthValue.toString().padStart(2, '0')}"
        val digits = invoiceNumber.filter { it.isDigit() }
        return digits.removePrefix(prefix).toIntOrNull() ?: (currentInvoiceSequence(period) + 1)
    }

    private fun slovakMonthName(period: YearMonth): String =
        period.month.getDisplayName(TextStyle.FULL, Locale("sk", "SK"))

    private fun slovakMonthLabel(period: YearMonth): String =
        slovakMonthName(period).replaceFirstChar { it.uppercase(Locale("sk", "SK")) }

    private fun defaultInvoiceDescription(period: YearMonth): String =
        "Fakturujem Vám za vykonanú prácu – kontrolu kvality v mesiaci ${slovakMonthName(period)}"

    private fun formatInvoiceQuantity(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else formatInvoiceNumber(value)

    private fun formatInvoiceAmount(value: Double, currency: String): String =
        "${formatInvoiceNumber(value)} ${currency.ifBlank { "EUR" }}"

    private fun formatInvoiceNumber(value: Double): String =
        NumberFormat.getNumberInstance(Locale("sk", "SK")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(value)

    private fun normalizeBankValue(value: String): String =
        value.replace(Regex("\\s+"), "").uppercase(Locale.ROOT)

    private fun variableSymbol(invoiceNumber: String): String =
        invoiceNumber.filter { it.isDigit() }.take(10).ifBlank { "0" }

    private fun inferSlovakBic(iban: String): String? {
        val normalized = normalizeBankValue(iban)
        if (!normalized.startsWith("SK") || normalized.length < 8) return null
        return SLOVAK_BIC_BY_BANK_CODE[normalized.substring(4, 8)]
    }

    private fun clientPref(jobId: Int, key: String): String = "${PREF_CLIENT_PREFIX}_${jobId}_$key"

    private fun clientPrefValue(
        prefs: android.content.SharedPreferences,
        jobId: Int,
        key: String,
        defaultValue: String
    ): String =
        prefs.getString(clientPref(jobId, key), null)
            ?: prefs.getString(key, defaultValue)
            ?: defaultValue

    private fun shareInvoice(file: File) {
        val context = requireContext()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.invoice_share_title)))
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

    private fun TextInputEditText.value(): String = text?.toString()?.trim().orEmpty()

    private fun String.toInvoiceDouble(defaultValue: Double): Double =
        replace(',', '.').toDoubleOrNull() ?: defaultValue

    private data class InvoiceParty(
        val name: String,
        val street: String,
        val city: String,
        val zip: String,
        val country: String,
        val ico: String,
        val dic: String,
        val icdph: String,
        val info: String
    ) {
        fun toSupplierLines(iban: String): String =
            listOfNotNull(
                name,
                street,
                zipCityLine(),
                country,
                labeled("IČO", ico),
                "Neplatiteľ DPH",
                labeled("IBAN", iban)
            ).filter { it.isNotBlank() }.joinToString("\n")

        fun toCustomerLines(): String =
            listOfNotNull(
                name,
                street,
                zipCityLine(),
                country,
                labeled("IČO", ico),
                labeled("DIČ", dic),
                labeled("IČDPH", icdph)
            ).filter { it.isNotBlank() }.joinToString("\n")

        private fun zipCityLine(): String = listOf(zip, city).filter { it.isNotBlank() }.joinToString(" ")

        private fun labeled(label: String, value: String): String? =
            value.takeIf { it.isNotBlank() }?.let { "$label: $it" }
    }

    private data class InvoiceDraft(
        val invoiceNumber: String,
        val supplier: InvoiceParty,
        val iban: String,
        val bic: String,
        val customer: InvoiceParty,
        val currency: String,
        val description: String,
        val extraItem: InvoiceExtraItem?,
        val extraName: String,
        val extraQuantity: String,
        val extraUnit: String,
        val extraPrice: String
    )

    private companion object {
        const val INVOICE_PREFS = "invoice_prefs"
        const val PREF_SUPPLIER_NAME = "supplier_name"
        const val PREF_SUPPLIER_STREET = "supplier_street"
        const val PREF_SUPPLIER_CITY = "supplier_city"
        const val PREF_SUPPLIER_ZIP = "supplier_zip"
        const val PREF_SUPPLIER_COUNTRY = "supplier_country"
        const val PREF_SUPPLIER_ICO = "supplier_ico"
        const val PREF_IBAN = "iban"
        const val PREF_BIC = "bic"
        const val PREF_CLIENT_PREFIX = "client"
        const val PREF_CLIENT_NAME = "name"
        const val PREF_CLIENT_STREET = "street"
        const val PREF_CLIENT_CITY = "city"
        const val PREF_CLIENT_ZIP = "zip"
        const val PREF_CLIENT_COUNTRY = "country"
        const val PREF_CLIENT_ICO = "ico"
        const val PREF_CLIENT_DIC = "dic"
        const val PREF_CLIENT_ICDPH = "icdph"
        const val PREF_CLIENT_INFO = "info"
        const val PREF_EXTRA_NAME = "extra_name"
        const val PREF_EXTRA_QUANTITY = "extra_quantity"
        const val PREF_EXTRA_UNIT = "extra_unit"
        const val PREF_EXTRA_PRICE = "extra_price"
        const val PREF_CURRENCY = "currency"

        const val DEFAULT_SUPPLIER_NAME = "Ukážkový dodávateľ"
        const val DEFAULT_SUPPLIER_STREET = "Hlavná 12"
        const val DEFAULT_SUPPLIER_CITY = "Nitra"
        const val DEFAULT_SUPPLIER_ZIP = "94901"
        const val DEFAULT_SUPPLIER_ICO = "12345678"
        const val DEFAULT_COUNTRY = "Slovensko"

        const val DEFAULT_CLIENT_NAME = "Demo klient s.r.o."
        const val DEFAULT_CLIENT_STREET = "Obchodná 24"
        const val DEFAULT_CLIENT_CITY = "Bratislava"
        const val DEFAULT_CLIENT_ZIP = "81106"
        const val DEFAULT_CLIENT_ICO = "87654321"
        const val DEFAULT_CLIENT_DIC = "2120000000"
        const val DEFAULT_CLIENT_ICDPH = "SK2120000000"
        const val DEFAULT_CLIENT_INFO =
            "Ukážkový text pre údaje odberateľa."

        const val DEFAULT_EXTRA_NAME = "Doprava"
        const val DEFAULT_EXTRA_QUANTITY = "1"
        const val DEFAULT_EXTRA_UNIT = ""
        const val DEFAULT_EXTRA_PRICE = "10"

        val SLOVAK_BIC_BY_BANK_CODE = mapOf(
            "0200" to "SUBASKBX",
            "0720" to "NBSBSKBX",
            "0900" to "GIBASKBX",
            "1100" to "TATRSKBX",
            "1111" to "UNCRSKBX",
            "3000" to "SLZBSKBA",
            "3100" to "LUBASKBX",
            "5200" to "OTPVSKBX",
            "5600" to "KOMASK2X",
            "5900" to "PRVASKBA",
            "6500" to "POBNSKBA",
            "7300" to "INGBSKBX",
            "7500" to "CEKOSKBX",
            "7930" to "WUSTSKBA",
            "8050" to "COBASKBX",
            "8120" to "BSLOSK22",
            "8130" to "CITISKBA",
            "8170" to "KBSPSKBX",
            "8180" to "SPSRSKBA",
            "8330" to "FIOZSKBA",
            "8360" to "BREXSKBX",
            "8400" to "BFKKSKBB",
            "8420" to "BFKKSKBB",
            "9950" to "TPAYSKBX"
        )
    }
}
