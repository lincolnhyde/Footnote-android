package com.footnote.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class OrbitSlot(val label: String, val onSelect: () -> Unit)

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
    val activationRadiusPx = with(density) { 72.dp.toPx() }
    val wheelRadiusPx = with(density) { 144.dp.toPx() }
    val textMeasurer = rememberTextMeasurer()

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
                        center = null
                        pointer = null
                        if (sel != null) {
                            slots[sel].onSelect()
                            onSlotFired(sel)
                        }
                    },
                    onDragCancel = {
                        center = null
                        pointer = null
                    }
                )
            }
    ) {
        val c = center ?: return@Canvas

        drawCircle(
            color = Color.White.copy(alpha = 0.18f),
            radius = activationRadiusPx,
            center = c,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = wheelRadiusPx,
            center = c,
            style = Stroke(width = 2f)
        )

        val perSlot = 360f / slots.size
        slots.forEachIndexed { i, slot ->
            val angleDeg = -90f + i * perSlot
            val rad = Math.toRadians(angleDeg.toDouble())
            val sx = c.x + wheelRadiusPx * cos(rad).toFloat()
            val sy = c.y + wheelRadiusPx * sin(rad).toFloat()
            val isSel = selectedSlot == i
            val style = TextStyle(
                color = if (isSel) Color.White else Color.White.copy(alpha = 0.55f),
                fontSize = if (isSel) 22.sp else 16.sp
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

        val p = pointer
        if (p != null) {
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = c,
                end = p,
                strokeWidth = 3f
            )
            drawCircle(
                color = Color.White,
                radius = 8f,
                center = p
            )
        }
    }
}
