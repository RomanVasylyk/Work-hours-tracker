package com.example.worktr.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.worktr.data.Job
import com.example.worktr.data.JobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    private val repository: JobRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val jobId: Int = savedStateHandle.get<Int>("jobId") ?: -1

    val job = repository.getAllJobs().map { list -> list.firstOrNull { it.jobId == jobId } }.asLiveData()

    fun update(job: Job) = viewModelScope.launch { repository.update(job) }

    fun delete(job: Job) = viewModelScope.launch { repository.delete(job) }
}
