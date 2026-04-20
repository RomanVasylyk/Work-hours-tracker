package com.example.worktr.ui.picker

import android.content.Context
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner

class DynamicYearSpinner(
    context: Context,
    private val spinner: Spinner,
    initialYear: Int,
    private val onYearSelected: (Int) -> Unit
) {
    private val adapter = ArrayAdapter<String>(
        context,
        android.R.layout.simple_spinner_item,
        mutableListOf()
    ).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    private var years: List<Int> = emptyList()
    private var suppressCallback = false

    init {
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (suppressCallback) {
                    suppressCallback = false
                    return
                }
                val selectedYear = years.getOrNull(position) ?: return
                maybeExpandWindow(selectedYear, position)
                onYearSelected(selectedYear)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
        setYear(initialYear, notify = false)
    }

    fun setYear(year: Int, notify: Boolean = false) {
        ensureWindow(year)
        val position = years.indexOf(year).coerceAtLeast(0)
        suppressCallback = !notify
        spinner.setSelection(position)
        if (notify) {
            onYearSelected(year)
        }
    }

    fun getSelectedYear(): Int? = years.getOrNull(spinner.selectedItemPosition)

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
        adapter.clear()
        adapter.addAll(years.map(Int::toString))
        adapter.notifyDataSetChanged()
        val selection = years.indexOf(selectedYear).coerceAtLeast(0)
        suppressCallback = true
        spinner.setSelection(selection)
    }

    companion object {
        private const val YEAR_RANGE = 150
        private const val EDGE_BUFFER = 24
    }
}
