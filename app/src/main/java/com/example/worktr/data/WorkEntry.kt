package com.example.worktr.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "work_entries",
    foreignKeys = [ForeignKey(
        entity = Job::class,
        parentColumns = ["jobId"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("jobId")]
)
data class WorkEntry(
    @PrimaryKey(autoGenerate = true) val entryId: Int = 0,
    val jobId: Int,
    val date: Long,
    val hoursWorked: Double,
    val breakHours: Double,
    val shiftType: String,
    val isHoliday: Boolean
)
