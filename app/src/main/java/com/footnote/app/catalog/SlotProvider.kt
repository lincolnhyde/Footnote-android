package com.footnote.app.catalog

import com.footnote.app.ranking.ContextSnapshot

interface SlotProvider {
    val id: String
    suspend fun slots(ctx: ContextSnapshot): List<Slot>
}
