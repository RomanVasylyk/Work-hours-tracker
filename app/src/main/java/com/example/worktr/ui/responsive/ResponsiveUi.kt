package com.example.worktr.ui.responsive

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.updatePadding
import kotlin.math.roundToInt

data class ResponsiveProfile(
    val isCompact: Boolean,
    val isMedium: Boolean,
    val screenPaddingPx: Int,
    val contentPaddingPx: Int,
    val chartHeightPx: Int,
    val calendarCellMinWidthPx: Int,
    val calendarCellHeightPx: Int
)

object ResponsiveUi {
    fun profile(context: Context): ResponsiveProfile {
        val widthDp = context.resources.configuration.screenWidthDp
        return when {
            widthDp < 360 -> ResponsiveProfile(
                isCompact = true,
                isMedium = false,
                screenPaddingPx = dp(context, 10),
                contentPaddingPx = dp(context, 12),
                chartHeightPx = dp(context, 220),
                calendarCellMinWidthPx = dp(context, 36),
                calendarCellHeightPx = dp(context, 44)
            )

            widthDp < 420 -> ResponsiveProfile(
                isCompact = false,
                isMedium = true,
                screenPaddingPx = dp(context, 14),
                contentPaddingPx = dp(context, 16),
                chartHeightPx = dp(context, 250),
                calendarCellMinWidthPx = dp(context, 40),
                calendarCellHeightPx = dp(context, 50)
            )

            else -> ResponsiveProfile(
                isCompact = false,
                isMedium = false,
                screenPaddingPx = dp(context, 16),
                contentPaddingPx = dp(context, 18),
                chartHeightPx = dp(context, 280),
                calendarCellMinWidthPx = dp(context, 46),
                calendarCellHeightPx = dp(context, 56)
            )
        }
    }

    fun applyOuterPadding(view: View, profile: ResponsiveProfile) {
        view.updatePadding(
            left = profile.screenPaddingPx,
            top = profile.screenPaddingPx,
            right = profile.screenPaddingPx,
            bottom = profile.screenPaddingPx
        )
    }

    fun applyContentPadding(view: View, profile: ResponsiveProfile) {
        view.updatePadding(
            left = profile.contentPaddingPx,
            top = profile.contentPaddingPx,
            right = profile.contentPaddingPx,
            bottom = profile.contentPaddingPx
        )
    }

    fun updateHeight(view: View, heightPx: Int) {
        view.layoutParams = view.layoutParams.apply {
            height = heightPx
        }
    }

    fun setLinearOrientation(layout: LinearLayout, vertical: Boolean) {
        layout.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
    }

    fun setTopMargin(view: View, marginPx: Int) {
        val params = view.layoutParams
        if (params is ViewGroup.MarginLayoutParams) {
            params.topMargin = marginPx
            view.layoutParams = params
        }
    }

    fun setStartMargin(view: View, marginPx: Int) {
        val params = view.layoutParams
        if (params is ViewGroup.MarginLayoutParams) {
            params.marginStart = marginPx
            view.layoutParams = params
        }
    }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}
