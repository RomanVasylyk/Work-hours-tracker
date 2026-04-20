package com.example.worktr.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.Job
import com.example.worktr.data.JobRepository
import com.example.worktr.databinding.FragmentJobListBinding
import com.example.worktr.ui.picker.DropdownUi
import com.example.worktr.ui.responsive.ResponsiveUi
import com.example.worktr.util.CsvImporter
import com.example.worktr.util.CsvImportSummary
import com.example.worktr.util.ExcelExporter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class JobListFragment : Fragment() {
    private var _binding: FragmentJobListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: com.example.worktr.viewmodel.JobListViewModel
    private lateinit var adapter: JobListAdapter
    private var currentJobs: List<Job> = emptyList()
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            showImportTargetDialog(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        returnTransition = MaterialFadeThrough()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentJobListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repository = JobRepository(DatabaseProvider.get(requireContext()).jobDao())
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return com.example.worktr.viewmodel.JobListViewModel(repository) as T
            }
        })[com.example.worktr.viewmodel.JobListViewModel::class.java]
        applyResponsiveLayout()

        adapter = JobListAdapter(
            onClick = { job ->
                val action = JobListFragmentDirections
                    .actionJobListFragmentToJobDetailFragment(job.jobId)
                findNavController().navigate(action)
            },
            onLongClick = { job -> showJobOptions(job) }
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
        binding.cardOverallStats.setOnClickListener {
            findNavController().navigate(
                R.id.action_jobListFragment_to_statsFragment,
                bundleOf("jobId" to -1)
            )
        }
        binding.cardExportAll.setOnClickListener { exportAllData() }
        binding.cardImportData.setOnClickListener { launchImportPicker() }
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
            val updated = job.copy(
                name = name.text.toString().trim(),
                hourlyRate = hourly.text.toString().toDoubleOrNull() ?: 0.0,
                nightBonus = night.text.toString().toDoubleOrNull() ?: 0.0,
                saturdayBonus = sat.text.toString().toDoubleOrNull() ?: 0.0,
                sundayBonus = sun.text.toString().toDoubleOrNull() ?: 0.0,
                holidayBonus = hol.text.toString().toDoubleOrNull() ?: 0.0
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
                val j = Job(
                    name = name.text.toString().trim(),
                    hourlyRate = hourly.text.toString().toDoubleOrNull() ?: 0.0,
                    nightBonus = night.text.toString().toDoubleOrNull() ?: 0.0,
                    saturdayBonus = sat.text.toString().toDoubleOrNull() ?: 0.0,
                    sundayBonus = sun.text.toString().toDoubleOrNull() ?: 0.0,
                    holidayBonus = hol.text.toString().toDoubleOrNull() ?: 0.0
                )
                if (j.name.isNotEmpty()) {
                    viewModel.insert(j)
                    dialog.dismiss()
                }
            }
        dialog.show()
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

    private fun applyResponsiveLayout() {
        val profile = ResponsiveUi.profile(requireContext())
        ResponsiveUi.applyOuterPadding(binding.jobListContent, profile)
        val statsParams = binding.cardOverallStats.layoutParams as ViewGroup.MarginLayoutParams
        val importParams = binding.cardImportData.layoutParams as ViewGroup.MarginLayoutParams
        val exportParams = binding.cardExportAll.layoutParams as ViewGroup.MarginLayoutParams
        if (profile.isCompact) {
            binding.quickActionRow.orientation = android.widget.LinearLayout.VERTICAL
            binding.cardOverallStats.layoutParams = statsParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            binding.cardImportData.layoutParams = importParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                marginStart = 0
                topMargin = ResponsiveUi.dp(requireContext(), 10)
            }
            binding.cardExportAll.layoutParams = exportParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                marginStart = 0
                topMargin = ResponsiveUi.dp(requireContext(), 10)
            }
        } else {
            binding.quickActionRow.orientation = android.widget.LinearLayout.HORIZONTAL
            binding.cardOverallStats.layoutParams = statsParams.apply {
                width = 0
                topMargin = 0
            }
            binding.cardImportData.layoutParams = importParams.apply {
                width = 0
                marginStart = ResponsiveUi.dp(requireContext(), 12)
                topMargin = 0
            }
            binding.cardExportAll.layoutParams = exportParams.apply {
                width = 0
                marginStart = ResponsiveUi.dp(requireContext(), 12)
                topMargin = 0
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
