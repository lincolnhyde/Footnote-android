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
    slots: List<Slot>,
    modifier: Modifier = Modifier,
    onSlotChosen: (Int) -> Unit = {},
    onCancelled: () -> Unit = {},
    onDrillRequested: (Int) -> Unit = {},
    onPopRequested: () -> Unit = {}
) {
    var center by remember { mutableStateOf<Offset?>(null) }
    var pointer by remember { mutableStateOf<Offset?>(null) }

    val density = LocalDensity.current
    val activationRadiusPx = with(density) { 44.dp.toPx() }
    val wheelRadiusPx = with(density) { 104.dp.toPx() }
    val drillThresholdPx = with(density) { 40.dp.toPx() }
    val textMeasurer = rememberTextMeasurer()

    val appear = remember { Animatable(0f) }
    val frameAlpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val latestSlots by rememberUpdatedState(slots)
    val latestOnSlotChosen by rememberUpdatedState(onSlotChosen)
    val latestOnCancelled by rememberUpdatedState(onCancelled)
    val latestOnDrillRequested by rememberUpdatedState(onDrillRequested)
    val latestOnPopRequested by rememberUpdatedState(onPopRequested)

    LaunchedEffect(slots) {
        if (slots.isNotEmpty()) {
            frameAlpha.snapTo(0.25f)
            frameAlpha.animateTo(1f, tween(durationMillis = 150))
        }
    }

    val selectedSlot = remember(center, pointer, slots) {
        val c = center
        val p = pointer
        if (c == null || p == null || slots.isEmpty()) null
        else computeSelectedSlot(c, p, slots.size, activationRadiusPx)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var wasInDrillZone = false
                var wasInActivation = false
                var drillCooldownEnd = 0L
                var popCooldownEnd = 0L

                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        center = offset
                        pointer = offset
                        wasInDrillZone = false
                        wasInActivation = false
                        drillCooldownEnd = 0L
                        popCooldownEnd = 0L
                        scope.launch {
                            appear.snapTo(0f)
                            appear.animateTo(1f, tween(durationMillis = 180))
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        pointer = change.position

                        val c = center
                        if (c != null) {
                            val p = change.position
                            val dist = hypot(p.x - c.x, p.y - c.y)
                            val now = System.currentTimeMillis()
                            val s = latestSlots

                            val inDrillZone = dist > wheelRadiusPx + drillThresholdPx
                            val inActivation = dist < activationRadiusPx

                            if (!wasInDrillZone && inDrillZone && now > drillCooldownEnd && s.isNotEmpty()) {
                                val sel = computeSelectedSlot(c, p, s.size, activationRadiusPx)
                                val candidate = sel?.let { s.getOrNull(it) }
                                val canDrill = candidate is Slot.Branch ||
                                    (candidate is Slot.Leaf &&
                                        (candidate.action is SlotAction.PagePrev ||
                                         candidate.action is SlotAction.PageNext))
                                if (sel != null && canDrill) {
                                    drillCooldownEnd = now + 220
                                    popCooldownEnd = now + 220
                                    latestOnDrillRequested(sel)
                                }
                            }

                            if (wasInActivation && !inActivation && now > popCooldownEnd) {
                                popCooldownEnd = now + 220
                                drillCooldownEnd = now + 220
                                latestOnPopRequested()
                            }

                            wasInDrillZone = inDrillZone
                            wasInActivation = inActivation
                        }
                    },
                    onDragEnd = {
                        val c = center
                        val p = pointer
                        val s = latestSlots
                        val sel = if (c != null && p != null && s.isNotEmpty())
                            computeSelectedSlot(c, p, s.size, activationRadiusPx)
                        else null
                        scope.launch {
                            appear.animateTo(0f, tween(durationMillis = 120))
                            center = null
                            pointer = null
                        }
                        if (sel != null) latestOnSlotChosen(sel) else latestOnCancelled()
                    },
                    onDragCancel = {
                        scope.launch {
                            appear.animateTo(0f, tween(durationMillis = 120))
                            center = null
                            pointer = null
                        }
                        latestOnCancelled()
                    }
                )
            }
    ) {
        val c = center ?: return@Canvas
        val a = appear.value
        if (a <= 0f) return@Canvas
        val ringA = a * frameAlpha.value

        val curWheelR = wheelRadiusPx * (0.85f + 0.15f * a)
        val curActivationR = activationRadiusPx * (0.7f + 0.3f * a)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x33000000), Color(0x00000000)),
                center = c,
                radius = curWheelR * 1.6f
            ),
            radius = curWheelR * 1.6f,
            center = c
        )

        drawCircle(
            color = RingActivation.copy(alpha = RingActivation.alpha * a),
            radius = curActivationR,
            center = c,
            style = Stroke(width = 1.5f)
        )

        drawCircle(
            color = RingIdle.copy(alpha = RingIdle.alpha * a),
            radius = curWheelR,
            center = c,
            style = Stroke(width = 1.2f)
        )

        if (slots.isEmpty()) return@Canvas

        val perSlot = 360f / slots.size
        slots.forEachIndexed { i, slot ->
            val angleDeg = -90f + i * perSlot
            val rad = Math.toRadians(angleDeg.toDouble())
            val sx = c.x + curWheelR * cos(rad).toFloat()
            val sy = c.y + curWheelR * sin(rad).toFloat()
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
        }

        if (selectedSlot != null) {
            val angleDeg = -90f + selectedSlot * perSlot
            val rad = Math.toRadians(angleDeg.toDouble())
            val dx = c.x + curActivationR * cos(rad).toFloat()
            val dy = c.y + curActivationR * sin(rad).toFloat()
            drawCircle(
                color = Accent.copy(alpha = 0.9f * ringA),
                radius = 3f,
                center = Offset(dx, dy)
            )
        }

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
