package com.example.worktr.util

import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher
import com.google.android.material.textfield.TextInputEditText

object DecimalInput {

    fun attach(editText: TextInputEditText, decimals: Int = 2) {
        val filter = InputFilter { source: CharSequence, start: Int, end: Int,
                                   dest: Spanned, dstart: Int, dend: Int ->

            val incoming = source.subSequence(start, end).toString()
            if (incoming.isEmpty()) return@InputFilter null
            if (!incoming.all { it.isDigit() || it == '.' || it == ',' }) return@InputFilter ""

            val normalized = incoming.replace(',', '.')
            val before = dest.toString()
            val future = before.substring(0, dstart) + normalized + before.substring(dend)

            if (future.count { it == '.' } > 1) return@InputFilter ""

            val dotIndex = future.indexOf('.')
            if (dotIndex >= 0) {
                val afterDot = future.length - dotIndex - 1
                if (afterDot > decimals) return@InputFilter ""
            }

            if (normalized != incoming) normalized else null
        }

        editText.filters = (editText.filters?.toList().orEmpty() + filter).toTypedArray()

        editText.addTextChangedListener(object : TextWatcher {
            private var selfChange = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (selfChange) return
                val text = s?.toString() ?: return
                if (text.isEmpty()) return

                var newText = text
                if (newText.startsWith(".")) newText = "0$newText"
                if (newText.contains(",")) newText = newText.replace(",", ".")

                if (newText != text) {
                    selfChange = true
                    editText.setText(newText)
                    editText.setSelection(newText.length)
                    selfChange = false
                }
            }
        })
    }
}
