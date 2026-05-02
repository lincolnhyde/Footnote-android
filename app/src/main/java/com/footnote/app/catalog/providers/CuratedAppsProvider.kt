package com.footnote.app.catalog.providers

import android.content.Context
import android.content.pm.PackageManager
import com.footnote.app.catalog.Slot
import com.footnote.app.catalog.SlotProvider
import com.footnote.app.catalog.curated.CuratedCatalog
import com.footnote.app.ranking.ContextSnapshot

class CuratedAppsProvider(private val appCtx: Context) : SlotProvider {
    override val id: String = "provider.curated"

    override suspend fun slots(ctx: ContextSnapshot): List<Slot> =
        CuratedCatalog.ALL
            .filter { isInstalled(it.packageName) }
            .map { it.branch }

    private fun isInstalled(pkg: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        appCtx.packageManager.getApplicationInfo(pkg, 0)
        true
    }.getOrDefault(false)
}
