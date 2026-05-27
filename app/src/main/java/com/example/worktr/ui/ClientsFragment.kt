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
import com.example.worktr.data.Client
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.Job
import com.example.worktr.databinding.DialogClientSettingsBinding
import com.example.worktr.databinding.FragmentClientsBinding
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.ClientDefaults
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
            combine(
                db.jobDao().getAllJobs(),
                db.clientDao().getAllClients(),
                db.invoiceDao().getAllInvoices()
            ) { jobs, clients, invoices ->
                val clientsByJob = clients.associateBy { it.jobId }
                val latestInvoices = invoices.groupBy { it.jobId }
                    .mapValues { (_, values) -> values.maxByOrNull { it.createdAtMillis } }
                jobs.map { job ->
                    val client = clientsByJob[job.jobId] ?: clientFromPreferences(prefs, job.jobId)
                    ClientUiModel(
                        job = job,
                        client = client,
                        customerName = client.name,
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

        val client = model.client
        dialogBinding.editCustomerName.setText(client.name)
        dialogBinding.editCustomerStreet.setText(client.street)
        dialogBinding.editCustomerCity.setText(client.city)
        dialogBinding.editCustomerZip.setText(client.zip)
        dialogBinding.editCustomerCountry.setText(client.country)
        dialogBinding.editCustomerIco.setText(client.ico)
        dialogBinding.editCustomerDic.setText(client.dic)
        dialogBinding.editCustomerIcdph.setText(client.icdph)
        dialogBinding.editHourly.setText(job.hourlyRate.toString())
        dialogBinding.editServiceTemplate.setText(client.serviceTemplate)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.buttonCancelClient.setOnClickListener { dialog.dismiss() }
        dialogBinding.buttonSaveClient.setOnClickListener {
            saveClient(model, dialogBinding, dialog)
        }
        dialog.show()
    }

    private fun saveClient(
        model: ClientUiModel,
        dialogBinding: DialogClientSettingsBinding,
        dialog: android.app.Dialog
    ) {
        val job = model.job
        val hourlyRate = dialogBinding.editHourly.value().toInvoiceDouble(job.hourlyRate)
        val client = (model.client.takeIf { it.clientId != 0L } ?: ClientDefaults.forJob(job.jobId)).copy(
            jobId = job.jobId,
            name = dialogBinding.editCustomerName.value().ifBlank { ClientDefaults.NAME },
            street = dialogBinding.editCustomerStreet.value(),
            city = dialogBinding.editCustomerCity.value(),
            zip = dialogBinding.editCustomerZip.value(),
            country = dialogBinding.editCustomerCountry.value().ifBlank { ClientDefaults.COUNTRY },
            ico = dialogBinding.editCustomerIco.value(),
            dic = dialogBinding.editCustomerDic.value(),
            icdph = dialogBinding.editCustomerIcdph.value(),
            serviceTemplate = dialogBinding.editServiceTemplate.value().ifBlank { ClientDefaults.SERVICE_TEMPLATE }
        )
        requireContext().getSharedPreferences(INVOICE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(clientPref(job.jobId, PREF_CLIENT_NAME), client.name)
            .putString(clientPref(job.jobId, PREF_CLIENT_STREET), client.street)
            .putString(clientPref(job.jobId, PREF_CLIENT_CITY), client.city)
            .putString(clientPref(job.jobId, PREF_CLIENT_ZIP), client.zip)
            .putString(clientPref(job.jobId, PREF_CLIENT_COUNTRY), client.country)
            .putString(clientPref(job.jobId, PREF_CLIENT_ICO), client.ico)
            .putString(clientPref(job.jobId, PREF_CLIENT_DIC), client.dic)
            .putString(clientPref(job.jobId, PREF_CLIENT_ICDPH), client.icdph)
            .putString(clientPref(job.jobId, PREF_CLIENT_DESCRIPTION), client.serviceTemplate)
            .apply()

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = DatabaseProvider.get(appContext)
                db.clientDao().insert(client)
                db.jobDao().update(job.copy(hourlyRate = hourlyRate))
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

    }
}
