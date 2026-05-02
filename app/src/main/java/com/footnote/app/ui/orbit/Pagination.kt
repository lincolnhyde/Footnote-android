package com.footnote.app.ui.orbit

import com.footnote.app.catalog.Slot
import com.footnote.app.catalog.SlotAction

const val MAX_SLOTS_PER_RING = 8
const val ITEMS_PER_PAGE = 6

private val PrevSlot = Slot.Leaf(
    id = "__prev",
    label = "‹ prev",
    action = SlotAction.PagePrev
)
private val NextSlot = Slot.Leaf(
    id = "__next",
    label = "next ›",
    action = SlotAction.PageNext
)
private val BlankSlot = Slot.Leaf(
    id = "__blank",
    label = "",
    action = SlotAction.NoOp
)

fun List<Slot>.pageCount(): Int =
    if (size <= MAX_SLOTS_PER_RING) 1
    else (size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE

fun List<Slot>.paginate(pageIndex: Int): List<Slot> {
    if (size <= MAX_SLOTS_PER_RING) return this
    val pages = pageCount()
    val safeIdx = pageIndex.coerceIn(0, pages - 1)
    val start = safeIdx * ITEMS_PER_PAGE
    val end = (start + ITEMS_PER_PAGE).coerceAtMost(size)
    val slice = subList(start, end)

    // 8-slot ring, fixed layout:
    //   index 2 (right, 0°)   = next
    //   index 6 (left, 180°)  = prev
    //   indices 0,1,3,4,5,7   = real slots from the current page
    val realPositions = listOf(0, 1, 3, 4, 5, 7)
    val ring = Array<Slot>(MAX_SLOTS_PER_RING) { BlankSlot }
    ring[2] = NextSlot
    ring[6] = PrevSlot
    slice.forEachIndexed { i, slot ->
        if (i < realPositions.size) ring[realPositions[i]] = slot
    }
    return ring.toList()
}
