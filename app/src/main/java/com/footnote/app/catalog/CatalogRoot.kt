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

    /** Top-N predicted leaves for the launcher's Suggested grid. */
    suspend fun suggested(ctx: ContextSnapshot, limit: Int): List<Slot.Leaf> {
        val dao = FootnoteDb.get(appCtx).selections()
        val curatedSlots = curated.slots(ctx)
        val candidates = collectCandidates(curatedSlots + installed.branch, ctx)
        val recent = runCatching { dao.recent(limit = HISTORY_LIMIT) }
            .getOrDefault(emptyList())
        val predicted = SlotRanker.rank(candidates.map { it.leaf }, recent, ctx, limit)
        if (predicted.isEmpty()) return emptyList()

        val parentLabelById = candidates.associate { it.leaf.id to it.parentLabel }
        return predicted.map { leaf ->
            val parent = parentLabelById[leaf.id]
            if (parent.isNullOrBlank() || parent == installed.branch.label) leaf
            else leaf.copy(label = "$parent ${leaf.label}")
        }
    }

    /** Flat alphabetised installed apps, for the panel's overflow rows. */
    suspend fun installedApps(): List<Slot.Leaf> = installed.loadAll()

    /** Full searchable set: curated leaves + installed apps + settings leaves. */
    suspend fun allSearchable(ctx: ContextSnapshot): List<Slot.Leaf> {
        val out = mutableListOf<Slot.Leaf>()
        for (cand in collectCandidates(curated.slots(ctx), ctx)) out += cand.leaf
        out += installed.loadAll()
        for (cand in collectCandidates(listOf(SystemSettingsProvider.branch), ctx)) out += cand.leaf
        // Dedup by id while preserving order — installed-app leaves can collide
        // with curated.open leaves on rare cases (different ids by design today).
        val seen = mutableSetOf<String>()
        return out.filter { seen.add(it.id) }
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
        private const val HISTORY_LIMIT = 500
    }
}
