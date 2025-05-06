package com.example.worktr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.worktr.data.WorkEntry
import com.example.worktr.data.WorkEntryRepository
import kotlinx.coroutines.launch

class AddEntryViewModel(
    private val repository: WorkEntryRepository
) : ViewModel() {

    fun getEntryForDay(jobId: Int, start: Long, end: Long) =
        repository.getEntryForDay(jobId, start, end).asLiveData()

    fun insert(entry: WorkEntry) = viewModelScope.launch { repository.insert(entry) }

    fun update(entry: WorkEntry) = viewModelScope.launch { repository.update(entry) }

    fun delete(entry: WorkEntry) = viewModelScope.launch { repository.delete(entry) }

}
