package com.example.worktr.ui.chart

import android.content.Context
import android.text.Layout
import androidx.core.content.ContextCompat
import com.example.worktr.R
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
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

/**
 * Shared styling for the stats charts: soft horizontal-only grid, no heavy axis
 * lines, muted label colors and a rounded tap marker — so the two charts read as
 * one calm, consistent set instead of the framework defaults.
 */
object ChartStyle {

    private fun axisLabel(context: Context): TextComponent =
        TextComponent(
            color = ContextCompat.getColor(context, R.color.chart_axis_text),
            textSizeSp = 11f,
            padding = Insets(horizontalDp = 4f, verticalDp = 2f),
        )

    /** Start (Y) axis: horizontal guidelines only, at most [labelCount] labels. */
    fun startAxis(
        context: Context,
        valueFormatter: CartesianValueFormatter,
        labelCount: Int = 5,
    ): VerticalAxis<com.patrykandpatrick.vico.core.cartesian.axis.Axis.Position.Vertical.Start> =
        VerticalAxis.start(
            line = null,
            label = axisLabel(context),
            valueFormatter = valueFormatter,
            tick = null,
            guideline = LineComponent(
                fill = Fill(ContextCompat.getColor(context, R.color.chart_grid)),
                thicknessDp = 1f,
            ),
            itemPlacer = VerticalAxis.ItemPlacer.count({ labelCount }),
        )

    /** Bottom (X) axis: just labels, no vertical grid lines or axis line. */
    fun bottomAxis(
        context: Context,
        valueFormatter: CartesianValueFormatter,
    ): HorizontalAxis<com.patrykandpatrick.vico.core.cartesian.axis.Axis.Position.Horizontal.Bottom> =
        HorizontalAxis.bottom(
            line = null,
            label = axisLabel(context),
            valueFormatter = valueFormatter,
            tick = null,
            guideline = null,
        )

    /** Rounded tap marker with a dashed guideline; [format] controls the value text. */
    fun marker(context: Context, format: DecimalFormat): CartesianMarker {
        val background = ShapeComponent(
            fill = Fill(ContextCompat.getColor(context, R.color.surface)),
            shape = MarkerCorneredShape(CorneredShape.Corner.Rounded),
            strokeThicknessDp = 1f,
            strokeFill = Fill(ContextCompat.getColor(context, R.color.calendar_outline)),
        )
        val label = TextComponent(
            color = ContextCompat.getColor(context, R.color.onSurface),
            textSizeSp = 12f,
            textAlignment = Layout.Alignment.ALIGN_CENTER,
            padding = Insets(horizontalDp = 8f, verticalDp = 4f),
            background = background,
            minWidth = TextComponent.MinWidth.fixed(valueDp = 40f),
        )
        val guideline = LineComponent(
            fill = Fill(ContextCompat.getColor(context, R.color.chart_highlight)),
            thicknessDp = 1f,
            shape = DashedShape(),
        )
        return DefaultCartesianMarker(
            label = label,
            valueFormatter = DefaultCartesianMarker.ValueFormatter.default(format),
            guideline = guideline,
        )
    }
}
