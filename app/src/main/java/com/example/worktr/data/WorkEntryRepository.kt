package com.example.worktr.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkEntryRepository(
    private val dao: WorkEntryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun getEntriesForPeriod(start: Long, end: Long) =
        dao.getAllEntriesForPeriod(start, end)

    fun getEntriesForPeriod(jobId: Int, start: Long, end: Long) =
        dao.getEntriesForPeriod(jobId, start, end)

    suspend fun getEntryForDay(jobId: Int, start: Long, end: Long) =
        withContext(ioDispatcher) { dao.getEntryForDay(jobId, start, end) }

    suspend fun getLatestEntry(jobId: Int) =
        withContext(ioDispatcher) { dao.getLatestEntry(jobId) }

    suspend fun insert(entry: WorkEntry) = withContext(ioDispatcher) { dao.insert(entry) }

    suspend fun update(entry: WorkEntry) = withContext(ioDispatcher) { dao.update(entry) }

    suspend fun delete(entry: WorkEntry) = withContext(ioDispatcher) { dao.delete(entry) }

    suspend fun deleteEntriesForDates(jobId: Int, dates: List<Long>) =
        withContext(ioDispatcher) { dao.deleteEntriesForDates(jobId, dates) }

}
