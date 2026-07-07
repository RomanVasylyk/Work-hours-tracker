package com.example.worktr.ui.picker

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.worktr.R
import com.example.worktr.ui.responsive.ResponsiveUi
import com.google.android.material.textfield.MaterialAutoCompleteTextView

object DropdownUi {
    private const val MAX_VISIBLE_ROWS = 6
    private const val ROW_HEIGHT_DP = 56

    fun adapter(context: Context, items: List<String>): DropdownAdapter =
        DropdownAdapter(context, items)

    fun emptyAdapter(context: Context): DropdownAdapter =
        DropdownAdapter(context, emptyList())

    fun attach(input: MaterialAutoCompleteTextView, selectedIndexProvider: () -> Int? = { null }) {
        input.threshold = 0
        val openDropdown = {
            input.post {
                (input.adapter as? DropdownAdapter)?.let { adapter ->
                    adapter.selectionText = input.text?.toString()
                    adapter.notifyDataSetChanged()
                    input.dropDownHeight = if (adapter.count > MAX_VISIBLE_ROWS) {
                        ResponsiveUi.dp(input.context, ROW_HEIGHT_DP * MAX_VISIBLE_ROWS)
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                }
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

        var selectionText: String? = null

        fun replaceItems(items: List<String>) {
            allItems.clear()
            allItems.addAll(items)
            clear()
            addAll(items)
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView
            val selected = selectionText != null && getItem(position) == selectionText
            view.isSelected = selected
            view.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            view.setTextColor(
                ContextCompat.getColor(context, if (selected) R.color.primary else R.color.field_text)
            )
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0,
                0,
                if (selected) R.drawable.ic_check else 0,
                0
            )
            view.compoundDrawablePadding = ResponsiveUi.dp(context, 8)
            return view
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
