package com.example.worktr.util

import android.content.Context
import android.widget.TextView
import com.example.worktr.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.DecimalFormat

class ChartMarkerView(
    context: Context,
    layoutResource: Int,
    private val xLabelProvider: (Int) -> String,
    private val valueSuffix: String,
    private val decimals: Int
) : MarkerView(context, layoutResource) {

    private val tvTitle: TextView = findViewById(R.id.textMarkerTitle)
    private val tvValue: TextView = findViewById(R.id.textMarkerValue)

    private val df = DecimalFormat(
        when (decimals) {
            0 -> "0"
            1 -> "0.0"
            else -> "0.00"
        }
    )

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return
        val x = e.x.toInt()
        tvTitle.text = xLabelProvider(x)
        tvValue.text = "${df.format(e.y)}$valueSuffix"
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}
