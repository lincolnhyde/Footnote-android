package com.footnote.app.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.footnote.app.catalog.Slot
import com.footnote.app.core.IntentLauncher
import com.footnote.app.ranking.ContextSnapshot
import com.footnote.app.ranking.SelectionLogger

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LauncherScreen(
                onLaunch = { leaf -> launchAndFinish(leaf) },
                onDismiss = { finish() }
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // Always return to a fresh launcher on next open.
        finish()
    }

    private fun launchAndFinish(leaf: Slot.Leaf) {
        IntentLauncher.launch(this, leaf.action)
        SelectionLogger.log(
            this,
            leaf.id,
            ContextSnapshot.now(triggerSource = "QS_TILE")
        )
        finish()
    }
}
