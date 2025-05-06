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
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_job, null)
        val editName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editJobName)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonSave)
        val btnDelete = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonDelete)
        editName.setText(job.name)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
        btnSave.setOnClickListener {
            val newName = editName.text.toString().trim()
            if (newName.isNotEmpty()) viewModel.update(job.copy(name = newName))
            dialog.dismiss()
        }
        btnDelete.setOnClickListener {
            viewModel.delete(job)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showAddJobDialog() {
        val input = EditText(requireContext())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_job))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.insert(Job(name = name))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
