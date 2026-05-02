package com.footnote.app.core

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.footnote.app.catalog.SlotAction

object IntentLauncher {
    fun launch(ctx: Context, action: SlotAction) {
        val intent = when (action) {
            is SlotAction.LaunchIntent -> action.intent
            is SlotAction.LaunchApp -> ctx.packageManager.getLaunchIntentForPackage(action.packageName)
            is SlotAction.Deeplink -> resolveDeeplink(ctx, action)
            is SlotAction.SettingsPanel -> Intent(action.action)
            SlotAction.Pop -> null
        }
        if (intent == null) {
            toast(ctx, "No app for that action")
            return
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        runCatching { ctx.startActivity(intent) }
            .onFailure { toast(ctx, "No app for that action") }
    }

    private fun resolveDeeplink(ctx: Context, action: SlotAction.Deeplink): Intent? {
        val direct = Intent(Intent.ACTION_VIEW, action.uri)
        if (direct.resolveActivity(ctx.packageManager) != null) return direct
        val fallback = action.fallbackPackage?.let {
            ctx.packageManager.getLaunchIntentForPackage(it)
        }
        return fallback
    }

    private fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
}
