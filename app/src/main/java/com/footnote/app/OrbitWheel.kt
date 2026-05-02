package com.footnote.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import com.footnote.app.catalog.Slot
import com.footnote.app.catalog.SlotAction
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val Accent = Color(0xFFE8B86E)
private val SlotIdle = Color(0xFFB8B5AE)
private val RingIdle = Color(0x33FFFFFF)
private val RingActivation = Color(0x66E8B86E)

private const val DwellMs = 280L

private fun computeSelectedSlot(
    center: Offset,
    pointer: Offset,
    slotCount: Int,
    activationRadiusPx: Float
): Int? {
    val dx = pointer.x - center.x
    val dy = pointer.y - center.y
    val dist = hypot(dx, dy)
    if (dist < activationRadiusPx) return null
    val rawDeg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    val deg = (rawDeg + 90f + 360f) % 360f
    val perSlot = 360f / slotCount
    return ((deg + perSlot / 2f) % 360f / perSlot).toInt() % slotCount
}

private fun slotPos(anchor: Offset, idx: Int, slotCount: Int, wheelRadiusPx: Float): Offset {
    val perSlot = 360f / slotCount
    val angleDeg = -90f + idx * perSlot
    val rad = Math.toRadians(angleDeg.toDouble())
    return Offset(
        anchor.x + wheelRadiusPx * cos(rad).toFloat(),
        anchor.y + wheelRadiusPx * sin(rad).toFloat()
    )
}

private fun isDwellEligible(slot: Slot?): Boolean = when (slot) {
    is Slot.Branch -> true
    is Slot.Leaf -> slot.action is SlotAction.PagePrev || slot.action is SlotAction.PageNext
    null -> false
}

@Composable
fun OrbitWheel(
    slots: List<Slot>,
    anchorOverride: Offset?,
    modifier: Modifier = Modifier,
    onSlotChosen: (Int) -> Unit = {},
    onCancelled: () -> Unit = {},
    onDrillRequested: (Int, Offset) -> Unit = { _, _ -> },
    onPopRequested: () -> Unit = {}
) {
    var pressStart by remember { mutableStateOf<Offset?>(null) }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var hoveredSlotIdx by remember { mutableStateOf<Int?>(null) }
    var hoverProgress by remember { mutableStateOf(0f) }
    // Pop arming: after bloom-from-slot drill the finger is inside the new
    // activation ring; we mustn't treat the user's first outward drag as pop.
    // Arm on outside→inside, fire on inside→outside. Reset on every frame change.
    var popArmed by remember { mutableStateOf(false) }
    var frameJustChanged by remember { mutableStateOf(true) }

    val density = LocalDensity.current
    val activationRadiusPx = with(density) { 44.dp.toPx() }
    val wheelRadiusPx = with(density) { 104.dp.toPx() }
    val textMeasurer = rememberTextMeasurer()
    val view = LocalView.current

    val appear = remember { Animatable(0f) }
    val frameAlpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val latestSlots by rememberUpdatedState(slots)
    val latestAnchor by rememberUpdatedState(anchorOverride ?: pressStart)
    val latestOnSlotChosen by rememberUpdatedState(onSlotChosen)
    val latestOnCancelled by rememberUpdatedState(onCancelled)
    val latestOnDrillRequested by rememberUpdatedState(onDrillRequested)
    val latestOnPopRequested by rememberUpdatedState(onPopRequested)

    val effectiveAnchor = anchorOverride ?: pressStart

    LaunchedEffect(slots) {
        hoveredSlotIdx = null
        hoverProgress = 0f
        popArmed = false
        frameJustChanged = true
        if (slots.isNotEmpty()) {
            frameAlpha.snapTo(0f)
            frameAlpha.animateTo(1f, tween(durationMillis = 200))
        }
    }

    LaunchedEffect(hoveredSlotIdx) {
        val idx = hoveredSlotIdx ?: run {
            hoverProgress = 0f
            return@LaunchedEffect
        }
        val start = withFrameMillis { it }
        var elapsed = 0L
        while (elapsed < DwellMs) {
            val now = withFrameMillis { it }
            elapsed = now - start
            hoverProgress = (elapsed.toFloat() / DwellMs).coerceIn(0f, 1f)
        }
        // Dwell complete — fire drill at slot position
        val anchor = latestAnchor
        val s = latestSlots
        if (anchor != null && idx < s.size) {
            val pos = slotPos(anchor, idx, s.size, wheelRadiusPx)
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            // Pre-arm reset: bloom puts the finger inside the new activation; the
            // first outward drag must not register as pop.
            frameJustChanged = true
            popArmed = false
            latestOnDrillRequested(idx, pos)
        }
        hoveredSlotIdx = null
        hoverProgress = 0f
    }

    val selectedSlot = remember(effectiveAnchor, pointer, slots) {
        val a = effectiveAnchor
        val p = pointer
        if (a == null || p == null || slots.isEmpty()) null
        else computeSelectedSlot(a, p, slots.size, activationRadiusPx)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var wasInActivation = false
                var popCooldownEnd = 0L

                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        pressStart = offset
                        pointer = offset
                        hoveredSlotIdx = null
                        hoverProgress = 0f
                        wasInActivation = false
                        popCooldownEnd = 0L
                        popArmed = false
                        frameJustChanged = true
                        scope.launch {
                            appear.snapTo(0f)
                            appear.animateTo(1f, tween(durationMillis = 180))
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        pointer = change.position

                        val a = latestAnchor
                        if (a != null) {
                            val p = change.position
                            val dist = hypot(p.x - a.x, p.y - a.y)
                            val now = System.currentTimeMillis()
                            val s = latestSlots

                            val inActivation = dist < activationRadiusPx

                            if (s.isNotEmpty()) {
                                val sel = computeSelectedSlot(a, p, s.size, activationRadiusPx)
                                val candidate = sel?.let { s.getOrNull(it) }
                                val newHovered = if (sel != null && isDwellEligible(candidate)) sel else null
                                if (newHovered != hoveredSlotIdx) {
                                    hoveredSlotIdx = newHovered
                                }
                            }

                            if (frameJustChanged) {
                                // Re-baseline activation tracking after a frame change.
                                // Bloom anchors the new wheel under the finger, so the
                                // very first inActivation reading isn't a real transition.
                                frameJustChanged = false
                                wasInActivation = inActivation
                            } else {
                                if (!wasInActivation && inActivation) {
                                    popArmed = true
                                }
                                if (popArmed && wasInActivation && !inActivation && now > popCooldownEnd) {
                                    popCooldownEnd = now + 220
                                    popArmed = false
                                    frameJustChanged = true
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    latestOnPopRequested()
                                }
                                wasInActivation = inActivation
                            }
                        }
                    },
                    onDragEnd = {
                        val a = latestAnchor
                        val p = pointer
                        val s = latestSlots
                        val sel = if (a != null && p != null && s.isNotEmpty())
                            computeSelectedSlot(a, p, s.size, activationRadiusPx)
                        else null
                        scope.launch {
                            appear.animateTo(0f, tween(durationMillis = 120))
                            pressStart = null
                            pointer = null
                            hoveredSlotIdx = null
                            hoverProgress = 0f
                        }
                        when {
                            sel == null -> latestOnCancelled()
                            a != null && isDwellEligible(s.getOrNull(sel)) -> {
                                // Release on a Branch / nav slot still drills, with bloom from slot pos
                                val pos = slotPos(a, sel, s.size, wheelRadiusPx)
                                latestOnDrillRequested(sel, pos)
                            }
                            else -> latestOnSlotChosen(sel)
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            appear.animateTo(0f, tween(durationMillis = 120))
                            pressStart = null
                            pointer = null
                            hoveredSlotIdx = null
                            hoverProgress = 0f
                        }
                        latestOnCancelled()
                    }
                )
            }
    ) {
        val a = effectiveAnchor ?: return@Canvas
        val visAlpha = appear.value
        if (visAlpha <= 0f) return@Canvas
        val ringA = visAlpha * frameAlpha.value

        val curWheelR = wheelRadiusPx * (0.85f + 0.15f * visAlpha)
        val curActivationR = activationRadiusPx * (0.7f + 0.3f * visAlpha)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x33000000), Color(0x00000000)),
                center = a,
                radius = curWheelR * 1.6f
            ),
            radius = curWheelR * 1.6f,
            center = a
        )

        drawCircle(
            color = RingActivation.copy(alpha = RingActivation.alpha * visAlpha),
            radius = curActivationR,
            center = a,
            style = Stroke(width = 1.5f)
        )

        drawCircle(
            color = RingIdle.copy(alpha = RingIdle.alpha * visAlpha),
            radius = curWheelR,
            center = a,
            style = Stroke(width = 1.2f)
        )

        if (slots.isNotEmpty()) {
            val perSlot = 360f / slots.size
            slots.forEachIndexed { i, slot ->
                val angleDeg = -90f + i * perSlot
                val rad = Math.toRadians(angleDeg.toDouble())
                val sx = a.x + curWheelR * cos(rad).toFloat()
                val sy = a.y + curWheelR * sin(rad).toFloat()
                val isSel = selectedSlot == i
                val isBranch = slot is Slot.Branch
                val text = if (isBranch) "${slot.label} ›" else slot.label

                val style = TextStyle(
                    color = (if (isSel) Accent else SlotIdle).copy(
                        alpha = (if (isSel) 1f else 0.78f) * ringA
                    ),
                    fontSize = if (isSel) 17.sp else 13.sp,
                    fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )
                val measured = textMeasurer.measure(text, style)
                drawText(
                    textMeasurer = textMeasurer,
                    text = text,
                    style = style,
                    topLeft = Offset(
                        sx - measured.size.width / 2f,
                        sy - measured.size.height / 2f
                    )
                )

                // Dwell progress ring around hovered Branch slot
                if (i == hoveredSlotIdx && hoverProgress > 0.02f) {
                    val ringR = with(density) { 22.dp.toPx() }
                    drawArc(
                        color = Accent.copy(alpha = 0.85f * ringA),
                        startAngle = -90f,
                        sweepAngle = 360f * hoverProgress,
                        useCenter = false,
                        topLeft = Offset(sx - ringR, sy - ringR),
                        size = Size(ringR * 2, ringR * 2),
                        style = Stroke(width = 2.5f)
                    )
                }
            }

            if (selectedSlot != null) {
                val angleDeg = -90f + selectedSlot * perSlot
                val rad = Math.toRadians(angleDeg.toDouble())
                val dx = a.x + curActivationR * cos(rad).toFloat()
                val dy = a.y + curActivationR * sin(rad).toFloat()
                drawCircle(
                    color = Accent.copy(alpha = 0.9f * ringA),
                    radius = 3f,
                    center = Offset(dx, dy)
                )
            }
        }

        val p = pointer
        if (p != null) {
            drawLine(
                color = Accent.copy(alpha = 0.45f * visAlpha),
                start = a,
                end = p,
                strokeWidth = 1.5f
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * visAlpha),
                radius = 5f,
                center = p
            )
        }
    }
}
