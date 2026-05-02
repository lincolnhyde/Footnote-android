package com.footnote.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FootnoteScreen() }
    }
}

private fun safeStart(ctx: Context, intent: Intent) {
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    runCatching { ctx.startActivity(intent) }
        .onFailure { Toast.makeText(ctx, "No app for that action", Toast.LENGTH_SHORT).show() }
}

private fun versionLabel(ctx: Context): String {
    val info = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
    val name = info?.versionName ?: "?"
    val code = info?.let {
        if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
    } ?: 0L
    return "v$name · build $code"
}

@Composable
fun FootnoteScreen() {
    val ctx = LocalContext.current
    var orbitUses by remember { mutableStateOf(0) }
    var lastFired by remember { mutableStateOf<String?>(null) }
    val version = remember { versionLabel(ctx) }

    val slots = remember {
        listOf(
            OrbitSlot("Phone") { safeStart(ctx, Intent(Intent.ACTION_DIAL)) },
            OrbitSlot("Camera") { safeStart(ctx, Intent("android.media.action.STILL_IMAGE_CAMERA")) },
            OrbitSlot("Browser") { safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://"))) },
            OrbitSlot("Messages") { safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))) },
            OrbitSlot("Maps") { safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))) },
            OrbitSlot("Settings") { safeStart(ctx, Intent(Settings.ACTION_SETTINGS)) }
        )
    }

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
                text = "press, drag, release",
                color = Color(0xFF8A8580),
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp
            )
        }

        OrbitWheel(
            slots = slots,
            onSlotFired = { idx ->
                orbitUses += 1
                lastFired = slots[idx].label
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "$orbitUses",
                color = Color(0xFF6E6A65),
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
            lastFired?.let {
                Text(
                    text = it.lowercase(),
                    color = Color(0xFF6E6A65),
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }
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
