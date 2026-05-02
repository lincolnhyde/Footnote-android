package com.footnote.app

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.footnote.app.launcher.FootnoteTileService
import com.footnote.app.launcher.LauncherActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HomeScreen() }
    }
}

@Composable
private fun HomeScreen() {
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
                .padding(36.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Footnote",
                color = Color(0xFFE8B86E),
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "open from anywhere via the\nFootnote quick-settings tile",
                color = Color(0xFF8A8580),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(36.dp))

            InstructionCard()

            Spacer(Modifier.height(28.dp))

            if (Build.VERSION.SDK_INT >= 33) {
                Button(
                    onClick = { requestAddTile(ctx) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE8B86E),
                        contentColor = Color(0xFF1A1816)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "Add tile to Quick Settings",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    ctx.startActivity(Intent(ctx, LauncherActivity::class.java))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A2825),
                    contentColor = Color(0xFFE8B86E)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "Test launcher",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
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

@Composable
private fun InstructionCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x141A1816), RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Step(num = "1", text = "Pull down twice from the top of the screen.")
        Step(num = "2", text = "Tap the pencil / edit button.")
        Step(num = "3", text = "Drag \"Footnote\" into your active tiles.")
    }
}

@Composable
private fun Step(num: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = num,
            color = Color(0xFFE8B86E),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 12.dp, top = 1.dp)
        )
        Text(
            text = text,
            color = Color(0xFFB8B3AE),
            fontSize = 13.sp,
            fontWeight = FontWeight.Light,
            lineHeight = 18.sp
        )
    }
}

private fun requestAddTile(ctx: Context) {
    if (Build.VERSION.SDK_INT < 33) return
    val sbm = ctx.getSystemService(StatusBarManager::class.java) ?: return
    val component = ComponentName(ctx, FootnoteTileService::class.java)
    val icon = Icon.createWithResource(ctx, R.drawable.ic_qs_tile)
    runCatching {
        sbm.requestAddTileService(
            component,
            "Footnote",
            icon,
            ctx.mainExecutor
        ) { /* result code ignored — user either added or skipped */ }
    }
}

private fun versionLabel(ctx: Context): String {
    val info = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
    val name = info?.versionName ?: "?"
    val code = info?.let {
        if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
    } ?: 0L
    return "v$name · build $code"
}
