package com.example.worktr.ui.picker

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import com.example.worktr.R
import com.google.android.material.textfield.MaterialAutoCompleteTextView

object DropdownUi {
    fun adapter(context: Context, items: List<String>): DropdownAdapter =
        DropdownAdapter(context, items)

    fun emptyAdapter(context: Context): DropdownAdapter =
        DropdownAdapter(context, emptyList())

    fun attach(input: MaterialAutoCompleteTextView, selectedIndexProvider: () -> Int? = { null }) {
        input.threshold = 0
        val openDropdown = {
            input.post {
                input.showDropDown()
                selectedIndexProvider()?.takeIf { it >= 0 }?.let { index ->
                    runCatching { input.setListSelection(index) }
                    input.postDelayed({
                        runCatching { input.setListSelection(index) }
                    }, 32)
                }
            }
        }
        input.setOnClickListener { openDropdown() }
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) openDropdown()
        }
    }

    class DropdownAdapter(
        context: Context,
        items: List<String>
    ) : ArrayAdapter<String>(context, R.layout.item_dropdown_option, items.toMutableList()) {
        private val allItems = items.toMutableList()

        fun replaceItems(items: List<String>) {
            allItems.clear()
            allItems.addAll(items)
            clear()
            addAll(items)
            notifyDataSetChanged()
        }

        override fun getFilter(): Filter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                return FilterResults().apply {
                    values = allItems
                    count = allItems.size
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                clear()
                addAll(allItems)
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return resultValue?.toString().orEmpty()
            }
        }
    }
}
