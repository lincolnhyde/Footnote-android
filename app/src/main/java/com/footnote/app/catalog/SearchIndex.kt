package com.footnote.app.catalog

object SearchIndex {

    /**
     * Case-insensitive substring + keyword match across [pool], with a tiny
     * scoring nudge so prefix matches and label hits beat mid-string keyword
     * matches. Caller passes a tiebreaker score (typically [SlotRanker.rank]
     * applied separately) — this just narrows by query.
     */
    fun search(query: String, pool: List<Slot.Leaf>, limit: Int): List<Slot.Leaf> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return pool
            .map { it to score(q, it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun score(q: String, leaf: Slot.Leaf): Int {
        val label = leaf.label.lowercase()
        if (label == q) return 1000
        if (label.startsWith(q)) return 500
        if (label.contains(q)) return 200
        for (kw in leaf.keywords) {
            val k = kw.lowercase()
            if (k == q) return 150
            if (k.startsWith(q)) return 100
            if (k.contains(q)) return 50
        }
        return 0
    }
}
