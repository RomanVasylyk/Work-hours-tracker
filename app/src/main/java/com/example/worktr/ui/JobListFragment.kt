package com.example.worktr.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.Job
import com.example.worktr.data.JobRepository
import com.example.worktr.databinding.DialogInvoiceSettingsBinding
import com.example.worktr.databinding.FragmentJobListBinding
import com.example.worktr.ui.picker.DropdownUi
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.AutoBackupManager
import com.example.worktr.util.CsvImporter
import com.example.worktr.util.CsvImportSummary
import com.example.worktr.util.ExcelExporter
import com.example.worktr.util.WorkBackupManager
import com.example.worktr.util.salaryBreakdown
import com.example.worktr.util.workedHours
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

class JobListFragment : Fragment() {
    private var _binding: FragmentJobListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: com.example.worktr.viewmodel.JobListViewModel
    private lateinit var adapter: JobListAdapter
    private var currentJobs: List<Job> = emptyList()
    private var pendingBackupPassword: String? = null
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            showImportTargetDialog(uri)
        }
    }
    private val backupExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) exportBackup(uri)
    }
    private val backupRestoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) confirmRestoreBackup(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        enterTransition = MaterialFadeThrough()
        returnTransition = MaterialFadeThrough()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentJobListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = DatabaseProvider.get(requireContext())
        val repository = JobRepository(db.jobDao())
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return com.example.worktr.viewmodel.JobListViewModel(repository) as T
            }
        })[com.example.worktr.viewmodel.JobListViewModel::class.java]
        prepareMainActions()
        applyResponsiveLayout()

        adapter = JobListAdapter(
            onClick = { job ->
                val action = JobListFragmentDirections
                    .actionJobListFragmentToJobDetailFragment(job.jobId)
                findNavController().navigate(action)
            },
            onEdit = { job -> showJobOptions(job) }
        )
        binding.recyclerJobs.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerJobs.adapter = adapter

        viewModel.jobs.observe(viewLifecycleOwner) { jobs ->
            currentJobs = jobs
            adapter.submitList(jobs)
            binding.textJobsOverview.text = if (jobs.isEmpty()) {
                getString(R.string.jobs_overview_empty_badge)
            } else {
                getString(R.string.jobs_overview_count, jobs.size)
            }
            binding.textEmptyJobs.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerJobs.visibility = if (jobs.isEmpty()) View.GONE else View.VISIBLE
        }

        binding.fabAddJob.setOnClickListener { showAddJobDialog() }
        binding.cardOverview.setOnClickListener {
            findNavController().navigate(R.id.action_jobListFragment_to_monthOverviewFragment)
        }
        binding.cardOverallStats.setOnClickListener {
            findNavController().navigate(
                R.id.action_jobListFragment_to_statsFragment,
                bundleOf("jobId" to -1)
            )
        }
        binding.cardExportAll.setOnClickListener { exportAllData() }
        binding.cardImportData.setOnClickListener { launchImportPicker() }
        binding.cardInvoiceArchive.setOnClickListener {
            findNavController().navigate(R.id.action_jobListFragment_to_invoiceArchiveFragment)
        }
        binding.cardBackupData.setOnClickListener { showBackupDialog() }
        binding.cardInvoiceSettings.setOnClickListener {
            findNavController().navigate(R.id.action_jobListFragment_to_settingsFragment)
        }
        runAutoBackupIfDue(db)
        showBackupReminderIfNeeded()
        loadCurrentMonthSummary(db)
    }

    private fun prepareMainActions() {
        binding.cardOverallStats.visibility = View.GONE
        binding.cardImportData.visibility = View.GONE
        binding.cardExportAll.visibility = View.GONE

        binding.quickActionRow.removeView(binding.cardInvoiceSettings)
        val backupIndex = binding.quickActionRow.indexOfChild(binding.cardBackupData)
        binding.quickActionRow.addView(binding.cardInvoiceSettings, backupIndex)
        normalizeQuickActionCards()
    }

    private fun normalizeQuickActionCards() {
        val height = ResponsiveUi.dp(requireContext(), 58)
        listOf(
            binding.cardOverallStats,
            binding.cardImportData,
            binding.cardExportAll,
            binding.cardInvoiceArchive,
            binding.cardInvoiceSettings,
            binding.cardBackupData
        ).forEach { card ->
            card.minimumHeight = height
            val content = card.getChildAt(0)
            content.minimumHeight = height
            content.layoutParams = content.layoutParams.apply {
                this.height = height
            }
            (content as? android.widget.LinearLayout)?.gravity = Gravity.CENTER
            card.applyOneLineLabels()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_job_list, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_overall_stats -> {
                findNavController().navigate(
                    R.id.action_jobListFragment_to_statsFragment,
                    bundleOf("jobId" to -1)
                )
                true
            }
            R.id.action_import -> {
                launchImportPicker()
                true
            }
            R.id.action_export -> {
                exportAllData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadCurrentMonthSummary(db: com.example.worktr.data.AppDatabase) {
        val zone = ZoneId.systemDefault()
        val period = YearMonth.now()
        val start = period.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = period.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val month = resources.getStringArray(R.array.months)[period.monthValue - 1]
        val hoursFormatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }
        val moneyFormatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

        binding.textCurrentMonth.text = "$month ${period.year}"
        viewLifecycleOwner.lifecycleScope.launch {
            db.workEntryDao().getAllEntriesForPeriod(start, end)
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { entries ->
                    val hours = entries.sumOf { it.workedHours() }
                    val salary = entries.sumOf { it.salaryBreakdown(zone).total }
                    val summaries = entries.groupBy { it.jobId }
                        .mapValues { (_, jobEntries) ->
                            JobMonthSummary(
                                hours = jobEntries.sumOf { it.workedHours() },
                                salary = jobEntries.sumOf { it.salaryBreakdown(zone).total }
                            )
                        }
                    binding.textCurrentHours.text = getString(
                        R.string.current_month_hours_value,
                        hoursFormatter.format(hours)
                    )
                    binding.textCurrentSalary.text = getString(
                        R.string.current_month_salary_value,
                        moneyFormatter.format(salary)
                    )
                    binding.textInvoiceReadiness.text = if (hours > 0.0) {
                        getString(R.string.current_month_invoice_ready)
                    } else {
                        getString(R.string.current_month_invoice_empty)
                    }
                    adapter.setMonthlySummaries(summaries)
                }
        }
    }

    private fun showJobOptions(job: Job) {
        val v = layoutInflater.inflate(R.layout.dialog_edit_job, null)
        val name = v.findViewById<TextInputEditText>(R.id.editJobName)
        val hourly = v.findViewById<TextInputEditText>(R.id.editHourly)
        val night = v.findViewById<TextInputEditText>(R.id.editNight)
        val sat = v.findViewById<TextInputEditText>(R.id.editSat)
        val sun = v.findViewById<TextInputEditText>(R.id.editSun)
        val hol = v.findViewById<TextInputEditText>(R.id.editHol)
        name.setText(job.name)
        hourly.setText(job.hourlyRate.toString())
        night.setText(job.nightBonus.toString())
        sat.setText(job.saturdayBonus.toString())
        sun.setText(job.sundayBonus.toString())
        hol.setText(job.holidayBonus.toString())
        val dlg = MaterialAlertDialogBuilder(requireContext()).setView(v).create()
        v.findViewById<MaterialButton>(R.id.buttonSave).setOnClickListener {
            val parsedName = name.text?.toString()?.trim().orEmpty()
            if (parsedName.isBlank()) {
                name.error = getString(R.string.validation_required)
                return@setOnClickListener
            }
            val hourlyRate = hourly.requireDecimal(positive = true) ?: return@setOnClickListener
            val nightBonus = night.requireDecimal() ?: return@setOnClickListener
            val saturdayBonus = sat.requireDecimal() ?: return@setOnClickListener
            val sundayBonus = sun.requireDecimal() ?: return@setOnClickListener
            val holidayBonus = hol.requireDecimal() ?: return@setOnClickListener
            val updated = job.copy(
                name = parsedName,
                hourlyRate = hourlyRate,
                nightBonus = nightBonus,
                saturdayBonus = saturdayBonus,
                sundayBonus = sundayBonus,
                holidayBonus = holidayBonus
            )
            viewModel.update(updated)
            dlg.dismiss()
        }
        v.findViewById<MaterialButton>(R.id.buttonDelete).setOnClickListener {
            viewModel.delete(job)
            dlg.dismiss()
        }
        dlg.show()
    }

    private fun showAddJobDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_add_job, null)
        val name = v.findViewById<TextInputEditText>(R.id.editJobName)
        val hourly = v.findViewById<TextInputEditText>(R.id.editHourly)
        val night = v.findViewById<TextInputEditText>(R.id.editNight)
        val sat = v.findViewById<TextInputEditText>(R.id.editSat)
        val sun = v.findViewById<TextInputEditText>(R.id.editSun)
        val hol = v.findViewById<TextInputEditText>(R.id.editHol)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(v)
            .create()
        v.findViewById<MaterialButton>(R.id.buttonSave).setOnClickListener {
                val parsedName = name.text?.toString()?.trim().orEmpty()
                if (parsedName.isBlank()) {
                    name.error = getString(R.string.validation_required)
                    return@setOnClickListener
                }
                val hourlyRate = hourly.requireDecimal(positive = true) ?: return@setOnClickListener
                val nightBonus = night.requireDecimal() ?: return@setOnClickListener
                val saturdayBonus = sat.requireDecimal() ?: return@setOnClickListener
                val sundayBonus = sun.requireDecimal() ?: return@setOnClickListener
                val holidayBonus = hol.requireDecimal() ?: return@setOnClickListener
                val j = Job(
                    name = parsedName,
                    hourlyRate = hourlyRate,
                    nightBonus = nightBonus,
                    saturdayBonus = saturdayBonus,
                    sundayBonus = sundayBonus,
                    holidayBonus = holidayBonus
                )
                viewModel.insert(j)
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun TextInputEditText.requireDecimal(positive: Boolean = false): Double? {
        error = null
        val value = text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        return when {
            value == null -> {
                error = getString(R.string.validation_non_negative_number)
                null
            }
            positive && value <= 0.0 -> {
                error = getString(R.string.validation_positive_number)
                null
            }
            value < 0.0 -> {
                error = getString(R.string.validation_non_negative_number)
                null
            }
            else -> value
        }
    }

    private fun exportAllData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val appContext = requireContext().applicationContext
            val file: File = withContext(Dispatchers.IO) {
                ExcelExporter(appContext).exportAll()
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

    private fun showBackupDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_title)
            .setItems(
                arrayOf(
                    getString(R.string.backup_export),
                    getString(R.string.backup_restore)
                )
            ) { _, which ->
                when (which) {
                    0 -> BackupPasswordDialog.show(requireContext(), layoutInflater, confirmPassword = true) { password ->
                        pendingBackupPassword = password
                        backupExportLauncher.launch("worktr-backup-${LocalDate.now()}.json")
                    }
                    1 -> backupRestoreLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                }
            }
            .show()
    }

    private fun showInvoiceSettingsDialog() {
        val prefs = requireContext().getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE)
        val settingsBinding = DialogInvoiceSettingsBinding.inflate(layoutInflater)

        settingsBinding.editSupplierName.setText(prefs.getString(PREF_SUPPLIER_NAME, DEFAULT_SUPPLIER_NAME))
        settingsBinding.editSupplierStreet.setText(prefs.getString(PREF_SUPPLIER_STREET, DEFAULT_SUPPLIER_STREET))
        settingsBinding.editSupplierCity.setText(prefs.getString(PREF_SUPPLIER_CITY, DEFAULT_SUPPLIER_CITY))
        settingsBinding.editSupplierZip.setText(prefs.getString(PREF_SUPPLIER_ZIP, DEFAULT_SUPPLIER_ZIP))
        settingsBinding.editSupplierCountry.setText(prefs.getString(PREF_SUPPLIER_COUNTRY, DEFAULT_COUNTRY))
        settingsBinding.editSupplierIco.setText(prefs.getString(PREF_SUPPLIER_ICO, DEFAULT_SUPPLIER_ICO))
        settingsBinding.editIban.setText(prefs.getString(PREF_IBAN, ""))
        settingsBinding.editBic.setText(prefs.getString(PREF_BIC, ""))

        settingsBinding.editCustomerName.setText(prefs.getString(PREF_CLIENT_NAME, DEFAULT_CLIENT_NAME))
        settingsBinding.editCustomerStreet.setText(prefs.getString(PREF_CLIENT_STREET, DEFAULT_CLIENT_STREET))
        settingsBinding.editCustomerCity.setText(prefs.getString(PREF_CLIENT_CITY, DEFAULT_CLIENT_CITY))
        settingsBinding.editCustomerZip.setText(prefs.getString(PREF_CLIENT_ZIP, DEFAULT_CLIENT_ZIP))
        settingsBinding.editCustomerCountry.setText(prefs.getString(PREF_CLIENT_COUNTRY, DEFAULT_COUNTRY))
        settingsBinding.editCustomerIco.setText(prefs.getString(PREF_CLIENT_ICO, DEFAULT_CLIENT_ICO))
        settingsBinding.editCustomerDic.setText(prefs.getString(PREF_CLIENT_DIC, DEFAULT_CLIENT_DIC))
        settingsBinding.editCustomerIcdph.setText(prefs.getString(PREF_CLIENT_ICDPH, DEFAULT_CLIENT_ICDPH))

        settingsBinding.editExtraName.setText(prefs.getString(PREF_EXTRA_NAME, DEFAULT_EXTRA_NAME))
        settingsBinding.editExtraQuantity.setText(prefs.getString(PREF_EXTRA_QUANTITY, DEFAULT_EXTRA_QUANTITY))
        settingsBinding.editExtraUnit.setText(prefs.getString(PREF_EXTRA_UNIT, DEFAULT_EXTRA_UNIT))
        settingsBinding.editExtraPrice.setText(prefs.getString(PREF_EXTRA_PRICE, DEFAULT_EXTRA_PRICE))
        settingsBinding.editCurrency.setText(prefs.getString(PREF_CURRENCY, "EUR"))

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(settingsBinding.root)
            .create()

        settingsBinding.buttonCancelInvoiceSettings.setOnClickListener {
            dialog.dismiss()
        }
        settingsBinding.buttonSaveInvoiceSettings.setOnClickListener {
            prefs.edit()
                .putString(PREF_SUPPLIER_NAME, settingsBinding.editSupplierName.value())
                .putString(PREF_SUPPLIER_STREET, settingsBinding.editSupplierStreet.value())
                .putString(PREF_SUPPLIER_CITY, settingsBinding.editSupplierCity.value())
                .putString(PREF_SUPPLIER_ZIP, settingsBinding.editSupplierZip.value())
                .putString(PREF_SUPPLIER_COUNTRY, settingsBinding.editSupplierCountry.value())
                .putString(PREF_SUPPLIER_ICO, settingsBinding.editSupplierIco.value())
                .putString(PREF_IBAN, settingsBinding.editIban.value())
                .putString(PREF_BIC, settingsBinding.editBic.value())
                .putString(PREF_CLIENT_NAME, settingsBinding.editCustomerName.value())
                .putString(PREF_CLIENT_STREET, settingsBinding.editCustomerStreet.value())
                .putString(PREF_CLIENT_CITY, settingsBinding.editCustomerCity.value())
                .putString(PREF_CLIENT_ZIP, settingsBinding.editCustomerZip.value())
                .putString(PREF_CLIENT_COUNTRY, settingsBinding.editCustomerCountry.value())
                .putString(PREF_CLIENT_ICO, settingsBinding.editCustomerIco.value())
                .putString(PREF_CLIENT_DIC, settingsBinding.editCustomerDic.value())
                .putString(PREF_CLIENT_ICDPH, settingsBinding.editCustomerIcdph.value())
                .putString(PREF_EXTRA_NAME, settingsBinding.editExtraName.value().ifBlank { DEFAULT_EXTRA_NAME })
                .putString(PREF_EXTRA_QUANTITY, settingsBinding.editExtraQuantity.value().ifBlank { DEFAULT_EXTRA_QUANTITY })
                .putString(PREF_EXTRA_UNIT, settingsBinding.editExtraUnit.value())
                .putString(PREF_EXTRA_PRICE, settingsBinding.editExtraPrice.value().ifBlank { DEFAULT_EXTRA_PRICE })
                .putString(PREF_CURRENCY, settingsBinding.editCurrency.value().uppercase().ifBlank { "EUR" })
                .apply()
            dialog.dismiss()
            Snackbar.make(binding.root, R.string.invoice_settings_saved, Snackbar.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun exportBackup(uri: Uri) {
        val password = pendingBackupPassword.orEmpty()
        pendingBackupPassword = null
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val appContext = requireContext().applicationContext
                withContext(Dispatchers.IO) {
                    WorkBackupManager(appContext, DatabaseProvider.get(appContext)).exportEncryptedTo(uri, password)
                }
            }
            if (!isAdded) return@launch
            result.onSuccess { summary ->
                requireContext()
                    .getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(PREF_LAST_BACKUP_EXPORT_MILLIS, System.currentTimeMillis())
                    .apply()
                Snackbar.make(
                    binding.root,
                    getString(R.string.backup_export_success, summary.jobs, summary.entries, summary.invoices),
                    Snackbar.LENGTH_LONG
                ).show()
            }.onFailure {
                Snackbar.make(
                    binding.root,
                    it.message ?: getString(R.string.backup_export_failed),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showBackupReminderIfNeeded() {
        val prefs = requireContext().getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastExport = prefs.getLong(PREF_LAST_BACKUP_EXPORT_MILLIS, 0L)
        val lastReminder = prefs.getLong(PREF_LAST_BACKUP_REMINDER_MILLIS, 0L)
        if (now - lastExport < BACKUP_REMINDER_INTERVAL_MILLIS) return
        if (now - lastReminder < BACKUP_REMINDER_SNOOZE_MILLIS) return

        prefs.edit().putLong(PREF_LAST_BACKUP_REMINDER_MILLIS, now).apply()
        Snackbar.make(binding.root, R.string.backup_reminder, Snackbar.LENGTH_LONG)
            .setAction(R.string.backup_export) { showBackupDialog() }
            .show()
    }

    private fun confirmRestoreBackup(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val encrypted = runCatching {
                val appContext = requireContext().applicationContext
                withContext(Dispatchers.IO) {
                    WorkBackupManager(appContext, DatabaseProvider.get(appContext)).isEncryptedBackup(uri)
                }
            }.getOrDefault(true)
            if (!isAdded) return@launch
            if (encrypted) {
                BackupPasswordDialog.show(requireContext(), layoutInflater, confirmPassword = false) { password ->
                    previewRestoreBackup(uri, password)
                }
            } else {
                previewRestoreBackup(uri, null)
            }
        }
    }

    private fun previewRestoreBackup(uri: Uri, password: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val appContext = requireContext().applicationContext
                withContext(Dispatchers.IO) {
                    WorkBackupManager(appContext, DatabaseProvider.get(appContext)).previewFrom(uri, password)
                }
            }
            if (!isAdded) return@launch
            result.onSuccess { summary ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.backup_restore_confirm_title)
                    .setMessage(getString(R.string.backup_restore_preview, summary.jobs, summary.entries, summary.invoices))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.backup_restore_confirm) { _, _ -> restoreBackup(uri, password) }
                    .show()
            }.onFailure {
                Snackbar.make(
                    binding.root,
                    it.message ?: getString(R.string.backup_restore_failed),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun restoreBackup(uri: Uri, password: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val appContext = requireContext().applicationContext
                withContext(Dispatchers.IO) {
                    WorkBackupManager(appContext, DatabaseProvider.get(appContext)).restoreFrom(uri, password)
                }
            }
            if (!isAdded) return@launch
            result.onSuccess { summary ->
                Snackbar.make(
                    binding.root,
                    getString(R.string.backup_restore_success, summary.jobs, summary.entries, summary.invoices),
                    Snackbar.LENGTH_LONG
                ).show()
            }.onFailure {
                Snackbar.make(
                    binding.root,
                    it.message ?: getString(R.string.backup_restore_failed),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun runAutoBackupIfDue(db: com.example.worktr.data.AppDatabase) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val appContext = requireContext().applicationContext
                withContext(Dispatchers.IO) {
                    AutoBackupManager.runIfDue(appContext, db)
                }
            }
            if (!isAdded) return@launch
            result.onSuccess { created ->
                if (created) {
                    Snackbar.make(binding.root, R.string.auto_backup_success, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showImportTargetDialog(uri: Uri) {
        if (currentJobs.isEmpty()) {
            importData(uri, null)
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_import_target, null)
        val modeGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.groupImportMode)
        val existingButton = view.findViewById<MaterialButton>(R.id.buttonImportExisting)
        val inputJobLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutImportJobField)
        val inputJob = view.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.inputImportJob)
        val confirmButton = view.findViewById<MaterialButton>(R.id.buttonImportConfirm)
        modeGroup.check(R.id.buttonImportAuto)

        val jobNames = currentJobs.map { it.name }
        inputJob.setAdapter(DropdownUi.adapter(requireContext(), jobNames))
        inputJob.setText(jobNames.firstOrNull().orEmpty(), false)
        DropdownUi.attach(inputJob) {
            jobNames.indexOf(inputJob.text?.toString().orEmpty()).takeIf { it >= 0 }
        }

        fun updateMode() {
            inputJobLayout.visibility = if (existingButton.isChecked) View.VISIBLE else View.GONE
            if (!existingButton.isChecked) {
                inputJobLayout.error = null
            }
        }
        modeGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) updateMode()
        }
        updateMode()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
        confirmButton.setOnClickListener {
            val targetJobId = if (existingButton.isChecked) {
                val selectedJob = currentJobs.firstOrNull { it.name == inputJob.text?.toString().orEmpty() }
                if (selectedJob == null) {
                    inputJobLayout.error = getString(R.string.import_dialog_pick_job)
                    return@setOnClickListener
                }
                inputJobLayout.error = null
                selectedJob.jobId
            } else {
                null
            }
            dialog.dismiss()
            importData(uri, targetJobId)
        }
        dialog.show()
    }

    private fun importData(uri: Uri, targetJobId: Int?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val summary = runCatching {
                val appContext = requireContext().applicationContext
                withContext(Dispatchers.IO) {
                    CsvImporter(appContext, DatabaseProvider.get(appContext)).import(uri, targetJobId)
                }
            }
            if (!isAdded) return@launch
            summary.onSuccess { showImportSummary(it) }
                .onFailure {
                    Snackbar.make(
                        binding.root,
                        it.message ?: getString(R.string.import_failed),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun showImportSummary(summary: CsvImportSummary) {
        Snackbar.make(
            binding.root,
            getString(
                R.string.import_success_summary,
                summary.importedEntries,
                summary.createdJobs,
                summary.matchedJobs
            ),
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun MaterialCardView.applyOneLineLabels() {
        fun visit(view: View) {
            if (view is TextView) {
                view.maxLines = 1
                view.ellipsize = TextUtils.TruncateAt.END
                view.gravity = Gravity.CENTER
                view.textAlignment = View.TEXT_ALIGNMENT_CENTER
                view.layoutParams = view.layoutParams.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))
                }
            }
        }
        visit(this)
    }

    private fun applyResponsiveLayout() {
        val profile = ResponsiveUi.profile(requireContext())
        ResponsiveUi.applyOuterPadding(binding.jobListContent, profile)
        val visibleActions = listOf(
            binding.cardInvoiceArchive,
            binding.cardInvoiceSettings,
            binding.cardBackupData
        )
        if (profile.isCompact) {
            binding.quickActionRow.orientation = android.widget.LinearLayout.VERTICAL
            visibleActions.forEachIndexed { index, card ->
                val params = card.layoutParams as ViewGroup.MarginLayoutParams
                card.layoutParams = params.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    marginStart = 0
                    topMargin = if (index == 0) 0 else ResponsiveUi.dp(requireContext(), 10)
                }
            }
        } else {
            binding.quickActionRow.orientation = android.widget.LinearLayout.HORIZONTAL
            visibleActions.forEachIndexed { index, card ->
                val params = card.layoutParams as ViewGroup.MarginLayoutParams
                card.layoutParams = params.apply {
                    width = 0
                    marginStart = if (index == 0) 0 else ResponsiveUi.dp(requireContext(), 12)
                    topMargin = 0
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun TextInputEditText.value(): String = text?.toString()?.trim().orEmpty()

    private companion object {
        const val BACKUP_PREFS = "backup_prefs"
        const val PREF_LAST_BACKUP_EXPORT_MILLIS = "last_backup_export_millis"
        const val PREF_LAST_BACKUP_REMINDER_MILLIS = "last_backup_reminder_millis"
        const val BACKUP_REMINDER_INTERVAL_MILLIS = 30L * 24L * 60L * 60L * 1000L
        const val BACKUP_REMINDER_SNOOZE_MILLIS = 24L * 60L * 60L * 1000L

        const val INVOICE_PREFS = "invoice_prefs"
        const val PREF_SUPPLIER_NAME = "supplier_name"
        const val PREF_SUPPLIER_STREET = "supplier_street"
        const val PREF_SUPPLIER_CITY = "supplier_city"
        const val PREF_SUPPLIER_ZIP = "supplier_zip"
        const val PREF_SUPPLIER_COUNTRY = "supplier_country"
        const val PREF_SUPPLIER_ICO = "supplier_ico"
        const val PREF_IBAN = "iban"
        const val PREF_BIC = "bic"
        const val PREF_CLIENT_NAME = "name"
        const val PREF_CLIENT_STREET = "street"
        const val PREF_CLIENT_CITY = "city"
        const val PREF_CLIENT_ZIP = "zip"
        const val PREF_CLIENT_COUNTRY = "country"
        const val PREF_CLIENT_ICO = "ico"
        const val PREF_CLIENT_DIC = "dic"
        const val PREF_CLIENT_ICDPH = "icdph"
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

        const val DEFAULT_EXTRA_NAME = "Doprava"
        const val DEFAULT_EXTRA_QUANTITY = "1"
        const val DEFAULT_EXTRA_UNIT = ""
        const val DEFAULT_EXTRA_PRICE = "10"
    }
}
