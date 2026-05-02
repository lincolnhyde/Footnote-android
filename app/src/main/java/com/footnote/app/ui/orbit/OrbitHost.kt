package com.footnote.app.ui.orbit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.footnote.app.OrbitWheel
import com.footnote.app.catalog.Slot
import com.footnote.app.catalog.SlotAction
import kotlinx.coroutines.launch

@Composable
fun OrbitHost(
    rootSlotsLoader: suspend () -> List<Slot>,
    modifier: Modifier = Modifier,
    onLeafFired: (Slot.Leaf) -> Unit
) {
    var frames by remember { mutableStateOf<List<List<Slot>>>(emptyList()) }
    var pageIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var anchorOverrides by remember { mutableStateOf<List<Offset?>>(emptyList()) }
    var breadcrumb by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (frames.isEmpty()) {
            frames = listOf(rootSlotsLoader())
            pageIndices = listOf(0)
            anchorOverrides = listOf(null)
        }
    }

    val currentFrame = frames.lastOrNull().orEmpty()
    val currentPage = pageIndices.lastOrNull() ?: 0
    val displayedSlots = currentFrame.paginate(currentPage)
    val pageCount = currentFrame.pageCount()
    val currentAnchor = anchorOverrides.lastOrNull() ?: null

    fun drillIntoBranch(slot: Slot.Branch) {
        scope.launch {
            val kids = runCatching { slot.children(snapshot()) }
                .getOrDefault(emptyList())
            if (kids.isNotEmpty()) {
                frames = frames + listOf(kids)
                pageIndices = pageIndices + 0
                // Marking-menu style: sub-orbit reuses the parent's anchor so
                // the user's finger (currently at the parent slot's position)
                // pre-points at the same-angle child of the new wheel.
                anchorOverrides = anchorOverrides + anchorOverrides.lastOrNull()
                breadcrumb = breadcrumb + slot.label
            }
        }
    }

    fun popOneFrame() {
        if (frames.size > 1) {
            frames = frames.dropLast(1)
            pageIndices = pageIndices.dropLast(1)
            anchorOverrides = anchorOverrides.dropLast(1)
            breadcrumb = breadcrumb.dropLast(1)
        }
    }

    fun changePage(delta: Int) {
        if (pageCount <= 1) return
        val cur = pageIndices.last()
        val next = ((cur + delta) % pageCount + pageCount) % pageCount
        pageIndices = pageIndices.dropLast(1) + next
    }

    fun resetToRoot() {
        if (frames.size > 1) {
            frames = frames.take(1)
            pageIndices = listOf(0)
            anchorOverrides = listOf(null)
            breadcrumb = emptyList()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (breadcrumb.isNotEmpty() || pageCount > 1) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (breadcrumb.isNotEmpty()) {
                    Text(
                        text = breadcrumb.joinToString(" › "),
                        color = Color(0xFFE8B86E).copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 2.sp
                    )
                }
                if (pageCount > 1) {
                    Spacer(Modifier.height(6.dp))
                    PageDots(current = currentPage, total = pageCount)
                }
            }
        }

        OrbitWheel(
            slots = displayedSlots,
            anchorOverride = currentAnchor,
            onSlotChosen = { idx ->
                val slot = displayedSlots.getOrNull(idx) ?: return@OrbitWheel
                when (slot) {
                    is Slot.Branch -> drillIntoBranch(slot)
                    is Slot.Leaf -> when (slot.action) {
                        SlotAction.PagePrev -> changePage(-1)
                        SlotAction.PageNext -> changePage(+1)
                        SlotAction.Pop -> popOneFrame()
                        SlotAction.NoOp -> Unit
                        else -> {
                            onLeafFired(slot)
                            resetToRoot()
                        }
                    }
                }
            },
            onCancelled = {
                if (frames.size > 1) popOneFrame()
            },
            onDrillRequested = { idx ->
                val slot = displayedSlots.getOrNull(idx) ?: return@OrbitWheel
                when (slot) {
                    is Slot.Branch -> drillIntoBranch(slot)
                    is Slot.Leaf -> when (slot.action) {
                        SlotAction.PagePrev -> changePage(-1)
                        SlotAction.PageNext -> changePage(+1)
                        else -> Unit
                    }
                }
            },
            onPopRequested = { popOneFrame() }
        )
    }
}

@Composable
private fun PageDots(current: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val active = i == current
            val color = if (active) Color(0xFFE8B86E) else Color(0x55E8B86E)
            Canvas(modifier = Modifier.size(if (active) 5.dp else 4.dp)) {
                drawCircle(color = color, center = Offset(size.width / 2, size.height / 2))
            }
            if (i < total - 1) Spacer(Modifier.width(5.dp))
        }
    }
}

private fun snapshot() = com.footnote.app.ranking.ContextSnapshot.now()
