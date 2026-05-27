package com.example.worktr.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clients",
    foreignKeys = [ForeignKey(
        entity = Job::class,
        parentColumns = ["jobId"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["jobId"], unique = true)
    ]
)
data class Client(
    @PrimaryKey(autoGenerate = true) val clientId: Long = 0,
    val jobId: Int,
    val name: String,
    val street: String,
    val city: String,
    val zip: String,
    val country: String,
    val ico: String,
    val dic: String,
    val icdph: String,
    val serviceTemplate: String
)
