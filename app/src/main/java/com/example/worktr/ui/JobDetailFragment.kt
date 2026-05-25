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
import com.example.worktr.util.InvoiceInput
import com.example.worktr.util.InvoicePdfGenerator
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
            ResponsiveUi.setStartMargin(binding.buttonStats, 0)
            ResponsiveUi.setTopMargin(binding.buttonStats, ResponsiveUi.dp(requireContext(), 10))
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
                addParams.weight = 0f
                statsParams.weight = 0f
                ResponsiveUi.setStartMargin(binding.buttonStats, 0)
                ResponsiveUi.setTopMargin(binding.buttonStats, ResponsiveUi.dp(requireContext(), 10))
            } else {
                addParams.width = 0
                statsParams.width = 0
                addParams.weight = 1.25f
                statsParams.weight = 0.75f
                ResponsiveUi.setStartMargin(binding.buttonStats, ResponsiveUi.dp(requireContext(), 12))
                ResponsiveUi.setTopMargin(binding.buttonStats, 0)
            }
        }
        binding.layoutYearField.layoutParams = yearParams
        binding.layoutMonthField.layoutParams = monthParams
        binding.buttonAddEntry.layoutParams = addParams
        binding.buttonStats.layoutParams = statsParams
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
                        getString(R.string.hours_worked_format, hours)
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
                        getString(R.string.salary_format, baseSalary + bonusNight + bonusSat + bonusSun + bonusHol)
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
                showInvoiceDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
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

        invoiceBinding.editCustomerName.setText(prefs.getString(clientPref(job.jobId, PREF_CLIENT_NAME), DEFAULT_CLIENT_NAME))
        invoiceBinding.editCustomerStreet.setText(prefs.getString(clientPref(job.jobId, PREF_CLIENT_STREET), DEFAULT_CLIENT_STREET))
        invoiceBinding.editCustomerCity.setText(prefs.getString(clientPref(job.jobId, PREF_CLIENT_CITY), DEFAULT_CLIENT_CITY))
        invoiceBinding.editCustomerZip.setText(prefs.getString(clientPref(job.jobId, PREF_CLIENT_ZIP), DEFAULT_CLIENT_ZIP))
        invoiceBinding.editCustomerCountry.setText(prefs.getString(clientPref(job.jobId, PREF_CLIENT_COUNTRY), DEFAULT_COUNTRY))
        invoiceBinding.editCustomerIco.setText(prefs.getString(clientPref(job.jobId, PREF_CLIENT_ICO), DEFAULT_CLIENT_ICO))
        invoiceBinding.editCustomerDic.setText(prefs.getString(clientPref(job.jobId, PREF_CLIENT_DIC), DEFAULT_CLIENT_DIC))
        invoiceBinding.editCustomerIcdph.setText(prefs.getString(clientPref(job.jobId, PREF_CLIENT_ICDPH), DEFAULT_CLIENT_ICDPH))
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
        if (invoiceNumber.isBlank() || supplier.name.isBlank() || iban.isBlank() || customer.name.isBlank()) {
            Snackbar.make(invoiceBinding.root, R.string.invoice_required_fields, Snackbar.LENGTH_LONG).show()
            return
        }
        if (bic.isBlank()) {
            Snackbar.make(invoiceBinding.root, R.string.invoice_bic_missing, Snackbar.LENGTH_LONG).show()
            return
        }

        val currency = invoiceBinding.editCurrency.text?.toString()?.trim()?.uppercase(Locale.ROOT).orEmpty().ifBlank { "EUR" }
        val description = invoiceBinding.editDescription.text?.toString()?.trim().orEmpty()
        val extraItem = if (invoiceBinding.checkExtraItem.isChecked) {
            InvoiceExtraItem(
                name = invoiceBinding.editExtraName.value().ifBlank { DEFAULT_EXTRA_NAME },
                quantity = invoiceBinding.editExtraQuantity.value().toInvoiceDouble(DEFAULT_EXTRA_QUANTITY.toDouble()),
                unit = invoiceBinding.editExtraUnit.value(),
                unitPrice = invoiceBinding.editExtraPrice.value().toInvoiceDouble(DEFAULT_EXTRA_PRICE.toDouble())
            )
        } else {
            null
        }
        val issueDate = LocalDate.now()
        val input = InvoiceInput(
            invoiceNumber = invoiceNumber,
            supplier = supplier.toSupplierLines(iban),
            customer = customer.toCustomerLines(),
            note = "",
            description = description,
            extraItem = extraItem,
            currency = currency,
            iban = iban,
            bic = bic,
            variableSymbol = variableSymbol(invoiceNumber),
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
                    InvoicePdfGenerator(appContext).generate(job, entries, period, input)
                }
            }
            if (!isAdded) return@launch

            result.onSuccess { file ->
                val generatedSequence = sequenceFromInvoiceNumber(period, invoiceNumber)
                requireContext().getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE).edit()
                    .putString(PREF_SUPPLIER_NAME, supplier.name)
                    .putString(PREF_SUPPLIER_STREET, supplier.street)
                    .putString(PREF_SUPPLIER_CITY, supplier.city)
                    .putString(PREF_SUPPLIER_ZIP, supplier.zip)
                    .putString(PREF_SUPPLIER_COUNTRY, supplier.country)
                    .putString(PREF_SUPPLIER_ICO, supplier.ico)
                    .putString(PREF_IBAN, iban)
                    .putString(PREF_BIC, bic)
                    .putString(clientPref(job.jobId, PREF_CLIENT_NAME), customer.name)
                    .putString(clientPref(job.jobId, PREF_CLIENT_STREET), customer.street)
                    .putString(clientPref(job.jobId, PREF_CLIENT_CITY), customer.city)
                    .putString(clientPref(job.jobId, PREF_CLIENT_ZIP), customer.zip)
                    .putString(clientPref(job.jobId, PREF_CLIENT_COUNTRY), customer.country)
                    .putString(clientPref(job.jobId, PREF_CLIENT_ICO), customer.ico)
                    .putString(clientPref(job.jobId, PREF_CLIENT_DIC), customer.dic)
                    .putString(clientPref(job.jobId, PREF_CLIENT_ICDPH), customer.icdph)
                    .putString(PREF_EXTRA_NAME, invoiceBinding.editExtraName.value().ifBlank { DEFAULT_EXTRA_NAME })
                    .putString(PREF_EXTRA_QUANTITY, invoiceBinding.editExtraQuantity.value().ifBlank { DEFAULT_EXTRA_QUANTITY })
                    .putString(PREF_EXTRA_UNIT, invoiceBinding.editExtraUnit.value())
                    .putString(PREF_EXTRA_PRICE, invoiceBinding.editExtraPrice.value().ifBlank { DEFAULT_EXTRA_PRICE })
                    .putString(PREF_CURRENCY, currency)
                    .putInt(sequencePrefKey(period), maxOf(currentInvoiceSequence(period), generatedSequence))
                    .apply()
                dialog.dismiss()
                Snackbar.make(binding.root, R.string.invoice_created, Snackbar.LENGTH_SHORT).show()
                shareInvoice(file)
            }.onFailure {
                Snackbar.make(
                    invoiceBinding.root,
                    it.message ?: getString(R.string.invoice_no_entries),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
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

    private fun defaultInvoiceDescription(period: YearMonth): String =
        "Fakturujem Vám za vykonanú prácu – kontrolu kvality v mesiaci ${slovakMonthName(period)}"

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

        const val DEFAULT_SUPPLIER_NAME = "Ivan Vasylyk"
        const val DEFAULT_SUPPLIER_STREET = "Baničova 519/3"
        const val DEFAULT_SUPPLIER_CITY = "Nitra"
        const val DEFAULT_SUPPLIER_ZIP = "94911"
        const val DEFAULT_SUPPLIER_ICO = "54261635"
        const val DEFAULT_COUNTRY = "Slovensko"

        const val DEFAULT_CLIENT_NAME = "LOKALPRO s.r.o."
        const val DEFAULT_CLIENT_STREET = "Karpatské námestie 10A"
        const val DEFAULT_CLIENT_CITY = "Bratislava - Rača"
        const val DEFAULT_CLIENT_ZIP = "83106"
        const val DEFAULT_CLIENT_ICO = "52728595"
        const val DEFAULT_CLIENT_DIC = "2121281294"
        const val DEFAULT_CLIENT_ICDPH = "SK2121281294"
        const val DEFAULT_CLIENT_INFO =
            "Spoločnosť zapísal Mestský súd Bratislava III do Obchodného registra pod číslom Sro/143648/B dňa 3.3.2020."

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
