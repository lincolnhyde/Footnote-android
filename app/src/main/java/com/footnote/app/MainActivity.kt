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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun FootnoteScreen() {
    val ctx = LocalContext.current
    var orbitUses by remember { mutableStateOf(0) }
    var lastFired by remember { mutableStateOf<String?>(null) }

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
            .background(Color(0xFF101012))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Footnote",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 28.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Long-press anywhere, then drag\noutward to a slot.",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 14.sp
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
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "uses: $orbitUses",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )
            lastFired?.let {
                Text(
                    text = "last: $it",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
