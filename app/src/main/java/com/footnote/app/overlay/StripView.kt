package com.footnote.app.overlay

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View

/**
 * Gesture-capturing edge strip. Lives in its own WindowManager window so it
 * never resizes during a touch sequence (which would otherwise cancel the
 * gesture). Once it captures ACTION_DOWN, the InputDispatcher delivers all
 * subsequent MOVE events here regardless of whether the finger leaves the
 * strip's physical bounds — so we can track a full-screen pull from a 24dp
 * window.
 */
class StripView(context: Context) : View(context) {

    interface Callbacks {
        fun onPressStart()
        /** [pullFraction] = how far across the screen the finger is, 0..1. */
        fun onPullChange(pullFraction: Float)
        fun onPressEnd(pullFraction: Float)
        /** A press in settled-open mode means the user wants to dismiss. */
        fun onDismissRequest()
    }

    var callbacks: Callbacks? = null

    /** When true, the strip is in settled-open mode — taps on it dismiss. */
    var dismissOnTap: Boolean = false

    init {
        setBackgroundColor(STRIP_TINT)
    }

    private var sawDown = false
    private var lastFraction = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (dismissOnTap) {
                    callbacks?.onDismissRequest()
                    return true
                }
                sawDown = true
                lastFraction = 0f
                callbacks?.onPressStart()
                callbacks?.onPullChange(0f)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!sawDown) return false
                val sw = resources.displayMetrics.widthPixels.toFloat()
                lastFraction = ((sw - event.rawX) / sw).coerceIn(0f, 1f)
                callbacks?.onPullChange(lastFraction)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!sawDown) return false
                sawDown = false
                callbacks?.onPressEnd(lastFraction)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        // Subtle gold, ~20% alpha. Visible affordance, not loud.
        private val STRIP_TINT: Int = Color.parseColor("#33E8B86E")
    }
}
