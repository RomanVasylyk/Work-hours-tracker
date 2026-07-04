package com.example.worktr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.worktr.data.Job
import com.example.worktr.data.JobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobListViewModel @Inject constructor(
    private val repository: JobRepository
) : ViewModel() {
    val jobs = repository.getAllJobs().asLiveData()

    fun insert(job: Job) = viewModelScope.launch { repository.insert(job) }

    fun update(job: Job) = viewModelScope.launch { repository.update(job) }

    fun delete(job: Job) = viewModelScope.launch { repository.delete(job) }
}
