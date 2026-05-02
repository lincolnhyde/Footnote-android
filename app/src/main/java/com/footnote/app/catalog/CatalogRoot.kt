package com.footnote.app.catalog

import android.content.Context
import com.footnote.app.catalog.providers.CuratedAppsProvider
import com.footnote.app.catalog.providers.InstalledAppsProvider
import com.footnote.app.catalog.providers.SystemSettingsProvider
import com.footnote.app.ranking.ContextSnapshot

class CatalogRoot(appCtx: Context) {
    private val curated = CuratedAppsProvider(appCtx)
    private val installed = InstalledAppsProvider(appCtx)

    suspend fun rootSlots(ctx: ContextSnapshot): List<Slot> {
        val curatedSlots = curated.slots(ctx)
        return buildList {
            addAll(curatedSlots)
            add(installed.branch)
            add(SystemSettingsProvider.branch)
        }
    }
}
