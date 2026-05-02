package com.footnote.app.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.footnote.app.MainActivity
import com.footnote.app.overlay.OverlayService

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnboardingScreen(
                onComplete = {
                    OverlayService.start(this@OnboardingActivity)
                    startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                    finish()
                }
            )
        }
    }
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val ctx = LocalContext.current
    var hasOverlay by remember { mutableStateOf(canDrawOverlays(ctx)) }
    var hasNotif by remember { mutableStateOf(hasNotificationPermission(ctx)) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotif = granted }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = canDrawOverlays(ctx)
                hasNotif = hasNotificationPermission(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasOverlay, hasNotif) {
        if (hasOverlay && hasNotif) onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0A09))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(40.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Footnote",
                color = Color(0xFFE8B86E),
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Edge launcher needs two permissions to work.",
                color = Color(0xFF8A8580),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(36.dp))

            PermissionRow(
                label = "Display over other apps",
                granted = hasOverlay,
                onGrant = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    )
                    runCatching { ctx.startActivity(intent) }
                }
            )

            if (Build.VERSION.SDK_INT >= 33) {
                Spacer(Modifier.height(16.dp))
                PermissionRow(
                    label = "Show notification",
                    granted = hasNotif,
                    onGrant = { notifLauncher.launch("android.permission.POST_NOTIFICATIONS") }
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (granted) "✓" else "·",
            color = if (granted) Color(0xFF8DC68A) else Color(0xFF6E6A65),
            fontSize = 18.sp,
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = label,
            color = Color(0xFFE8B86E),
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.weight(1f)
        )
        if (!granted) {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8B86E),
                    contentColor = Color(0xFF1A1816)
                )
            ) {
                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun canDrawOverlays(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

private fun hasNotificationPermission(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(
        ctx,
        "android.permission.POST_NOTIFICATIONS"
    ) == PackageManager.PERMISSION_GRANTED
}

internal fun hasAllPermissions(ctx: Context): Boolean {
    val overlay = canDrawOverlays(ctx)
    val notif = hasNotificationPermission(ctx)
    return overlay && notif
}
