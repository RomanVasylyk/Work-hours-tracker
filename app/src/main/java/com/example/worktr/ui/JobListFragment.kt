package com.example.worktr.ui

import android.widget.EditText
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.Job
import com.example.worktr.data.JobRepository
import com.example.worktr.databinding.FragmentJobListBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class JobListFragment : Fragment() {
    private var _binding: FragmentJobListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: com.example.worktr.viewmodel.JobListViewModel
    private lateinit var adapter: JobListAdapter

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

        viewModel.jobs.observe(viewLifecycleOwner) { adapter.submitList(it) }

        binding.fabAddJob.setOnClickListener { showAddJobDialog() }
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
        MaterialAlertDialogBuilder(requireContext())
            .setView(v)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val j = Job(
                    name = name.text.toString().trim(),
                    hourlyRate = hourly.text.toString().toDoubleOrNull() ?: 0.0,
                    nightBonus = night.text.toString().toDoubleOrNull() ?: 0.0,
                    saturdayBonus = sat.text.toString().toDoubleOrNull() ?: 0.0,
                    sundayBonus = sun.text.toString().toDoubleOrNull() ?: 0.0,
                    holidayBonus = hol.text.toString().toDoubleOrNull() ?: 0.0
                )
                if (j.name.isNotEmpty()) viewModel.insert(j)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
