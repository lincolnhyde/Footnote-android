package com.footnote.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    onOpenSideKeySettings: () -> Unit,
    onDismiss: () -> Unit
) {
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
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "one-step setup",
                color = Color(0xFF8A8580),
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Light
            )
            Spacer(Modifier.height(36.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x141A1816), RoundedCornerShape(12.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Step("1", "Open Settings → Advanced features → Side key.")
                Step("2", "Set Press and hold → Open app.")
                Step("3", "Pick Footnote.")
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onOpenSideKeySettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8B86E),
                    contentColor = Color(0xFF1A1816)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Open Settings", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A2825),
                    contentColor = Color(0xFFE8B86E)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("I've set it up", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
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
