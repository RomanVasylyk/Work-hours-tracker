package com.example.worktr.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.worktr.R
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.Job
import com.example.worktr.data.JobRepository
import com.example.worktr.databinding.FragmentJobListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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

        adapter = JobListAdapter { job ->
            val action = JobListFragmentDirections.actionJobListFragmentToJobDetailFragment(job.jobId)
            findNavController().navigate(action)
        }
        binding.recyclerJobs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerJobs.adapter = adapter

        viewModel.jobs.observe(viewLifecycleOwner) { adapter.submitList(it) }

        binding.fabAddJob.setOnClickListener { showAddJobDialog() }
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
