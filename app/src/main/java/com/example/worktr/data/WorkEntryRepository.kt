package com.example.worktr.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkEntryRepository(
    private val dao: WorkEntryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun getEntriesForPeriod(jobId: Int, start: Long, end: Long) =
        dao.getEntriesForPeriod(jobId, start, end)

    fun getEntryForDay(jobId: Int, start: Long, end: Long) = dao.getEntryForDay(jobId, start, end)

    suspend fun insert(entry: WorkEntry) = withContext(ioDispatcher) { dao.insert(entry) }

    suspend fun update(entry: WorkEntry) = withContext(ioDispatcher) { dao.update(entry) }

    suspend fun delete(entry: WorkEntry) = withContext(ioDispatcher) { dao.delete(entry) }

}
