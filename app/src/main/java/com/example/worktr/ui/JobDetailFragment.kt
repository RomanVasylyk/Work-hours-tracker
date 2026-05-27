package com.example.worktr.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.worktr.R
import com.example.worktr.data.Client
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.InvoiceRecord
import com.example.worktr.data.JobRepository
import com.example.worktr.data.WorkEntryRepository
import com.example.worktr.data.Job as WorkJob
import com.example.worktr.databinding.DialogInvoiceBinding
import com.example.worktr.databinding.FragmentJobDetailBinding
import com.example.worktr.databinding.ItemInvoiceExtraPositionBinding
import com.example.worktr.ui.picker.DropdownUi
import com.example.worktr.ui.picker.DynamicYearSpinner
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.CsvImporter
import com.example.worktr.util.ExcelExporter
import com.example.worktr.util.InvoiceExtraItem
import com.example.worktr.util.InvoiceFiles
import com.example.worktr.util.InvoiceInput
import com.example.worktr.util.InvoiceInputJson
import com.example.worktr.util.InvoiceLanguage
import com.example.worktr.util.InvoicePdfGenerator
import com.example.worktr.util.InvoiceRules
import com.example.worktr.util.ClientDefaults
import com.example.worktr.util.PaymentValidation
import com.example.worktr.util.PaymentValidationResult
import com.example.worktr.util.SecureInvoicePrefs
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
        setupMenu()
        setupCalendarResults()
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

    private fun setupMenu() {
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_job_detail, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                    when (menuItem.itemId) {
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
                        R.id.action_calendar -> {
                            CalendarDialogFragment.newInstance(
                                args.jobId,
                                CalendarDialogFragment.REQUEST_JOB_DETAIL
                            )
                                .show(parentFragmentManager, "calendar")
                            true
                        }
                        else -> false
                    }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }

    private fun setupCalendarResults() {
        setFragmentResultListener(CalendarDialogFragment.dateResultKey(CalendarDialogFragment.REQUEST_JOB_DETAIL)) { _, bundle ->
            openAddEntryWithDates(longArrayOf(bundle.getLong("date")))
        }
        setFragmentResultListener(CalendarDialogFragment.datesResultKey(CalendarDialogFragment.REQUEST_JOB_DETAIL)) { _, bundle ->
            openAddEntryWithDates(bundle.getLongArray("dates") ?: longArrayOf())
        }
    }

    private fun openAddEntryWithDates(dates: LongArray) {
        if (dates.isEmpty()) return
        findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.set(AddEntryFragment.ENTRY_DATES_KEY, dates)
        findNavController().navigate(
            JobDetailFragmentDirections.actionJobDetailFragmentToAddEntryFragment(args.jobId)
        )
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
                    val entries = workRepository.getEntriesForPeriod(job.jobId, start, end).first()
                    val database = DatabaseProvider.get(requireContext().applicationContext)
                    val existingInvoice = database
                        .invoiceDao()
                        .getInvoiceByNumber(defaultInvoiceNumber(period))
                    val client = database.clientDao().getClientForJob(job.jobId)
                    Triple(entries, existingInvoice, client)
                }
            }
            if (!isAdded) return@launch

            result.onSuccess { (entries, existingInvoice, client) ->
                if (entries.isEmpty()) {
                    Snackbar.make(binding.root, R.string.invoice_no_entries, Snackbar.LENGTH_LONG).show()
                    return@onSuccess
                }

                val draft = invoiceDraftFromPreferences(job, period, client)
                val hours = entries.sumOf { it.workedHours() }
                val automaticExtras = InvoiceRules.bonusExtraItems(entries, draft.pdfLanguage)
                val calculation = InvoiceRules.calculateTotals(
                    entries = entries,
                    extraItems = draft.extraItems + automaticExtras,
                    serviceQuantityOverride = draft.serviceQuantity
                )
                val serviceQuantity = draft.serviceQuantity?.takeIf { it > 0.0 } ?: hours
                val unitPrice = calculation.unitPrice
                val paymentValidation = PaymentValidation.validatePayment(draft.iban, draft.bic)
                val checklist = listOf(
                    ChecklistItem(getString(R.string.invoice_checklist_iban), paymentValidation !is PaymentValidationResult.InvalidIban),
                    ChecklistItem(getString(R.string.invoice_checklist_bic), paymentValidation is PaymentValidationResult.Valid),
                    ChecklistItem(getString(R.string.invoice_checklist_client), draft.customer.name.isNotBlank()),
                    ChecklistItem(getString(R.string.invoice_checklist_hours), hours > 0.0),
                    ChecklistItem(getString(R.string.invoice_checklist_rate), unitPrice > 0.0),
                    ChecklistItem(getString(R.string.invoice_checklist_number), existingInvoice == null)
                )
                val checklistText = checklist.joinToString("\n") { item ->
                    getString(
                        if (item.ok) R.string.invoice_checklist_ok else R.string.invoice_checklist_bad,
                        item.label
                    )
                }
                val canCreate = checklist.all { it.ok }
                val summary = getString(
                    R.string.invoice_preview_summary,
                    draft.invoiceNumber,
                    slovakMonthLabel(period),
                    period.year,
                    draft.customer.name,
                    formatInvoiceQuantity(serviceQuantity),
                    formatInvoiceAmount(unitPrice, draft.currency),
                    formatInvoiceAmount(calculation.total, draft.currency)
                ) + "\n\n$checklistText"

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.invoice_preview_title)
                    .setMessage(summary)
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.invoice_preview_edit) { _, _ -> showInvoiceDialog() }
                    .setPositiveButton(R.string.invoice_create_pdf) { _, _ ->
                        if (canCreate) {
                            createInvoiceFromDraft(job, period, draft, binding.root)
                        } else {
                            Snackbar.make(binding.root, R.string.invoice_checklist_fix, Snackbar.LENGTH_LONG).show()
                        }
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
        val start = period.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = period.atEndOfMonth()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli() - 1
        viewLifecycleOwner.lifecycleScope.launch {
            val (client, serviceHours) = withContext(Dispatchers.IO) {
                val database = DatabaseProvider.get(requireContext().applicationContext)
                val entries = workRepository.getEntriesForPeriod(job.jobId, start, end).first()
                database.clientDao().getClientForJob(job.jobId) to entries.sumOf { it.workedHours() }
            }
            if (!isAdded) return@launch
            showInvoiceDialog(job, client, period, serviceHours)
        }
    }

    private fun showInvoiceDialog(job: WorkJob, client: Client?, period: YearMonth, serviceHours: Double) {
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
        invoiceBinding.editIban.setText(SecureInvoicePrefs.readIban(requireContext()))
        invoiceBinding.editBic.setText(SecureInvoicePrefs.readBic(requireContext()))

        val invoiceClient = client ?: clientFromPreferences(prefs, job.jobId)
        invoiceBinding.editCustomerName.setText(invoiceClient.name)
        invoiceBinding.editCustomerStreet.setText(invoiceClient.street)
        invoiceBinding.editCustomerCity.setText(invoiceClient.city)
        invoiceBinding.editCustomerZip.setText(invoiceClient.zip)
        invoiceBinding.editCustomerCountry.setText(invoiceClient.country)
        invoiceBinding.editCustomerIco.setText(invoiceClient.ico)
        invoiceBinding.editCustomerDic.setText(invoiceClient.dic)
        invoiceBinding.editCustomerIcdph.setText(invoiceClient.icdph)
        invoiceBinding.editDescription.setText(invoiceDescriptionFromClient(invoiceClient, period, selectedPdfLanguage(prefs)))
        invoiceBinding.editServiceQuantity.setText(formatInvoiceQuantity(serviceHours))
        invoiceBinding.editServiceUnit.setText(defaultServiceUnit(selectedPdfLanguage(prefs)))
        invoiceBinding.editExtraName.setText(prefs.getString(PREF_EXTRA_NAME, DEFAULT_EXTRA_NAME))
        invoiceBinding.editExtraQuantity.setText(prefs.getString(PREF_EXTRA_QUANTITY, DEFAULT_EXTRA_QUANTITY))
        invoiceBinding.editExtraUnit.setText(prefs.getString(PREF_EXTRA_UNIT, DEFAULT_EXTRA_UNIT))
        invoiceBinding.editExtraPrice.setText(prefs.getString(PREF_EXTRA_PRICE, DEFAULT_EXTRA_PRICE))
        invoiceBinding.checkExtraItem.setOnCheckedChangeListener { _, isChecked ->
            invoiceBinding.layoutExtraItem.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        invoiceBinding.buttonAddExtraItem.setOnClickListener {
            addExtraItemRow(invoiceBinding.layoutExtraItemsContainer)
        }
        invoiceBinding.layoutExtraItem.visibility = View.GONE
        invoiceBinding.editCurrency.setText(prefs.getString(PREF_CURRENCY, "EUR"))
        val invoiceLanguages = InvoiceLanguage.entries.map { it.label }
        invoiceBinding.inputPdfLanguage.setAdapter(DropdownUi.adapter(requireContext(), invoiceLanguages))
        invoiceBinding.inputPdfLanguage.setText(selectedPdfLanguage(prefs).label, false)
        DropdownUi.attach(invoiceBinding.inputPdfLanguage) {
            invoiceLanguages.indexOf(invoiceBinding.inputPdfLanguage.text?.toString().orEmpty()).takeIf { it >= 0 }
        }

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
        val validPayment = when (val paymentValidation = PaymentValidation.validatePayment(draft.iban, draft.bic)) {
            PaymentValidationResult.InvalidIban -> {
                Snackbar.make(feedbackView, R.string.invoice_iban_invalid, Snackbar.LENGTH_LONG).show()
                return
            }
            PaymentValidationResult.MissingBic -> {
                Snackbar.make(feedbackView, R.string.invoice_bic_missing, Snackbar.LENGTH_LONG).show()
                return
            }
            PaymentValidationResult.InvalidBic -> {
                Snackbar.make(feedbackView, R.string.invoice_bic_invalid, Snackbar.LENGTH_LONG).show()
                return
            }
            is PaymentValidationResult.Valid -> paymentValidation
        }

        val issueDate = LocalDate.now()
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
                    val invoiceExtraItems = draft.extraItems +
                        InvoiceRules.bonusExtraItems(entries, draft.pdfLanguage)
                    val input = InvoiceInput(
                        invoiceNumber = draft.invoiceNumber,
                        supplier = draft.supplier.toSupplierLines(validPayment.iban),
                        customer = draft.customer.toCustomerLines(),
                        note = "",
                        description = draft.description,
                        serviceQuantity = draft.serviceQuantity,
                        serviceUnit = draft.serviceUnit,
                        extraItem = null,
                        extraItems = invoiceExtraItems,
                        currency = draft.currency,
                        pdfLanguage = draft.pdfLanguage,
                        iban = validPayment.iban,
                        bic = validPayment.bic,
                        variableSymbol = variableSymbol(draft.invoiceNumber),
                        issueDate = issueDate,
                        dueDate = issueDate.plusDays(15)
                    )
                    val generatedFile = InvoicePdfGenerator(appContext).generate(job, entries, period, input)
                    val archiveFile = InvoiceFiles.persist(appContext, generatedFile)
                    val totalAmount = InvoiceRules.calculateTotals(
                        entries = entries,
                        extraItems = invoiceExtraItems,
                        serviceQuantityOverride = draft.serviceQuantity
                    ).total
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
                showInvoiceCreatedActions(file)
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
        val extraItems = if (invoiceBinding.checkExtraItem.isChecked) {
            listOfNotNull(extraItem) + readExtraItemRows(invoiceBinding.layoutExtraItemsContainer)
        } else {
            emptyList()
        }
        return InvoiceDraft(
            invoiceNumber = invoiceNumber,
            supplier = supplier,
            iban = iban,
            bic = bic,
            customer = customer,
            currency = invoiceBinding.editCurrency.text?.toString()?.trim()?.uppercase(Locale.ROOT).orEmpty().ifBlank { "EUR" },
            pdfLanguage = InvoiceLanguage.fromLabel(invoiceBinding.inputPdfLanguage.text?.toString().orEmpty()).code,
            description = invoiceBinding.editDescription.text?.toString()?.trim().orEmpty(),
            serviceQuantity = invoiceBinding.editServiceQuantity.value().toInvoiceDoubleOrNull(),
            serviceUnit = invoiceBinding.editServiceUnit.value().ifBlank {
                defaultServiceUnit(InvoiceLanguage.fromLabel(invoiceBinding.inputPdfLanguage.text?.toString().orEmpty()))
            },
            extraItem = extraItem,
            extraItems = extraItems,
            extraName = extraName,
            extraQuantity = extraQuantity,
            extraUnit = extraUnit,
            extraPrice = extraPrice
        )
    }

    private fun invoiceDraftFromPreferences(job: WorkJob, period: YearMonth, client: Client?): InvoiceDraft {
        val prefs = requireContext().getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE)
        val iban = normalizeBankValue(SecureInvoicePrefs.readIban(requireContext()))
        val bic = normalizeBankValue(SecureInvoicePrefs.readBic(requireContext()))
            .ifBlank { inferSlovakBic(iban).orEmpty() }
        val invoiceLanguage = selectedPdfLanguage(prefs)
        val invoiceClient = client ?: clientFromPreferences(prefs, job.jobId)
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
                name = invoiceClient.name,
                street = invoiceClient.street,
                city = invoiceClient.city,
                zip = invoiceClient.zip,
                country = invoiceClient.country,
                ico = invoiceClient.ico,
                dic = invoiceClient.dic,
                icdph = invoiceClient.icdph,
                info = ""
            ),
            currency = prefs.getString(PREF_CURRENCY, "EUR").orEmpty().ifBlank { "EUR" },
            pdfLanguage = invoiceLanguage.code,
            description = invoiceDescriptionFromClient(invoiceClient, period, invoiceLanguage),
            serviceQuantity = null,
            serviceUnit = defaultServiceUnit(invoiceLanguage),
            extraItem = null,
            extraItems = emptyList(),
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
            .putString(PREF_PDF_LANGUAGE, draft.pdfLanguage)
            .putInt(sequencePrefKey(period), maxOf(currentInvoiceSequence(period), generatedSequence))
            .apply()
        SecureInvoicePrefs.saveBank(requireContext(), draft.iban, draft.bic)
    }

    private fun selectedMonth(): Int {
        val selected = binding.inputMonth.text?.toString().orEmpty()
        return monthLabels.indexOf(selected).let { index ->
            if (index >= 0) index + 1 else LocalDate.now().monthValue
        }
    }

    private fun selectedYear(): Int = yearSpinner.getSelectedYear() ?: LocalDate.now().year

    private fun selectedInvoicePeriod(): YearMonth {
        val selected = YearMonth.of(selectedYear(), selectedMonth())
        return InvoiceRules.selectedInvoicePeriod(LocalDate.now(), selected)
    }

    private fun defaultInvoiceNumber(period: YearMonth): String =
        InvoiceRules.defaultInvoiceNumber(period, currentInvoiceSequence(period))

    private fun currentInvoiceSequence(period: YearMonth): Int =
        requireContext().getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE)
            .getInt(sequencePrefKey(period), 0)

    private fun sequencePrefKey(period: YearMonth): String =
        InvoiceRules.sequencePrefKey(period)

    private fun sequenceFromInvoiceNumber(period: YearMonth, invoiceNumber: String): Int {
        return InvoiceRules.sequenceFromInvoiceNumber(period, invoiceNumber, currentInvoiceSequence(period))
    }

    private fun slovakMonthName(period: YearMonth): String =
        period.month.getDisplayName(TextStyle.FULL, Locale("sk", "SK"))

    private fun slovakMonthLabel(period: YearMonth): String =
        slovakMonthName(period).replaceFirstChar { it.uppercase(Locale("sk", "SK")) }

    private fun invoiceDescriptionFromClient(client: Client, period: YearMonth, language: InvoiceLanguage): String {
        return serviceDescription(client.serviceTemplate, period, language)
    }

    private fun serviceDescription(template: String, period: YearMonth, language: InvoiceLanguage): String {
        val monthLocale = when (language) {
            InvoiceLanguage.SLOVAK -> Locale("sk", "SK")
            InvoiceLanguage.UKRAINIAN -> Locale("uk", "UA")
            InvoiceLanguage.ENGLISH -> Locale.ENGLISH
        }
        val month = period.month.getDisplayName(TextStyle.FULL, monthLocale)
        return InvoiceRules.serviceDescription(template, month)
    }

    private fun defaultServiceUnit(language: InvoiceLanguage): String =
        when (language) {
            InvoiceLanguage.SLOVAK -> "hod"
            InvoiceLanguage.UKRAINIAN -> "год"
            InvoiceLanguage.ENGLISH -> "hrs"
        }

    private fun selectedPdfLanguage(prefs: android.content.SharedPreferences): InvoiceLanguage =
        InvoiceLanguage.fromCode(prefs.getString(PREF_PDF_LANGUAGE, InvoiceLanguage.SLOVAK.code))

    private fun clientFromPreferences(prefs: android.content.SharedPreferences, jobId: Int): Client =
        Client(
            jobId = jobId,
            name = clientPrefValue(prefs, jobId, PREF_CLIENT_NAME, ClientDefaults.NAME),
            street = clientPrefValue(prefs, jobId, PREF_CLIENT_STREET, ClientDefaults.STREET),
            city = clientPrefValue(prefs, jobId, PREF_CLIENT_CITY, ClientDefaults.CITY),
            zip = clientPrefValue(prefs, jobId, PREF_CLIENT_ZIP, ClientDefaults.ZIP),
            country = clientPrefValue(prefs, jobId, PREF_CLIENT_COUNTRY, ClientDefaults.COUNTRY),
            ico = clientPrefValue(prefs, jobId, PREF_CLIENT_ICO, ClientDefaults.ICO),
            dic = clientPrefValue(prefs, jobId, PREF_CLIENT_DIC, ClientDefaults.DIC),
            icdph = clientPrefValue(prefs, jobId, PREF_CLIENT_ICDPH, ClientDefaults.ICDPH),
            serviceTemplate = clientPrefValue(prefs, jobId, PREF_CLIENT_DESCRIPTION, ClientDefaults.SERVICE_TEMPLATE)
        )

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
        PaymentValidation.normalizeBankValue(value)

    private fun variableSymbol(invoiceNumber: String): String =
        invoiceNumber.filter { it.isDigit() }.take(10).ifBlank { "0" }

    private fun inferSlovakBic(iban: String): String? {
        return PaymentValidation.inferSlovakBic(iban)
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

    private fun showInvoiceCreatedActions(file: File) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.invoice_created)
            .setMessage(R.string.invoice_created_actions)
            .setNegativeButton(R.string.invoice_open) { _, _ -> openInvoiceFile(file) }
            .setPositiveButton(R.string.share) { _, _ -> shareInvoice(file) }
            .show()
    }

    private fun openInvoiceFile(file: File) {
        val context = requireContext()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { Snackbar.make(binding.root, R.string.invoice_open_failed, Snackbar.LENGTH_LONG).show() }
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

    private fun String.toInvoiceDoubleOrNull(): Double? =
        replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

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
        val pdfLanguage: String,
        val description: String,
        val serviceQuantity: Double?,
        val serviceUnit: String,
        val extraItem: InvoiceExtraItem?,
        val extraItems: List<InvoiceExtraItem>,
        val extraName: String,
        val extraQuantity: String,
        val extraUnit: String,
        val extraPrice: String
    )

    private data class ChecklistItem(
        val label: String,
        val ok: Boolean
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
        const val PREF_CLIENT_DESCRIPTION = "description_template"
        const val PREF_EXTRA_NAME = "extra_name"
        const val PREF_EXTRA_QUANTITY = "extra_quantity"
        const val PREF_EXTRA_UNIT = "extra_unit"
        const val PREF_EXTRA_PRICE = "extra_price"
        const val PREF_CURRENCY = "currency"
        const val PREF_PDF_LANGUAGE = "pdf_language"

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
        const val DEFAULT_SERVICE_TEMPLATE =
            InvoiceRules.DEFAULT_SERVICE_TEMPLATE

        const val DEFAULT_EXTRA_NAME = "Doprava"
        const val DEFAULT_EXTRA_QUANTITY = "1"
        const val DEFAULT_EXTRA_UNIT = ""
        const val DEFAULT_EXTRA_PRICE = "10"
    }
}
