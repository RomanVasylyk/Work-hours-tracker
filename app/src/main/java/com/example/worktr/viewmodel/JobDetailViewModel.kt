package com.example.worktr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.worktr.data.Job
import com.example.worktr.data.JobRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class JobDetailViewModel(
    private val repository: JobRepository,
    private val jobId: Int
) : ViewModel() {
    val job = repository.getAllJobs().map { list -> list.firstOrNull { it.jobId == jobId } }.asLiveData()

    fun update(job: Job) = viewModelScope.launch { repository.update(job) }

    fun delete(job: Job) = viewModelScope.launch { repository.delete(job) }
}
