package com.example.worktr.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JobRepository(
    private val dao: JobDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun getAllJobs() = dao.getAllJobs()

    suspend fun getAllJobsList() = withContext(ioDispatcher) { dao.getAllJobsList() }

    suspend fun getJobById(jobId: Int) = withContext(ioDispatcher) { dao.getJobById(jobId) }

    suspend fun getJobByName(name: String) = withContext(ioDispatcher) { dao.getJobByName(name) }

    suspend fun insert(job: Job) = withContext(ioDispatcher) { dao.insert(job) }

    suspend fun update(job: Job) = withContext(ioDispatcher) { dao.update(job) }

    suspend fun delete(job: Job) = withContext(ioDispatcher) { dao.delete(job) }
}
