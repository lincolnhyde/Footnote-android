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
    // Lowered thresholds (Build 23): the brainstorm's 20%/50% width meant the
    // user had to pull half the screen for Search. On a 1080px phone that's a
    // 540px thumb stretch. Bring Stack to ~12% and Search to ~28% so a normal
    // thumb arc reaches all three depths.
    return if (!snap) {
        when {
            v < 0.12f -> PullDepth.Immediate
            v < 0.28f -> PullDepth.Stack
            else -> PullDepth.Search
        }
    } else {
        when {
            v < 0.04f -> PullDepth.Closed       // tap with no slide → close
            v < 0.12f -> PullDepth.Immediate
            v < 0.28f -> PullDepth.Stack
            else -> PullDepth.Search
        }
    }
}
