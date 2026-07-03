package com.example.worktr.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.ui.picker.DropdownUi
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.AutoBackupManager
import com.example.worktr.util.AutoBackupPasswordStore
import com.example.worktr.util.AppLanguage
import com.example.worktr.util.InvoiceLanguage
import com.example.worktr.util.InvoicePrefs
import com.example.worktr.util.LanguagePreferences
import com.example.worktr.util.SecureInvoicePrefs
import com.example.worktr.util.WorkBackupManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

class SettingsFragment : Fragment() {
    private var pendingBackupPassword: String? = null
    private var pendingAutoBackupPassword: String? = null

    private val backupExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) exportBackup(uri)
    }
    private val backupRestoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) confirmRestoreBackup(uri)
    }
    private val autoBackupFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) saveAutoBackupFolder(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val profile = ResponsiveUi.profile(requireContext())
        ResponsiveUi.applyOuterPadding(view.findViewById(R.id.settingsScroll), profile)
        ResponsiveUi.applyContentPadding(view.findViewById(R.id.settingsContent), profile)
        loadSettings(view)
        setupLanguagePickers(view)
        setupAutoBackup(view)

        view.findViewById<MaterialButton>(R.id.buttonSaveSettings).setOnClickListener {
            saveSettings(view)
        }
        view.findViewById<MaterialButton>(R.id.buttonOpenClients).setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_clientsFragment)
        }
        view.findViewById<MaterialButton>(R.id.buttonExportBackup).setOnClickListener {
            BackupPasswordDialog.show(requireContext(), layoutInflater, confirmPassword = true) { password ->
                pendingBackupPassword = password
                backupExportLauncher.launch("worktr-backup-${LocalDate.now()}.json")
            }
        }
        view.findViewById<MaterialButton>(R.id.buttonRestoreBackup).setOnClickListener {
            backupRestoreLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        }
        view.findViewById<MaterialButton>(R.id.buttonChooseAutoBackupFolder).setOnClickListener {
            BackupPasswordDialog.show(requireContext(), layoutInflater, confirmPassword = true) { password ->
                pendingAutoBackupPassword = password
                autoBackupFolderLauncher.launch(null)
            }
        }
    }

    private fun loadSettings(view: View) {
        val prefs = InvoicePrefs.get(requireContext())
        view.input(R.id.editSupplierName).setText(prefs.getString(InvoicePrefs.PREF_SUPPLIER_NAME, InvoicePrefs.DEFAULT_SUPPLIER_NAME))
        view.input(R.id.editSupplierStreet).setText(prefs.getString(InvoicePrefs.PREF_SUPPLIER_STREET, InvoicePrefs.DEFAULT_SUPPLIER_STREET))
        view.input(R.id.editSupplierCity).setText(prefs.getString(InvoicePrefs.PREF_SUPPLIER_CITY, InvoicePrefs.DEFAULT_SUPPLIER_CITY))
        view.input(R.id.editSupplierZip).setText(prefs.getString(InvoicePrefs.PREF_SUPPLIER_ZIP, InvoicePrefs.DEFAULT_SUPPLIER_ZIP))
        view.input(R.id.editSupplierCountry).setText(prefs.getString(InvoicePrefs.PREF_SUPPLIER_COUNTRY, InvoicePrefs.DEFAULT_COUNTRY))
        view.input(R.id.editSupplierIco).setText(prefs.getString(InvoicePrefs.PREF_SUPPLIER_ICO, InvoicePrefs.DEFAULT_SUPPLIER_ICO))
        view.input(R.id.editIban).setText(SecureInvoicePrefs.readIban(requireContext()))
        view.input(R.id.editBic).setText(SecureInvoicePrefs.readBic(requireContext()))
        view.input(R.id.editCurrency).setText(prefs.getString(InvoicePrefs.PREF_CURRENCY, "EUR"))
        view.input(R.id.editExtraName).setText(prefs.getString(InvoicePrefs.PREF_EXTRA_NAME, InvoicePrefs.DEFAULT_EXTRA_NAME))
        view.input(R.id.editExtraQuantity).setText(prefs.getString(InvoicePrefs.PREF_EXTRA_QUANTITY, InvoicePrefs.DEFAULT_EXTRA_QUANTITY))
        view.input(R.id.editExtraUnit).setText(prefs.getString(InvoicePrefs.PREF_EXTRA_UNIT, InvoicePrefs.DEFAULT_EXTRA_UNIT))
        view.input(R.id.editExtraPrice).setText(prefs.getString(InvoicePrefs.PREF_EXTRA_PRICE, InvoicePrefs.DEFAULT_EXTRA_PRICE))
    }

    private fun saveSettings(view: View) {
        InvoicePrefs.get(requireContext()).edit()
            .putString(InvoicePrefs.PREF_SUPPLIER_NAME, view.input(R.id.editSupplierName).value())
            .putString(InvoicePrefs.PREF_SUPPLIER_STREET, view.input(R.id.editSupplierStreet).value())
            .putString(InvoicePrefs.PREF_SUPPLIER_CITY, view.input(R.id.editSupplierCity).value())
            .putString(InvoicePrefs.PREF_SUPPLIER_ZIP, view.input(R.id.editSupplierZip).value())
            .putString(InvoicePrefs.PREF_SUPPLIER_COUNTRY, view.input(R.id.editSupplierCountry).value())
            .putString(InvoicePrefs.PREF_SUPPLIER_ICO, view.input(R.id.editSupplierIco).value())
            .putString(InvoicePrefs.PREF_CURRENCY, view.input(R.id.editCurrency).value().uppercase(Locale.ROOT).ifBlank { "EUR" })
            .putString(
                InvoicePrefs.PREF_PDF_LANGUAGE,
                InvoiceLanguage.fromLabel(
                    view.findViewById<MaterialAutoCompleteTextView>(R.id.inputPdfLanguage).text?.toString().orEmpty()
                ).code
            )
            .putString(InvoicePrefs.PREF_EXTRA_NAME, view.input(R.id.editExtraName).value().ifBlank { InvoicePrefs.DEFAULT_EXTRA_NAME })
            .putString(InvoicePrefs.PREF_EXTRA_QUANTITY, view.input(R.id.editExtraQuantity).value().ifBlank { InvoicePrefs.DEFAULT_EXTRA_QUANTITY })
            .putString(InvoicePrefs.PREF_EXTRA_UNIT, view.input(R.id.editExtraUnit).value())
            .putString(InvoicePrefs.PREF_EXTRA_PRICE, view.input(R.id.editExtraPrice).value().ifBlank { InvoicePrefs.DEFAULT_EXTRA_PRICE })
            .apply()
        SecureInvoicePrefs.saveBank(
            context = requireContext(),
            iban = view.input(R.id.editIban).value().replace(Regex("\\s+"), "").uppercase(Locale.ROOT),
            bic = view.input(R.id.editBic).value().replace(Regex("\\s+"), "").uppercase(Locale.ROOT)
        )
        requireContext().getSharedPreferences(AutoBackupManager.BACKUP_PREFS, Context.MODE_PRIVATE).edit()
            .putString(
                AutoBackupManager.PREF_AUTO_BACKUP_INTERVAL,
                autoBackupIntervalFromLabel(view.findViewById<MaterialAutoCompleteTextView>(R.id.inputAutoBackupInterval).text?.toString().orEmpty())
            )
            .apply()
        Snackbar.make(view, R.string.settings_saved, Snackbar.LENGTH_SHORT).show()
    }

    private fun setupLanguagePickers(view: View) {
        val appLanguageInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.inputAppLanguage)
        val appLanguages = AppLanguage.entries.map { it.label }
        appLanguageInput.setAdapter(DropdownUi.adapter(requireContext(), appLanguages))
        appLanguageInput.setText(LanguagePreferences.appLanguage(requireContext()).label, false)
        DropdownUi.attach(appLanguageInput)

        val pdfLanguageInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.inputPdfLanguage)
        val invoiceLanguages = InvoiceLanguage.entries.map { it.label }
        pdfLanguageInput.setAdapter(DropdownUi.adapter(requireContext(), invoiceLanguages))
        val savedPdfLanguage = InvoicePrefs.get(requireContext())
            .getString(InvoicePrefs.PREF_PDF_LANGUAGE, InvoiceLanguage.SLOVAK.code)
        pdfLanguageInput.setText(InvoiceLanguage.fromCode(savedPdfLanguage).label, false)
        DropdownUi.attach(pdfLanguageInput)

        appLanguageInput.setOnItemClickListener { _, _, _, _ ->
            val language = AppLanguage.fromLabel(appLanguageInput.text?.toString().orEmpty())
            LanguagePreferences.saveAppLanguage(requireContext(), language)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.code))
        }
    }

    private fun setupAutoBackup(view: View) {
        val prefs = requireContext().getSharedPreferences(AutoBackupManager.BACKUP_PREFS, Context.MODE_PRIVATE)
        val labels = listOf(
            getString(R.string.auto_backup_weekly),
            getString(R.string.auto_backup_monthly)
        )
        val input = view.findViewById<MaterialAutoCompleteTextView>(R.id.inputAutoBackupInterval)
        input.setAdapter(DropdownUi.adapter(requireContext(), labels))
        val savedInterval = prefs.getString(AutoBackupManager.PREF_AUTO_BACKUP_INTERVAL, AutoBackupManager.INTERVAL_WEEKLY)
        input.setText(autoBackupLabel(savedInterval), false)
        DropdownUi.attach(input)
        input.setOnItemClickListener { _, _, _, _ ->
            prefs.edit()
                .putString(
                    AutoBackupManager.PREF_AUTO_BACKUP_INTERVAL,
                    autoBackupIntervalFromLabel(input.text?.toString().orEmpty())
                )
                .apply()
        }
        updateAutoBackupFolderLabel(view)
    }

    private fun saveAutoBackupFolder(uri: Uri) {
        val password = pendingAutoBackupPassword
        if (password.isNullOrBlank()) {
            BackupPasswordDialog.show(requireContext(), layoutInflater, confirmPassword = true) { confirmedPassword ->
                pendingAutoBackupPassword = confirmedPassword
                saveAutoBackupFolder(uri)
            }
            return
        }
        pendingAutoBackupPassword = null

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
        }
        AutoBackupPasswordStore.save(requireContext(), password)
        requireContext().getSharedPreferences(AutoBackupManager.BACKUP_PREFS, Context.MODE_PRIVATE).edit()
            .putString(AutoBackupManager.PREF_AUTO_BACKUP_TREE_URI, uri.toString())
            .apply()
        updateAutoBackupFolderLabel(requireView())

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val appContext = requireContext().applicationContext
                withContext(Dispatchers.IO) {
                    AutoBackupManager.exportNow(appContext, DatabaseProvider.get(appContext), uri)
                }
            }
            if (!isAdded) return@launch
            result.onSuccess {
                requireContext().getSharedPreferences(AutoBackupManager.BACKUP_PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(AutoBackupManager.PREF_LAST_AUTO_BACKUP_MILLIS, System.currentTimeMillis())
                    .putLong(AutoBackupManager.PREF_LAST_BACKUP_EXPORT_MILLIS, System.currentTimeMillis())
                    .apply()
                Snackbar.make(requireView(), R.string.auto_backup_success, Snackbar.LENGTH_LONG).show()
            }.onFailure {
                Snackbar.make(requireView(), R.string.auto_backup_saved, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun updateAutoBackupFolderLabel(view: View) {
        val hasFolder = requireContext()
            .getSharedPreferences(AutoBackupManager.BACKUP_PREFS, Context.MODE_PRIVATE)
            .getString(AutoBackupManager.PREF_AUTO_BACKUP_TREE_URI, null)
            .isNullOrBlank()
            .not()
        view.findViewById<TextView>(R.id.textAutoBackupFolder).setText(
            if (hasFolder) R.string.auto_backup_folder_selected else R.string.auto_backup_folder_not_selected
        )
    }

    private fun autoBackupLabel(interval: String?): String =
        if (interval == AutoBackupManager.INTERVAL_MONTHLY) {
            getString(R.string.auto_backup_monthly)
        } else {
            getString(R.string.auto_backup_weekly)
        }

    private fun autoBackupIntervalFromLabel(label: String): String =
        if (label == getString(R.string.auto_backup_monthly)) {
            AutoBackupManager.INTERVAL_MONTHLY
        } else {
            AutoBackupManager.INTERVAL_WEEKLY
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
                    .getSharedPreferences(AutoBackupManager.BACKUP_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(AutoBackupManager.PREF_LAST_BACKUP_EXPORT_MILLIS, System.currentTimeMillis())
                    .apply()
                Snackbar.make(
                    requireView(),
                    getString(R.string.backup_export_success, summary.jobs, summary.entries, summary.invoices),
                    Snackbar.LENGTH_LONG
                ).show()
            }.onFailure {
                Snackbar.make(
                    requireView(),
                    it.message ?: getString(R.string.backup_export_failed),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
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
                    requireView(),
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
                    requireView(),
                    getString(R.string.backup_restore_success, summary.jobs, summary.entries, summary.invoices),
                    Snackbar.LENGTH_LONG
                ).show()
            }.onFailure {
                Snackbar.make(
                    requireView(),
                    it.message ?: getString(R.string.backup_restore_failed),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun View.input(id: Int): TextInputEditText = findViewById(id)

    private fun TextInputEditText.value(): String = text?.toString()?.trim().orEmpty()

}
