package com.example.worktr.data

import androidx.room.ColumnInfo
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
    indices = [
        Index("jobId"),
        Index(value = ["jobId", "date"], unique = true)
    ]
)
data class WorkEntry(
    @PrimaryKey(autoGenerate = true) val entryId: Int = 0,
    val jobId: Int,
    val date: Long,
    val hoursWorked: Double,
    val breakHours: Double,
    val shiftType: String,
    val isHoliday: Boolean,
    @ColumnInfo(defaultValue = "0.0") val hourlyRate: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val nightBonus: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val saturdayBonus: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val sundayBonus: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val holidayBonus: Double = 0.0
)
