package com.footnote.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
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
                    onOpenAppInfo = { openAppInfo() },
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onDismiss = {
                        prefs().edit().putBoolean(KEY_NEEDS_SETUP, false).apply()
                        showOnboarding = false
                    }
                )
            } else {
                LauncherScreen(
                    triggerSource = "A11Y_SHORTCUT",
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
        SelectionLogger.log(this, leaf.id, ContextSnapshot.now(triggerSource = "A11Y_SHORTCUT"))
        finish()
    }

    private fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(intent) }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(intent) }
    }

    private fun prefs(): SharedPreferences =
        getSharedPreferences("footnote_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_NEEDS_SETUP = "needs_setup"
    }
}
