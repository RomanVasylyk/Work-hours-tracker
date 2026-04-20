package com.example.worktr.ui.picker

import android.content.Context
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class DynamicYearSpinner(
    context: Context,
    private val input: MaterialAutoCompleteTextView,
    initialYear: Int,
    private val onYearSelected: (Int) -> Unit
) {
    private val adapter = DropdownUi.emptyAdapter(context)

    private var years: List<Int> = emptyList()

    init {
        input.setAdapter(adapter)
        DropdownUi.attach(input) {
            val currentYear = getSelectedYear() ?: return@attach null
            years.indexOf(currentYear).takeIf { it >= 0 }
        }
        input.setOnItemClickListener { _, _, position, _ ->
            val selectedYear = years.getOrNull(position) ?: return@setOnItemClickListener
            maybeExpandWindow(selectedYear, position)
            onYearSelected(selectedYear)
        }
        setYear(initialYear, notify = false)
    }

    fun setYear(year: Int, notify: Boolean = false) {
        ensureWindow(year)
        input.setText(year.toString(), false)
        if (notify) {
            onYearSelected(year)
        }
    }

    fun getSelectedYear(): Int? = input.text?.toString()?.toIntOrNull()

    private fun maybeExpandWindow(year: Int, position: Int) {
        if (position <= EDGE_BUFFER || position >= years.lastIndex - EDGE_BUFFER) {
            replaceWindow(year, year)
        }
    }

    private fun ensureWindow(year: Int) {
        if (years.isEmpty() || year !in years.first()..years.last()) {
            replaceWindow(year, year)
        }
    }

    private fun replaceWindow(centerYear: Int, selectedYear: Int) {
        years = ((centerYear - YEAR_RANGE)..(centerYear + YEAR_RANGE)).toList()
        adapter.replaceItems(years.map(Int::toString))
        input.setText(selectedYear.toString(), false)
    }

    companion object {
        private const val YEAR_RANGE = 150
        private const val EDGE_BUFFER = 24
    }
}
