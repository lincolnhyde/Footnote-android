package com.footnote.app.overlay

sealed interface PullDepth {
    data object Closed : PullDepth
    data object Immediate : PullDepth
    data object Stack : PullDepth
    data object Search : PullDepth
}

internal const val IMMEDIATE_COUNT = 4
internal const val STACK_COUNT = 12

internal fun depthForFraction(f: Float, snap: Boolean): PullDepth {
    val v = f.coerceIn(0f, 1f)
    return if (!snap) {
        when {
            v < 0.10f -> PullDepth.Immediate    // live preview is generous; always show something during gesture
            v < 0.30f -> PullDepth.Immediate
            v < 0.55f -> PullDepth.Stack
            else -> PullDepth.Search
        }
    } else {
        // Snap thresholds — releasing barely-pulled keeps you at Immediate, not closed.
        when {
            v < 0.05f -> PullDepth.Closed       // tap with no slide → close
            v < 0.30f -> PullDepth.Immediate
            v < 0.55f -> PullDepth.Stack
            else -> PullDepth.Search
        }
    }
}
