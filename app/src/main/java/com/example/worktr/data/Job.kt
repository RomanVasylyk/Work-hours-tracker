package com.example.worktr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class Job(
    @PrimaryKey(autoGenerate = true) val jobId: Int = 0,
    val name: String
)
