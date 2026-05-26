package com.example.worktr.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.Job
import com.example.worktr.databinding.DialogClientSettingsBinding
import com.example.worktr.databinding.FragmentClientsBinding
import com.example.worktr.ui.responsive.ResponsiveUi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClientsFragment : Fragment() {
    private var _binding: FragmentClientsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ClientsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClientsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val profile = ResponsiveUi.profile(requireContext())
        ResponsiveUi.applyOuterPadding(binding.clientsContent, profile)

        adapter = ClientsAdapter(onEdit = ::showClientDialog)
        binding.recyclerClients.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerClients.adapter = adapter

        val db = DatabaseProvider.get(requireContext())
        val prefs = requireContext().getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE)
        viewLifecycleOwner.lifecycleScope.launch {
            combine(db.jobDao().getAllJobs(), db.invoiceDao().getAllInvoices()) { jobs, invoices ->
                val latestInvoices = invoices.groupBy { it.jobId }
                    .mapValues { (_, values) -> values.maxByOrNull { it.createdAtMillis } }
                jobs.map { job ->
                    ClientUiModel(
                        job = job,
                        customerName = clientPrefValue(prefs, job.jobId, PREF_CLIENT_NAME, DEFAULT_CLIENT_NAME),
                        lastInvoice = latestInvoices[job.jobId]
                    )
                }
            }
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collectLatest { clients ->
                    adapter.submitList(clients)
                    binding.textEmptyClients.visibility = if (clients.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerClients.visibility = if (clients.isEmpty()) View.GONE else View.VISIBLE
                }
        }
    }

    private fun showClientDialog(model: ClientUiModel) {
        val prefs = requireContext().getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE)
        val dialogBinding = DialogClientSettingsBinding.inflate(layoutInflater)
        val job = model.job

        dialogBinding.editCustomerName.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_NAME, DEFAULT_CLIENT_NAME))
        dialogBinding.editCustomerStreet.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_STREET, DEFAULT_CLIENT_STREET))
        dialogBinding.editCustomerCity.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_CITY, DEFAULT_CLIENT_CITY))
        dialogBinding.editCustomerZip.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_ZIP, DEFAULT_CLIENT_ZIP))
        dialogBinding.editCustomerCountry.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_COUNTRY, DEFAULT_COUNTRY))
        dialogBinding.editCustomerIco.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_ICO, DEFAULT_CLIENT_ICO))
        dialogBinding.editCustomerDic.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_DIC, DEFAULT_CLIENT_DIC))
        dialogBinding.editCustomerIcdph.setText(clientPrefValue(prefs, job.jobId, PREF_CLIENT_ICDPH, DEFAULT_CLIENT_ICDPH))
        dialogBinding.editHourly.setText(job.hourlyRate.toString())
        dialogBinding.editServiceTemplate.setText(
            prefs.getString(clientPref(job.jobId, PREF_CLIENT_DESCRIPTION), DEFAULT_SERVICE_TEMPLATE)
                ?: DEFAULT_SERVICE_TEMPLATE
        )

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.buttonCancelClient.setOnClickListener { dialog.dismiss() }
        dialogBinding.buttonSaveClient.setOnClickListener {
            saveClient(job, dialogBinding, dialog)
        }
        dialog.show()
    }

    private fun saveClient(
        job: Job,
        dialogBinding: DialogClientSettingsBinding,
        dialog: android.app.Dialog
    ) {
        val hourlyRate = dialogBinding.editHourly.value().toInvoiceDouble(job.hourlyRate)
        requireContext().getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(clientPref(job.jobId, PREF_CLIENT_NAME), dialogBinding.editCustomerName.value())
            .putString(clientPref(job.jobId, PREF_CLIENT_STREET), dialogBinding.editCustomerStreet.value())
            .putString(clientPref(job.jobId, PREF_CLIENT_CITY), dialogBinding.editCustomerCity.value())
            .putString(clientPref(job.jobId, PREF_CLIENT_ZIP), dialogBinding.editCustomerZip.value())
            .putString(clientPref(job.jobId, PREF_CLIENT_COUNTRY), dialogBinding.editCustomerCountry.value())
            .putString(clientPref(job.jobId, PREF_CLIENT_ICO), dialogBinding.editCustomerIco.value())
            .putString(clientPref(job.jobId, PREF_CLIENT_DIC), dialogBinding.editCustomerDic.value())
            .putString(clientPref(job.jobId, PREF_CLIENT_ICDPH), dialogBinding.editCustomerIcdph.value())
            .putString(clientPref(job.jobId, PREF_CLIENT_DESCRIPTION), dialogBinding.editServiceTemplate.value().ifBlank { DEFAULT_SERVICE_TEMPLATE })
            .apply()

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                DatabaseProvider.get(appContext).jobDao().update(job.copy(hourlyRate = hourlyRate))
            }
            if (!isAdded) return@launch
            dialog.dismiss()
            Snackbar.make(binding.root, R.string.client_saved, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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

    private fun TextInputEditText.value(): String = text?.toString()?.trim().orEmpty()

    private fun String.toInvoiceDouble(defaultValue: Double): Double =
        replace(',', '.').toDoubleOrNull() ?: defaultValue

    private companion object {
        const val INVOICE_PREFS = "invoice_prefs"
        const val PREF_CLIENT_PREFIX = "client"
        const val PREF_CLIENT_NAME = "name"
        const val PREF_CLIENT_STREET = "street"
        const val PREF_CLIENT_CITY = "city"
        const val PREF_CLIENT_ZIP = "zip"
        const val PREF_CLIENT_COUNTRY = "country"
        const val PREF_CLIENT_ICO = "ico"
        const val PREF_CLIENT_DIC = "dic"
        const val PREF_CLIENT_ICDPH = "icdph"
        const val PREF_CLIENT_DESCRIPTION = "description_template"

        const val DEFAULT_CLIENT_NAME = "Demo klient s.r.o."
        const val DEFAULT_CLIENT_STREET = "Obchodná 24"
        const val DEFAULT_CLIENT_CITY = "Bratislava"
        const val DEFAULT_CLIENT_ZIP = "81106"
        const val DEFAULT_CLIENT_ICO = "87654321"
        const val DEFAULT_CLIENT_DIC = "2120000000"
        const val DEFAULT_CLIENT_ICDPH = "SK2120000000"
        const val DEFAULT_COUNTRY = "Slovensko"
        const val DEFAULT_SERVICE_TEMPLATE =
            "Fakturujem Vám za vykonanú prácu – kontrolu kvality v mesiaci {month}"
    }
}
