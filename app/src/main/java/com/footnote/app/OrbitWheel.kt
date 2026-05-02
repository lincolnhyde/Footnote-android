package com.footnote.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class OrbitSlot(val label: String, val onSelect: () -> Unit)

private val Accent = Color(0xFFE8B86E)
private val SlotIdle = Color(0xFFB8B5AE)
private val SlotDim = Color(0x55B8B5AE)
private val RingIdle = Color(0x33FFFFFF)
private val RingActivation = Color(0x66E8B86E)

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

@Composable
fun OrbitWheel(
    slots: List<OrbitSlot>,
    modifier: Modifier = Modifier,
    onSlotFired: (Int) -> Unit = {}
) {
    var center by remember { mutableStateOf<Offset?>(null) }
    var pointer by remember { mutableStateOf<Offset?>(null) }

    val density = LocalDensity.current
    val activationRadiusPx = with(density) { 44.dp.toPx() }
    val wheelRadiusPx = with(density) { 104.dp.toPx() }
    val textMeasurer = rememberTextMeasurer()

    val appear = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val selectedSlot = remember(center, pointer) {
        val c = center
        val p = pointer
        if (c == null || p == null) null
        else computeSelectedSlot(c, p, slots.size, activationRadiusPx)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(slots) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        center = offset
                        pointer = offset
                        scope.launch {
                            appear.snapTo(0f)
                            appear.animateTo(1f, tween(durationMillis = 180))
                        }
                    },
                    onDrag = { change, _ ->
                        pointer = change.position
                        change.consume()
                    },
                    onDragEnd = {
                        val c = center
                        val p = pointer
                        val sel = if (c != null && p != null)
                            computeSelectedSlot(c, p, slots.size, activationRadiusPx)
                        else null
                        scope.launch {
                            appear.animateTo(0f, tween(durationMillis = 120))
                            center = null
                            pointer = null
                        }
                        if (sel != null) {
                            slots[sel].onSelect()
                            onSlotFired(sel)
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            appear.animateTo(0f, tween(durationMillis = 120))
                            center = null
                            pointer = null
                        }
                    }
                )
            }
    ) {
        val c = center ?: return@Canvas
        val a = appear.value
        if (a <= 0f) return@Canvas

        val curWheelR = wheelRadiusPx * (0.85f + 0.15f * a)
        val curActivationR = activationRadiusPx * (0.7f + 0.3f * a)

        // Soft halo behind the wheel for depth
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x33000000), Color(0x00000000)),
                center = c,
                radius = curWheelR * 1.6f
            ),
            radius = curWheelR * 1.6f,
            center = c
        )

        // Activation ring (cancel zone)
        drawCircle(
            color = RingActivation.copy(alpha = RingActivation.alpha * a),
            radius = curActivationR,
            center = c,
            style = Stroke(width = 1.5f)
        )

        // Outer wheel ring
        drawCircle(
            color = RingIdle.copy(alpha = RingIdle.alpha * a),
            radius = curWheelR,
            center = c,
            style = Stroke(width = 1.2f)
        )

        // Slot labels
        val perSlot = 360f / slots.size
        slots.forEachIndexed { i, slot ->
            val angleDeg = -90f + i * perSlot
            val rad = Math.toRadians(angleDeg.toDouble())
            val sx = c.x + curWheelR * cos(rad).toFloat()
            val sy = c.y + curWheelR * sin(rad).toFloat()
            val isSel = selectedSlot == i

            val style = TextStyle(
                color = (if (isSel) Accent else SlotIdle).copy(
                    alpha = (if (isSel) 1f else 0.78f) * a
                ),
                fontSize = if (isSel) 17.sp else 13.sp,
                fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
            val measured = textMeasurer.measure(slot.label, style)
            drawText(
                textMeasurer = textMeasurer,
                text = slot.label,
                style = style,
                topLeft = Offset(
                    sx - measured.size.width / 2f,
                    sy - measured.size.height / 2f
                )
            )
        }

        // Selected slot accent dot at the angle
        if (selectedSlot != null) {
            val angleDeg = -90f + selectedSlot * perSlot
            val rad = Math.toRadians(angleDeg.toDouble())
            val dx = c.x + curActivationR * cos(rad).toFloat()
            val dy = c.y + curActivationR * sin(rad).toFloat()
            drawCircle(
                color = Accent.copy(alpha = 0.9f * a),
                radius = 3f,
                center = Offset(dx, dy)
            )
        }

        // Finger trail
        val p = pointer
        if (p != null) {
            drawLine(
                color = Accent.copy(alpha = 0.45f * a),
                start = c,
                end = p,
                strokeWidth = 1.5f
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * a),
                radius = 5f,
                center = p
            )
        }
    }
}
