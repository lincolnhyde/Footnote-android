package com.footnote.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.footnote.app.catalog.Slot
import com.footnote.app.core.IntentLauncher
import com.footnote.app.launcher.LauncherScreen
import com.footnote.app.launcher.OnboardingScreen
import com.footnote.app.ranking.ContextSnapshot
import com.footnote.app.ranking.SelectionLogger

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            var showOnboarding by remember {
                mutableStateOf(prefs().getBoolean(KEY_NEEDS_SETUP, true))
            }
            if (showOnboarding) {
                OnboardingScreen(
                    onOpenSideKeySettings = { openSideKeySettings() },
                    onDismiss = {
                        prefs().edit().putBoolean(KEY_NEEDS_SETUP, false).apply()
                        showOnboarding = false
                    }
                )
            } else {
                LauncherScreen(
                    triggerSource = "SIDE_KEY",
                    onLaunch = { leaf -> launchAndFinish(leaf) }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!prefs().getBoolean(KEY_NEEDS_SETUP, true)) {
            finish()
        }
    }

    private fun launchAndFinish(leaf: Slot.Leaf) {
        IntentLauncher.launch(this, leaf.action)
        SelectionLogger.log(this, leaf.id, ContextSnapshot.now(triggerSource = "SIDE_KEY"))
        finish()
    }

    private fun openSideKeySettings() {
        val attempts = listOf(
            Intent("com.samsung.android.settings.SIDE_KEY"),
            Intent("com.samsung.settings.SIDE_KEY_SETTING"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in attempts) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (intent.resolveActivity(packageManager) != null) {
                val launched = runCatching { startActivity(intent) }.isSuccess
                if (launched) return
            }
        }
    }

    private fun prefs(): SharedPreferences =
        getSharedPreferences("footnote_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_NEEDS_SETUP = "needs_side_key_setup"
    }
}
