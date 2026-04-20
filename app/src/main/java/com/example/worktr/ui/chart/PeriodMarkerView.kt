package com.example.worktr.ui.chart

import android.content.Context
import android.widget.TextView
import com.example.worktr.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import kotlin.math.roundToInt

class PeriodMarkerView(
    context: Context,
    private var labels: List<String>,
    private val valueFormatter: (Float) -> String
) : MarkerView(context, R.layout.view_chart_marker) {

    private val titleText = findViewById<TextView>(R.id.textMarkerTitle)
    private val valueText = findViewById<TextView>(R.id.textMarkerValue)

    fun updateLabels(newLabels: List<String>) {
        labels = newLabels
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        val index = e?.x?.roundToInt()?.minus(1) ?: -1
        titleText.text = labels.getOrElse(index) { "" }
        valueText.text = valueFormatter(e?.y ?: 0f)
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 12f)
    }
}
