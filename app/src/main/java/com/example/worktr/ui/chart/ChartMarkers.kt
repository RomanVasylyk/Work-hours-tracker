package com.example.worktr.ui.chart

import android.content.Context
import android.text.Layout
import androidx.core.content.ContextCompat
import com.example.worktr.R
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.patrykandpatrick.vico.core.common.shape.DashedShape
import com.patrykandpatrick.vico.core.common.shape.MarkerCorneredShape
import java.text.DecimalFormat

/** Tap marker shared by the stats charts. */
fun chartMarker(context: Context): CartesianMarker {
    val background = ShapeComponent(
        fill = Fill(ContextCompat.getColor(context, R.color.surface)),
        shape = MarkerCorneredShape(CorneredShape.Corner.Rounded),
        strokeThicknessDp = 1f,
        strokeFill = Fill(ContextCompat.getColor(context, R.color.calendar_outline)),
    )
    val label = TextComponent(
        color = ContextCompat.getColor(context, R.color.onSurface),
        textAlignment = Layout.Alignment.ALIGN_CENTER,
        padding = Insets(horizontalDp = 8f, verticalDp = 4f),
        background = background,
        minWidth = TextComponent.MinWidth.fixed(valueDp = 40f),
    )
    val guideline = LineComponent(
        fill = Fill(ContextCompat.getColor(context, R.color.chart_grid)),
        thicknessDp = 1f,
        shape = DashedShape(),
    )
    return DefaultCartesianMarker(
        label = label,
        valueFormatter = DefaultCartesianMarker.ValueFormatter.default(DecimalFormat("#.##")),
        guideline = guideline,
    )
}
