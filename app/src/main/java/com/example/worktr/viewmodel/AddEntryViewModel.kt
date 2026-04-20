package com.example.worktr.viewmodel

import androidx.lifecycle.ViewModel
import com.example.worktr.data.WorkEntry
import com.example.worktr.data.WorkEntryRepository

class AddEntryViewModel(
    private val repository: WorkEntryRepository
) : ViewModel() {

    suspend fun getEntryForDay(jobId: Int, start: Long, end: Long) =
        repository.getEntryForDay(jobId, start, end)

    suspend fun insert(entry: WorkEntry) = repository.insert(entry)

    suspend fun update(entry: WorkEntry) = repository.update(entry)

    suspend fun delete(entry: WorkEntry) = repository.delete(entry)

    fun upsertForDay(jobId: Int, start: Long, end: Long, entry: WorkEntry) =
        viewModelScope.launch { repository.upsertForDay(jobId, start, end, entry) }
}
