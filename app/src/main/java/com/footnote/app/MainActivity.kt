package com.footnote.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.footnote.app.onboarding.OnboardingActivity
import com.footnote.app.onboarding.hasAllPermissions
import com.footnote.app.overlay.OverlayService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasAllPermissions(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        OverlayService.start(this)
        setContent { StatusScreen() }
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions(this)) {
            OverlayService.start(this)
        }
    }
}

@Composable
private fun StatusScreen() {
    val ctx = LocalContext.current
    val version = remember { versionLabel(ctx) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1816), Color(0xFF0B0A09)),
                    radius = 1400f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Footnote",
                color = Color(0xFFE8B86E),
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "edge launcher running",
                color = Color(0xFF8A8580),
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "pull from right edge to open",
                color = Color(0xFF6E6A65),
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp
            )
        }

        Text(
            text = version,
            color = Color(0xFF4A4744),
            fontSize = 9.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }
}

private fun versionLabel(ctx: Context): String {
    val info = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
    val name = info?.versionName ?: "?"
    val code = info?.let {
        if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
    } ?: 0L
    return "v$name · build $code"
}
