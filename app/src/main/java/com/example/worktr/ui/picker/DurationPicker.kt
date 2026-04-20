package com.example.worktr.ui.picker

import android.content.Context
import androidx.fragment.app.FragmentManager
import com.example.worktr.R
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlin.math.roundToInt

object DurationPicker {
    fun show(
        fragmentManager: FragmentManager,
        title: String,
        initialHours: Double,
        tag: String,
        onSelected: (Double) -> Unit
    ) {
        val totalMinutes = (initialHours * 60).roundToInt().coerceIn(0, 23 * 60 + 59)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setTitleText(title)
            .setHour(totalMinutes / 60)
            .setMinute(totalMinutes % 60)
            .build()
        picker.addOnPositiveButtonClickListener {
            onSelected(picker.hour + picker.minute / 60.0)
        }
        picker.show(fragmentManager, tag)
    }

    fun format(context: Context, hours: Double): String {
        val totalMinutes = (hours * 60).roundToInt().coerceAtLeast(0)
        val displayHours = totalMinutes / 60
        val displayMinutes = totalMinutes % 60
        return when {
            totalMinutes == 0 -> context.getString(R.string.break_picker_zero)
            displayHours > 0 && displayMinutes > 0 -> context.getString(
                R.string.break_picker_hours_minutes,
                displayHours,
                displayMinutes
            )
            displayHours > 0 -> context.getString(R.string.break_picker_hours, displayHours)
            else -> context.getString(R.string.break_picker_minutes, displayMinutes)
        }
    }
}
