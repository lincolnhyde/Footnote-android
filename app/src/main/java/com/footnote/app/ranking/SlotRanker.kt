package com.footnote.app.ranking

import com.footnote.app.catalog.Slot
import com.footnote.app.data.SelectionEntity
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

object SlotRanker {

    // Tuning weights — start moderate, adjust once we see real usage.
    private const val W_RECENCY = 2.0
    private const val W_HOUR = 1.5
    private const val W_SOURCE = 0.5
    private const val W_TOTAL = 1.0

    // Decay constants (days).
    private const val RECENCY_HALFLIFE_DAYS = 7.0
    private const val PER_ENTRY_HALFLIFE_DAYS = 14.0

    private const val MS_PER_DAY = 86_400_000.0

    fun rank(
        candidates: List<Slot.Leaf>,
        history: List<SelectionEntity>,
        ctx: ContextSnapshot,
        limit: Int
    ): List<Slot.Leaf> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()
        val byId = history.groupBy { it.slotId }
        return candidates
            .map { it to score(it, byId[it.id].orEmpty(), ctx) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun score(
        leaf: Slot.Leaf,
        entries: List<SelectionEntity>,
        ctx: ContextSnapshot
    ): Double {
        if (entries.isEmpty()) return 0.0
        val now = ctx.timestamp

        val mostRecent = entries.maxOf { it.ts }
        val recencyAgeDays = ((now - mostRecent).coerceAtLeast(0L)) / MS_PER_DAY
        val recency = exp(-recencyAgeDays / RECENCY_HALFLIFE_DAYS)

        var hourScore = 0.0
        var sourceScore = 0.0
        for (e in entries) {
            val ageDays = ((now - e.ts).coerceAtLeast(0L)) / MS_PER_DAY
            val decay = exp(-ageDays / PER_ENTRY_HALFLIFE_DAYS)
            if (circHourDist(e.hourOfDay, ctx.hourOfDay) <= 1) hourScore += decay
            if (e.triggerSource == ctx.triggerSource) sourceScore += decay
        }

        val totalScore = ln(1.0 + entries.size.toDouble())

        return W_RECENCY * recency +
            W_HOUR * hourScore +
            W_SOURCE * sourceScore +
            W_TOTAL * totalScore
    }

    private fun circHourDist(a: Int, b: Int): Int {
        val raw = abs(a - b)
        return min(raw, 24 - raw)
    }
}
