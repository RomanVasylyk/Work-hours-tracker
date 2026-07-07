package com.example.worktr.ui

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.example.worktr.ui.responsive.ResponsiveUi
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
fun attachHorizontalSwipe(view: View, onSwipe: (forward: Boolean) -> Unit) {
    val context = view.context
    val minDistancePx = ResponsiveUi.dp(context, 56)
    val minVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 2
    val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                e1 ?: return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) > abs(dy) && abs(dx) > minDistancePx && abs(velocityX) > minVelocity) {
                    onSwipe(dx < 0)
                    return true
                }
                return false
            }
        }
    )
    view.setOnTouchListener { _, event ->
        detector.onTouchEvent(event)
        false
    }
}
