package com.example.worktr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class Job(
    @PrimaryKey(autoGenerate = true) val jobId: Int = 0,
    val name: String,
    val hourlyRate: Double = 0.0,
    val nightBonus: Double = 0.0,
    val saturdayBonus: Double = 0.0,
    val sundayBonus: Double = 0.0,
    val holidayBonus: Double = 0.0
)
