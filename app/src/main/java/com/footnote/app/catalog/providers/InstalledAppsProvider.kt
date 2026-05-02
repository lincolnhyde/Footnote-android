package com.footnote.app.catalog.providers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.footnote.app.catalog.Slot
import com.footnote.app.catalog.SlotAction
import com.footnote.app.catalog.SlotIcon
import com.footnote.app.ranking.ContextSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsProvider(private val appCtx: Context) {
    val branch: Slot.Branch = Slot.Branch(
        id = "apps.root",
        label = "All apps",
        children = { _ -> loadGrouped() }
    )

    suspend fun loadAll(): List<Slot.Leaf> = withContext(Dispatchers.IO) {
        val pm = appCtx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(intent, 0)
        resolved
            .asSequence()
            .filter { it.activityInfo.packageName != appCtx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                val label = ri.loadLabel(pm).toString().ifBlank { pkg }
                Slot.Leaf(
                    id = "app.$pkg",
                    label = label,
                    icon = SlotIcon.AppIcon(pkg),
                    action = SlotAction.LaunchApp(pkg)
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private suspend fun loadGrouped(): List<Slot> {
        val all = loadAll()
        if (all.size <= 8) return all
        return all
            .groupBy { groupKey(it.label) }
            .toSortedMap()
            .map { (key, children) ->
                Slot.Branch(
                    id = "apps.group.$key",
                    label = key,
                    children = { children }
                )
            }
    }

    private fun groupKey(label: String): String {
        val first = label.firstOrNull()?.uppercaseChar() ?: '#'
        return when {
            first.isLetter() -> first.toString()
            else -> "#"
        }
    }
}
