package com.footnote.app.catalog

import android.content.Context
import com.footnote.app.catalog.providers.CuratedAppsProvider
import com.footnote.app.catalog.providers.InstalledAppsProvider
import com.footnote.app.catalog.providers.SystemSettingsProvider
import com.footnote.app.data.FootnoteDb
import com.footnote.app.ranking.ContextSnapshot
import com.footnote.app.ranking.SlotRanker

class CatalogRoot(private val appCtx: Context) {
    private val curated = CuratedAppsProvider(appCtx)
    private val installed = InstalledAppsProvider(appCtx)

    suspend fun rootSlots(ctx: ContextSnapshot): List<Slot> {
        val curatedSlots = curated.slots(ctx)
        val tail = buildList {
            addAll(curatedSlots)
            add(installed.branch)
            add(SystemSettingsProvider.branch)
        }

        val dao = FootnoteDb.get(appCtx).selections()
        val historyCount = runCatching { dao.count() }.getOrDefault(0)
        if (historyCount < COLD_START_THRESHOLD) {
            // Not enough signal yet — show today's deterministic root.
            return tail
        }

        val candidates = collectCandidates(curatedSlots, ctx)
        val recent = runCatching { dao.recent(limit = HISTORY_LIMIT) }
            .getOrDefault(emptyList())
        val predicted = SlotRanker.rank(candidates.map { it.leaf }, recent, ctx, PREDICTED_LIMIT)
        if (predicted.isEmpty()) return tail

        val parentLabelById = candidates.associate { it.leaf.id to it.parentLabel }
        val labeled = predicted.map { leaf ->
            val parent = parentLabelById[leaf.id]
            if (parent.isNullOrBlank()) leaf
            else leaf.copy(label = "$parent ${leaf.label}")
        }

        return labeled + tail
    }

    private suspend fun collectCandidates(
        rootSlots: List<Slot>,
        ctx: ContextSnapshot
    ): List<Candidate> {
        val out = mutableListOf<Candidate>()
        for (slot in rootSlots) walk(slot, parentLabel = null, ctx = ctx, out = out)
        return out
    }

    private suspend fun walk(
        slot: Slot,
        parentLabel: String?,
        ctx: ContextSnapshot,
        out: MutableList<Candidate>
    ) {
        when (slot) {
            is Slot.Leaf -> {
                if (slot.action.isLaunchable()) out += Candidate(parentLabel, slot)
            }
            is Slot.Branch -> {
                val children = runCatching { slot.children(ctx) }.getOrDefault(emptyList())
                for (child in children) walk(child, parentLabel = slot.label, ctx = ctx, out = out)
            }
        }
    }

    private fun SlotAction.isLaunchable(): Boolean = when (this) {
        SlotAction.NoOp, SlotAction.Pop, SlotAction.PagePrev, SlotAction.PageNext -> false
        is SlotAction.LaunchIntent, is SlotAction.LaunchApp,
        is SlotAction.Deeplink, is SlotAction.SettingsPanel -> true
    }

    private data class Candidate(val parentLabel: String?, val leaf: Slot.Leaf)

    companion object {
        // Below this many recorded launches, the ranker is too noisy to trust.
        private const val COLD_START_THRESHOLD = 10
        // How many predicted leaves to show at the root (tail = curated branches +
        // All apps + Settings, currently 5 entries — keeping pred small avoids
        // pagination pressure on the wheel).
        private const val PREDICTED_LIMIT = 3
        private const val HISTORY_LIMIT = 500
    }
}
