package com.footnote.app.ui.orbit

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.footnote.app.OrbitWheel
import com.footnote.app.catalog.Slot
import kotlinx.coroutines.launch

@Composable
fun OrbitHost(
    rootSlotsLoader: suspend () -> List<Slot>,
    modifier: Modifier = Modifier,
    onLeafFired: (Slot.Leaf) -> Unit
) {
    var frames by remember { mutableStateOf<List<List<Slot>>>(emptyList()) }
    var breadcrumb by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (frames.isEmpty()) frames = listOf(rootSlotsLoader())
    }

    val currentSlots = frames.lastOrNull().orEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        if (breadcrumb.isNotEmpty()) {
            Text(
                text = breadcrumb.joinToString(" › "),
                color = Color(0xFFE8B86E).copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            )
        }

        OrbitWheel(
            slots = currentSlots,
            onSlotChosen = { idx ->
                val slot = currentSlots.getOrNull(idx) ?: return@OrbitWheel
                when (slot) {
                    is Slot.Branch -> {
                        scope.launch {
                            val kids = runCatching { slot.children(snapshot()) }
                                .getOrDefault(emptyList())
                            if (kids.isNotEmpty()) {
                                frames = frames + listOf(kids)
                                breadcrumb = breadcrumb + slot.label
                            }
                        }
                    }
                    is Slot.Leaf -> {
                        onLeafFired(slot)
                        frames = frames.take(1)
                        breadcrumb = emptyList()
                    }
                }
            },
            onCancelled = {
                if (frames.size > 1) {
                    frames = frames.dropLast(1)
                    breadcrumb = breadcrumb.dropLast(1)
                }
            }
        )
    }
}

private fun snapshot() = com.footnote.app.ranking.ContextSnapshot.now()
